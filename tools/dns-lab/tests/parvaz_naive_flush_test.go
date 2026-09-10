// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"golang.org/x/net/dns/dnsmessage"
	"net"
	"testing"
	"time"
)

// Negative control on UNMODIFIED pinned upstream. This is a proposed naive flush,
// not an API exposed by the shipped app. Expected outcome: old reply repopulates.
func TestParvazNaiveFlushRepopulatesFromLateReply(t *testing.T) {
	c := NewCacheController("fixture", false, false, 0)
	defer c.cacheCleanup.Close()
	req := &dnsRequest{domain: "fixture.invalid.", reqType: dnsmessage.TypeA, start: time.Now()}
	c.Lock()
	c.ips = make(map[string]*record)
	c.Unlock()
	sub := c.pub.Subscribe(req.domain + "4")
	defer sub.Close()
	c.updateRecord(req, &IPRecord{IP: []net.IP{net.IPv4(192, 0, 2, 1)}, Expire: time.Now().Add(time.Minute)})
	if c.findRecords(req.domain) == nil {
		t.Fatal("Negative control did not reproduce late cache write")
	}
	select {
	case <-sub.Wait():
	default:
		t.Fatal("Negative control did not reproduce late publication")
	}
	t.Log("NAIVE_FLUSH_REPRODUCED: old reply writes and publishes after map clear; do not implement this")
}
