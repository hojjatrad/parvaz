// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"context"
	wire "github.com/miekg/dns"
	"github.com/xtls/xray-core/common/buf"
	udp_proto "github.com/xtls/xray-core/common/protocol/udp"
	"golang.org/x/net/dns/dnsmessage"
	"testing"
)

// EXPECTED KNOWN LIMIT, not a security PASS. A cache-only generation cannot
// distinguish an old wire reply if the UDP transaction ID was reassigned to a
// new request. This uses the real HandleResponse parser/dispatcher callback,
// with synthetic bytes in memory: no DNS service or third-party host is hit.
func TestParvazKnownLimitReusedUDPTransactionID(t *testing.T) {
	c := labCache(t, false)
	oldWire := new(wire.Msg)
	oldWire.Id = 42
	oldWire.Response = true
	oldWire.Question = []wire.Question{{Name: "fixture.invalid.", Qtype: wire.TypeA, Qclass: wire.ClassINET}}
	answer, err := wire.NewRR("fixture.invalid. 60 IN A 192.0.2.99")
	if err != nil {
		t.Fatal(err)
	}
	oldWire.Answer = []wire.RR{answer}
	payload, err := oldWire.Pack()
	if err != nil {
		t.Fatal(err)
	}
	c.FlushGeneration()
	req := labRequest(c, c.scope(), dnsmessage.TypeA)
	server := &ClassicNameServer{cacheController: c, requests: map[uint16]*udpDnsRequest{42: {dnsRequest: *req, ctx: context.Background()}}}
	buffer := buf.New()
	if _, err = buffer.Write(payload); err != nil {
		buffer.Release()
		t.Fatal(err)
	}
	server.HandleResponse(context.Background(), &udp_proto.Packet{Payload: buffer})
	observed := c.findRecords("fixture.invalid.")
	if observed == nil || observed.A == nil || observed.A.IP[0].String() != "192.0.2.99" {
		t.Fatal("Known-limit reproduction changed; review transport isolation before changing promotion gate")
	}
	t.Log("PROMOTION_BLOCKED: reused UDP ID binds old wire reply to new request; cache generation alone is insufficient")
}
