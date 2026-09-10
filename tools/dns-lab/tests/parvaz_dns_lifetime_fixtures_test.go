// SPDX-License-Identifier: MPL-2.0
package dns

import (
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"testing"
	"time"
)

func aggregateDNS(t *testing.T, servers ...Server) *DNS {
	t.Helper()
	hosts, err := NewStaticHosts(nil)
	if err != nil {
		t.Fatal(err)
	}
	option := &dnsfeature.IPOption{IPv4Enable: true}
	s := &DNS{hosts: hosts, ipOption: option, ctx: udpContext()}
	for _, server := range servers {
		s.clients = append(s.clients, &Client{server: server, ipOption: option, timeoutMs: 3 * time.Second, tag: "fixture"})
	}
	t.Cleanup(func() { s.Close() })
	return s
}
func aggregateLookup(s *DNS) chan labResult {
	done := make(chan labResult, 1)
	go func() {
		ips, _, err := s.LookupIP("fixture.invalid", dnsfeature.IPOption{IPv4Enable: true})
		done <- labResult{ips, err}
	}()
	return done
}
