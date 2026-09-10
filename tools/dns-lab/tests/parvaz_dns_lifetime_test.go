// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"context"
	"errors"
	"github.com/xtls/xray-core/common/net"
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"golang.org/x/net/dns/dnsmessage"
	"math"
	"net/url"
	"sync"
	"testing"
	"time"
)

func TestParvazDNSCloseRevokesCachedAndFutureLookups(t *testing.T) {
	d := newUDPFixture()
	u := udpServer(t, d)
	s := aggregateDNS(t, u)
	c := u.cacheController
	c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), labRecord(7))
	s.Close()
	if r := labAwaitResult(t, aggregateLookup(s)); !errors.Is(r.err, errDNSLifetimeClosed) {
		t.Fatalf("Closed DNS returned %v", r.err)
	}
	c.FlushGeneration()
	c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), labRecord(8))
	if c.owns(c.scope()) || c.findRecords("fixture.invalid.") != nil {
		t.Fatal("Closed cache was resurrected")
	}
	if r := labAwaitResult(t, udpLookup(u, "fixture.invalid")); r.err == nil {
		t.Fatal("Direct nameserver admission survived close")
	}
	if d.calls.Load() != 0 {
		t.Fatal("Closed cache initiated native I/O")
	}
}
func TestParvazDNSInvalidationStopsOldAggregateFallback(t *testing.T) {
	d1, d2 := newUDPFixture(), newUDPFixture()
	u1, u2 := udpServer(t, d1), udpServer(t, d2)
	s := aggregateDNS(t, u1, u2)
	done := aggregateLookup(s)
	conn := udpOpened(t, d1)
	oldQuery := conn.request(t)
	oldEpoch := udpEpoch(u1)
	report := s.ParvazTryInvalidateDNS(0)
	if !report.LogicalApplied || !report.RebuildRequired || report.FullChainFlushed {
		t.Fatalf("Wrong receipt %+v", report)
	}
	if r := labAwaitResult(t, done); !errors.Is(r.err, errDNSLifetimeChanged) {
		t.Fatalf("Old aggregate lookup survived: %v", r.err)
	}
	if d2.calls.Load() != 0 {
		t.Fatal("Old lookup opened a fresh fallback transport")
	}
	done = aggregateLookup(s)
	conn = udpOpened(t, d1)
	fresh := conn.request(t)
	oldEpoch.handle(udpPayload(t, udpReply(t, oldQuery, "192.0.2.99")))
	conn.reply(t, udpReply(t, fresh, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
	t.Log("DNS_LIFETIME_FENCE_VERIFIED: old aggregate lookup cannot enter new fallback scope")
}
func TestParvazDNSDetachedOldContextCannotAdoptFreshCache(t *testing.T) {
	d := newUDPFixture()
	u := udpServer(t, d)
	s := aggregateDNS(t, u)
	s.parvaz.mu.Lock()
	old := s.parvazScopeLocked()
	s.parvaz.mu.Unlock()
	if r := s.ParvazTryInvalidateDNS(0); !r.LogicalApplied {
		t.Fatal(r)
	}
	_, _, err := queryIP(context.WithoutCancel(old), u, "fixture.invalid", dnsfeature.IPOption{IPv4Enable: true})
	if !errors.Is(err, errCacheGenerationChanged) || d.calls.Load() != 0 {
		t.Fatalf("Detached old scope adopted fresh cache: %v", err)
	}
}
func TestParvazDNSPreflightRejectsMixedResolversWithoutPartialMutation(t *testing.T) {
	for _, kind := range []string{"local", "fake", "doh"} {
		t.Run(kind, func(t *testing.T) {
			d := newUDPFixture()
			u := udpServer(t, d)
			c := u.cacheController
			c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), labRecord(3))
			before := c.scope()
			expiry := c.findRecords("fixture.invalid.").A.Expire
			var unsupported Server
			switch kind {
			case "local":
				unsupported = NewLocalNameServer()
			case "fake":
				unsupported = NewFakeDNSServer(nil)
			case "doh":
				endpoint, _ := url.Parse("https://dns.fixture.invalid/dns-query")
				unsupported = NewDoHNameServer(endpoint, nil, false, false, false, 0, nil)
			}
			s := aggregateDNS(t, u, unsupported)
			r := s.ParvazTryInvalidateDNS(0)
			if r.LogicalApplied || r.Status != "UNSUPPORTED_REBUILD_REQUIRED" || !c.owns(before) || !c.findRecords("fixture.invalid.").A.Expire.Equal(expiry) {
				t.Fatalf("Mixed configuration partially mutated: %+v", r)
			}
		})
	}
}
func TestParvazDNSStaleRevisionCannotMutateNewState(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u)
	first := s.ParvazTryInvalidateDNS(0)
	if first.Revision != 1 || !first.LogicalApplied {
		t.Fatal(first)
	}
	scope := u.cacheController.scope()
	r := s.ParvazTryInvalidateDNS(0)
	if r.Status != "STALE_REVISION" || r.LogicalApplied || !u.cacheController.owns(scope) {
		t.Fatalf("Stale command mutated current state: %+v", r)
	}
}
func TestParvazDNSDuplicateCacheOwnersAreInvalidatedOnce(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u, u)
	before := u.cacheController.scope().id
	r := s.ParvazTryInvalidateDNS(0)
	if !r.LogicalApplied || r.CacheControllers != 1 || u.cacheController.scope().id != before+1 {
		t.Fatal(r)
	}
}
func TestParvazDNSBusyControlDoesNotQueue(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u)
	s.parvaz.mu.Lock()
	defer s.parvaz.mu.Unlock()
	if r := s.ParvazTryInvalidateDNS(0); r.Status != "BUSY" || r.LogicalApplied {
		t.Fatal(r)
	}
	if r := s.ParvazDNSControlState(); r.Status != "BUSY" {
		t.Fatal(r)
	}
}
func TestParvazDNSRevisionExhaustionFailsWithoutMutation(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u)
	scope := u.cacheController.scope()
	s.parvaz.revision = math.MaxUint64
	if r := s.ParvazTryInvalidateDNS(math.MaxUint64); r.Status != "REVISION_EXHAUSTED" || !u.cacheController.owns(scope) {
		t.Fatal(r)
	}
}
func TestParvazDNSCacheGenerationExhaustionPreflightIsAtomic(t *testing.T) {
	u1, u2 := udpServer(t, newUDPFixture()), udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u1, u2)
	before := u1.cacheController.scope()
	u2.cacheController.Lock()
	u2.cacheController.generation = math.MaxUint64
	u2.cacheController.Unlock()
	if r := s.ParvazTryInvalidateDNS(0); r.Status != "CACHE_LIFETIME_UNAVAILABLE" || r.LogicalApplied || !u1.cacheController.owns(before) {
		t.Fatal(r)
	}
	u2.cacheController.FlushGeneration()
	if u2.cacheController.owns(u2.cacheController.scope()) {
		t.Fatal("Exhausted scalar generation wrapped")
	}
}
func TestParvazDNSCloseCancelsPendingWithoutClaimingNativeDrain(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	d := newUDPFixture()
	d.blockOpen = release
	d.entered = make(chan struct{}, 1)
	u := udpServer(t, d)
	s := aggregateDNS(t, u)
	done := aggregateLookup(s)
	select {
	case <-d.entered:
	case <-time.After(time.Second):
		t.Fatal("Native initializer not entered")
	}
	s.Close()
	if r := labAwaitResult(t, done); !errors.Is(r.err, errDNSLifetimeClosed) {
		t.Fatal(r.err)
	}
	r := s.ParvazDNSControlState()
	if r.Status != "CLOSED" || r.OwnedUDPLeases != 1 || r.FullChainFlushed || !r.RebuildRequired {
		t.Fatalf("Close falsely acknowledged drain %+v", r)
	}
}

