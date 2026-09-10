// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"context"
	"errors"
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"golang.org/x/net/dns/dnsmessage"
	"net"
	"sync"
	"testing"
	"time"
)

func labCache(t *testing.T, disabled bool) *CacheController {
	t.Helper()
	c := NewCacheController("fixture", disabled, false, 0)
	t.Cleanup(func() { c.cancelGeneration(); c.cacheCleanup.Close() })
	return c
}
func labRequest(c *CacheController, scope cacheScope, kind dnsmessage.Type) *dnsRequest {
	return &dnsRequest{domain: "fixture.invalid.", reqType: kind, start: time.Now(), cacheScope: scope}
}
func labRecord(last byte) *IPRecord {
	return &IPRecord{IP: []net.IP{net.IPv4(192, 0, 2, last)}, Expire: time.Now().Add(time.Minute)}
}
func TestParvazFlushClearsBothFamiliesAndDirtyMap(t *testing.T) {
	c := labCache(t, false)
	s := c.scope()
	c.updateRecord(labRequest(c, s, dnsmessage.TypeA), labRecord(1))
	c.updateRecord(labRequest(c, s, dnsmessage.TypeAAAA), labRecord(2))
	c.Lock()
	c.dirtyips = map[string]*record{"dirty.invalid.": {A: labRecord(3)}}
	c.Unlock()
	c.FlushGeneration()
	if c.findRecords("fixture.invalid.") != nil || c.findRecords("dirty.invalid.") != nil {
		t.Fatal("Old entries survived flush")
	}
	if c.owns(s) {
		t.Fatal("Old scope remains current")
	}
	select {
	case <-s.context.Done():
	default:
		t.Fatal("Old query scope was not cancelled")
	}
}
func TestParvazLateReplyCannotWriteOrPublish(t *testing.T) {
	c := labCache(t, false)
	old := c.scope()
	c.FlushGeneration()
	fresh := c.scope()
	a, _ := c.registerSubscribers("fixture.invalid.", dnsfeature.IPOption{IPv4Enable: true}, fresh)
	defer a.Close()
	c.updateRecord(labRequest(c, old, dnsmessage.TypeA), labRecord(1))
	if c.findRecords("fixture.invalid.") != nil {
		t.Fatal("Old callback repopulated cache")
	}
	select {
	case <-a.Wait():
		t.Fatal("Old response reached a fresh subscriber")
	default:
	}
	c.updateRecord(labRequest(c, fresh, dnsmessage.TypeA), labRecord(2))
	select {
	case got := <-a.Wait():
		if !got.(*IPRecord).IP[0].Equal(labRecord(2).IP[0]) {
			t.Fatal("Wrong fresh reply")
		}
	default:
		t.Fatal("Fresh response lost")
	}
}
func TestParvazUnstampedAndOtherControllerRepliesRejected(t *testing.T) {
	c := labCache(t, false)
	other := labCache(t, false)
	c.updateRecord(labRequest(c, cacheScope{}, dnsmessage.TypeA), labRecord(1))
	c.updateRecord(labRequest(c, other.scope(), dnsmessage.TypeA), labRecord(2))
	if c.findRecords("fixture.invalid.") != nil {
		t.Fatal("A foreign or unstamped response was accepted")
	}
}
func TestParvazDisabledCacheStillSeparatesSubscribers(t *testing.T) {
	c := labCache(t, true)
	old := c.scope()
	c.FlushGeneration()
	fresh := c.scope()
	a, _ := c.registerSubscribers("fixture.invalid.", dnsfeature.IPOption{IPv4Enable: true}, fresh)
	defer a.Close()
	c.updateRecord(labRequest(c, old, dnsmessage.TypeA), labRecord(1))
	select {
	case <-a.Wait():
		t.Fatal("Cross-generation reply with cache disabled")
	default:
	}
	c.updateRecord(labRequest(c, fresh, dnsmessage.TypeA), labRecord(2))
	select {
	case <-a.Wait():
	default:
		t.Fatal("Fresh reply lost with cache disabled")
	}
	if c.findRecords("fixture.invalid.") != nil {
		t.Fatal("Disabled cache stored a record")
	}
}
func TestParvazOldMigrationCannotResurrectOrClearNewMigration(t *testing.T) {
	c := labCache(t, false)
	old := c.scope()
	dirty := map[string]*record{"old.invalid.": {A: labRecord(1)}}
	c.FlushGeneration()
	c.Lock()
	c.dirtyips = map[string]*record{"new.invalid.": {A: labRecord(2)}}
	c.Unlock()
	c.flush([]migrationEntry{{"batch.invalid.", &record{A: labRecord(3)}}}, old.id)
	c.migrate(dirty, old.id)
	if c.findRecords("old.invalid.") != nil || c.findRecords("batch.invalid.") != nil {
		t.Fatal("Old migration resurrected cache")
	}
	if c.findRecords("new.invalid.") == nil {
		t.Fatal("Old completion cleared new migration")
	}
}
func TestParvazOldCleanupCannotDeleteNewGeneration(t *testing.T) {
	c := labCache(t, false)
	old := c.scope()
	c.FlushGeneration()
	r := labRecord(1)
	r.Expire = time.Now().Add(-time.Second)
	c.Lock()
	c.ips["fixture.invalid."] = &record{A: r}
	c.Unlock()
	c.writeAndShrink([]string{"fixture.invalid."}, old.id)
	if c.findRecords("fixture.invalid.") == nil {
		t.Fatal("Old cleanup changed new generation")
	}
}
func TestParvazRecordSlotSnapshotIsDetached(t *testing.T) {
	c := labCache(t, false)
	c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), labRecord(1))
	snapshot := c.findRecords("fixture.invalid.")
	c.Lock()
	c.ips["fixture.invalid."].A = nil
	c.Unlock()
	if snapshot.A == nil {
		t.Fatal("Live record slots exposed outside lock")
	}
}
func TestParvazCacheAndMigrationDoNotExtendExpiry(t *testing.T) {
	c := labCache(t, false)
	r := labRecord(1)
	expiry := r.Expire
	c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), r)
	if !c.findRecords("fixture.invalid.").A.Expire.Equal(expiry) {
		t.Fatal("Cache extended expiry")
	}
	c.flush([]migrationEntry{{"migrated.invalid.", &record{A: r}}}, c.scope().id)
	if !c.findRecords("migrated.invalid.").A.Expire.Equal(expiry) {
		t.Fatal("Migration extended expiry")
	}
}

