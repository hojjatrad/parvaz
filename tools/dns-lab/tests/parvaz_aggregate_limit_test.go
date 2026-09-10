// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"golang.org/x/net/dns/dnsmessage"
	"testing"
)

// Expected failure of scalar-cache-only flush across aggregate fallback. This
// stage predates the lifetime patch; it must remain a separate negative control.
func TestParvazKnownLimitAggregateFallbackCrossesScalarFlush(t *testing.T) {
	d1, d2 := newUDPFixture(), newUDPFixture()
	u1, u2 := udpServer(t, d1), udpServer(t, d2)
	s := aggregateDNS(t, u1, u2)
	done := aggregateLookup(s)
	conn := udpOpened(t, d1)
	_ = conn.request(t)
	// Flush the second cache first, then cancel the first request deterministically.
	u2.cacheController.FlushGeneration()
	u1.cacheController.FlushGeneration()
	conn = udpOpened(t, d2)
	q := conn.request(t)
	conn.reply(t, udpReply(t, q, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
	t.Log("AGGREGATE_BOUNDARY_REPRODUCED: pre-flush aggregate lookup adopted fresh fallback scope")
}
func TestParvazKnownLimitDNSCloseStillServesCachedLookup(t *testing.T) {
	d := newUDPFixture()
	u := udpServer(t, d)
	s := aggregateDNS(t, u)
	c := u.cacheController
	c.updateRecord(labRequest(c, c.scope(), dnsmessage.TypeA), labRecord(7))
	s.Close()
	udpExpect(t, aggregateLookup(s), "192.0.2.7")
	t.Log("AGGREGATE_BOUNDARY_REPRODUCED: upstream DNS.Close did not revoke lookup admission")
}