type aggregateStuckServer struct {
	entered chan struct{}
	release chan struct{}
	exited  chan struct{}
}

func (s *aggregateStuckServer) Name() string         { return "fixture-uncooperative" }
func (s *aggregateStuckServer) IsDisableCache() bool { return false }
func (s *aggregateStuckServer) QueryIP(context.Context, string, dnsfeature.IPOption) ([]net.IP, uint32, error) {
	close(s.entered)
	<-s.release
	close(s.exited)
	return []net.IP{net.IP{192, 0, 2, 9}}, 60, nil
}
func TestParvazDNSParallelCloseRejectsLateUnsupportedChild(t *testing.T) {
	child := &aggregateStuckServer{make(chan struct{}), make(chan struct{}), make(chan struct{})}
	defer func() { close(child.release); <-child.exited }()
	s := aggregateDNS(t, child)
	s.enableParallelQuery = true
	done := aggregateLookup(s)
	select {
	case <-child.entered:
	case <-time.After(time.Second):
		t.Fatal("Child not entered")
	}
	s.Close()
	if r := labAwaitResult(t, done); !errors.Is(r.err, errDNSLifetimeClosed) {
		t.Fatal(r.err)
	}
	select {
	case <-child.exited:
		t.Fatal("Fixture did not retain child until actual exit")
	default:
	}
	r := s.ParvazDNSControlState()
	if r.FullChainFlushed || !r.RebuildRequired || r.UncoveredConditions == 0 {
		t.Fatal(r)
	}
}
func TestParvazDNSConcurrentControlAndCloseCannotResurrect(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u)
	var wg sync.WaitGroup
	for i := 0; i < 4; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 30; j++ {
				r := s.ParvazDNSControlState()
				s.ParvazTryInvalidateDNS(r.Revision)
			}
		}()
	}
	s.Close()
	wg.Wait()
	if r := s.ParvazTryInvalidateDNS(0); r.Status != "CLOSED" || r.LogicalApplied {
		t.Fatal(r)
	}
}
func TestParvazDNSSystemRouteStrategyIsNotAdvertisedAsCovered(t *testing.T) {
	u := udpServer(t, newUDPFixture())
	s := aggregateDNS(t, u)
	s.checkSystem = true
	before := u.cacheController.scope()
	r := s.ParvazTryInvalidateDNS(0)
	if r.Status != "UNSUPPORTED_REBUILD_REQUIRED" || !u.cacheController.owns(before) {
		t.Fatal(r)
	}
}

func TestParvazDNSClosedMaxGenerationRejectsLateMigration(t *testing.T) {
	c := labCache(t, false)
	c.Lock()
	c.generation = math.MaxUint64
	c.Unlock()
	scope := c.scope()
	dirty := map[string]*record{"fixture.invalid.": {A: labRecord(7)}}
	c.FlushGeneration()
	c.flush([]migrationEntry{{"fixture.invalid.", dirty["fixture.invalid."]}}, scope.id)
	c.migrate(dirty, scope.id)
	c.updateRecord(labRequest(c, scope, dnsmessage.TypeA), labRecord(8))
	c.writeAndShrink([]string{"fixture.invalid."}, scope.id)
	if c.findRecords("fixture.invalid.") != nil || c.owns(c.scope()) {
		t.Fatal("Late migration resurrected a closed maximum-generation cache")
	}
}