type labNameserver struct {
	cache *CacheController
	sent  chan *dnsRequest
}

func (s *labNameserver) getCacheController() *CacheController { return s.cache }
func (s *labNameserver) sendQuery(ctx context.Context, ch chan<- error, fqdn string, opt dnsfeature.IPOption, scope cacheScope) {
	req := labRequest(s.cache, scope, dnsmessage.TypeA)
	req.domain = fqdn
	select {
	case s.sent <- req:
	case <-ctx.Done():
	}
}

type labResult struct {
	ips []net.IP
	err error
}

func labLookup(s *labNameserver) chan labResult {
	done := make(chan labResult, 1)
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		ips, _, err := queryIP(ctx, s, "fixture.invalid", dnsfeature.IPOption{IPv4Enable: true})
		done <- labResult{ips, err}
	}()
	return done
}
func labAwaitRequest(t *testing.T, s *labNameserver) *dnsRequest {
	t.Helper()
	select {
	case req := <-s.sent:
		return req
	case <-time.After(4 * time.Second):
		t.Fatal("Query did not reach fixture")
		return nil
	}
}
func labAwaitResult(t *testing.T, done chan labResult) labResult {
	t.Helper()
	select {
	case result := <-done:
		return result
	case <-time.After(4 * time.Second):
		t.Fatal("Query did not finish")
		return labResult{}
	}
}
func TestParvazFlushCancelsInflightAndSeparatesSingleflight(t *testing.T) {
	c := labCache(t, false)
	s := &labNameserver{c, make(chan *dnsRequest, 4)}
	oldDone := labLookup(s)
	oldReq := labAwaitRequest(t, s)
	c.FlushGeneration()
	freshDone := labLookup(s)
	freshReq := labAwaitRequest(t, s)
	if oldReq.cacheScope.id == freshReq.cacheScope.id {
		t.Fatal("New query joined old singleflight")
	}
	c.updateRecord(oldReq, labRecord(1))
	c.updateRecord(freshReq, labRecord(2))
	if result := labAwaitResult(t, oldDone); !errors.Is(result.err, errCacheGenerationChanged) {
		t.Fatalf("Old query not rejected: %v", result.err)
	}
	if result := labAwaitResult(t, freshDone); result.err != nil || len(result.ips) != 1 || !result.ips[0].Equal(labRecord(2).IP[0]) {
		t.Fatalf("Fresh lookup wrong: %+v", result)
	}
}
func TestParvazStaleBackgroundRefreshCannotReenterNewGeneration(t *testing.T) {
	c := labCache(t, false)
	s := &labNameserver{c, make(chan *dnsRequest, 2)}
	old := c.scope()
	c.FlushGeneration()
	pull(context.Background(), s, "fixture.invalid.", dnsfeature.IPOption{IPv4Enable: true}, old)
	select {
	case <-s.sent:
		t.Fatal("Stale refresh reentered new generation")
	default:
	}
}
func TestParvazLateNegativeReplyCannotPoisonNewGeneration(t *testing.T) {
	c := labCache(t, false)
	old := c.scope()
	c.FlushGeneration()
	negative := labRecord(1)
	negative.IP = nil
	negative.RCode = dnsmessage.RCodeNameError
	c.updateRecord(labRequest(c, old, dnsmessage.TypeA), negative)
	if c.findRecords("fixture.invalid.") != nil {
		t.Fatal("Old negative reply poisoned new cache")
	}
}
func TestParvazConcurrentFlushLookupUpdateAndCleanup(t *testing.T) {
	c := labCache(t, false)
	var wg sync.WaitGroup
	for worker := 0; worker < 6; worker++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for i := 0; i < 150; i++ {
				scope := c.scope()
				c.updateRecord(labRequest(c, scope, dnsmessage.TypeA), labRecord(1))
				snapshot := c.findRecords("fixture.invalid.")
				if snapshot != nil && snapshot.A != nil {
					_, _, _ = snapshot.A.getIPs()
				}
				_ = c.CacheCleanup()
			}
		}()
	}
	for i := 0; i < 150; i++ {
		c.FlushGeneration()
	}
	wg.Wait()
	c.FlushGeneration()
	if c.findRecords("fixture.invalid.") != nil {
		t.Fatal("Cache survived final barrier")
	}
}
