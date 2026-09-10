// SPDX-License-Identifier: LGPL-3.0-only
package libv2ray

import (
	"context"
	"encoding/json"
	"errors"
	appdns "github.com/xtls/xray-core/app/dns"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"github.com/xtls/xray-core/features/routing"
	"github.com/xtls/xray-core/transport"
	"github.com/xtls/xray-core/transport/pipe"
	"sync"
	"testing"
	"time"
)

type parvazCallback struct{}

func (*parvazCallback) Startup() int                 { return 0 }
func (*parvazCallback) Shutdown() int                { return 0 }
func (*parvazCallback) OnEmitStatus(int, string) int { return 0 }

const parvazConfig = `{"log":{"loglevel":"none"},"dns":{"servers":["192.0.2.53"],"queryStrategy":"UseIPv4"},"outbounds":[{"protocol":"blackhole"}]}`

func parvazController(t *testing.T, config string) *CoreController {
	t.Helper()
	x := NewCoreController(&parvazCallback{})
	if err := x.StartLoop(config, 0); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { x.StopLoop() })
	return x
}
func parvazRead(t *testing.T, data string) parvazDNSWireResult {
	t.Helper()
	var r parvazDNSWireResult
	if err := json.Unmarshal([]byte(data), &r); err != nil {
		t.Fatal(err)
	}
	if r.FullChainFlushed || !r.RebuildRequired {
		t.Fatal("Lab bridge advertised full-chain success")
	}
	return r
}
func TestParvazBridgeRealCoreLogicalInvalidation(t *testing.T) {
	x := parvazController(t, parvazConfig)
	state := parvazRead(t, x.ParvazLabDNSState())
	if state.Session == "" || state.Revision != "0" {
		t.Fatal(state)
	}
	r := parvazRead(t, x.ParvazLabInvalidateDNS(state.Session, state.Revision))
	if !r.LogicalApplied || r.Revision != "1" || r.CacheControllers != 1 {
		t.Fatal(r)
	}
	t.Log("PRIVATE_BRIDGE_VERIFIED: real pinned wrapper/core; logical-only receipt requires rebuild; PROMOTION_BLOCKED")
}
func TestParvazBridgeStaleSessionCannotTargetReplacement(t *testing.T) {
	x := parvazController(t, parvazConfig)
	old := parvazRead(t, x.ParvazLabDNSState())
	x.StopLoop()
	if r := parvazRead(t, x.ParvazLabInvalidateDNS(old.Session, "0")); r.Status != "INACTIVE" {
		t.Fatal(r)
	}
	if err := x.StartLoop(parvazConfig, 0); err != nil {
		t.Fatal(err)
	}
	fresh := parvazRead(t, x.ParvazLabDNSState())
	if old.Session == fresh.Session {
		t.Fatal("Session reused after replacement")
	}
	if r := parvazRead(t, x.ParvazLabInvalidateDNS(old.Session, "0")); r.Status != "STALE_SESSION" || r.LogicalApplied {
		t.Fatal(r)
	}
	if r := parvazRead(t, x.ParvazLabDNSState()); r.Revision != "0" {
		t.Fatal("Replacement was mutated by stale command")
	}
}
func TestParvazBridgeSessionCannotCrossControllers(t *testing.T) {
	a, b := parvazController(t, parvazConfig), parvazController(t, parvazConfig)
	first := parvazRead(t, a.ParvazLabDNSState())
	if r := parvazRead(t, b.ParvazLabInvalidateDNS(first.Session, "0")); r.Status != "STALE_SESSION" {
		t.Fatal(r)
	}
}
func TestParvazBridgeCanonicalRevisionAndCAS(t *testing.T) {
	x := parvazController(t, parvazConfig)
	s := parvazRead(t, x.ParvazLabDNSState())
	for _, bad := range []string{"", "-1", "+0", "00", " 0", "18446744073709551616"} {
		if r := parvazRead(t, x.ParvazLabInvalidateDNS(s.Session, bad)); r.Status != "INVALID_REVISION" {
			t.Fatal(r)
		}
	}
	parvazRead(t, x.ParvazLabInvalidateDNS(s.Session, "0"))
	if r := parvazRead(t, x.ParvazLabInvalidateDNS(s.Session, "0")); r.Status != "STALE_REVISION" {
		t.Fatal(r)
	}
}
func TestParvazBridgeBusyLifecycleIsNotQueued(t *testing.T) {
	x := parvazController(t, parvazConfig)
	state := parvazRead(t, x.ParvazLabDNSState())
	x.coreMutex.Lock()
	defer x.coreMutex.Unlock()
	if r := parvazRead(t, x.ParvazLabInvalidateDNS(state.Session, "0")); r.Status != "BUSY" {
		t.Fatal(r)
	}
	if r := parvazRead(t, x.ParvazLabDNSState()); r.Status != "BUSY" {
		t.Fatal(r)
	}
}
func TestParvazBridgeMixedConfigurationRequiresRebuildWithoutMutation(t *testing.T) {
	x := parvazController(t, `{"log":{"loglevel":"none"},"dns":{"servers":["192.0.2.53","localhost"],"queryStrategy":"UseIPv4"},"outbounds":[{"protocol":"blackhole"}]}`)
	s := parvazRead(t, x.ParvazLabDNSState())
	r := parvazRead(t, x.ParvazLabInvalidateDNS(s.Session, s.Revision))
	if r.Status != "UNSUPPORTED_REBUILD_REQUIRED" || r.LogicalApplied || r.Revision != "0" {
		t.Fatal(r)
	}
}
func TestParvazBridgeAlreadyRunningStartKeepsSession(t *testing.T) {
	x := parvazController(t, parvazConfig)
	before := parvazRead(t, x.ParvazLabDNSState())
	if err := x.StartLoop(parvazConfig, 0); err != nil {
		t.Fatal(err)
	}
	if after := parvazRead(t, x.ParvazLabDNSState()); after.Session != before.Session {
		t.Fatal("No-op start changed owner")
	}
}
func TestParvazBridgeFailedStartDoesNotPublishSession(t *testing.T) {
	x := NewCoreController(&parvazCallback{})
	if err := x.StartLoop(`{`, 0); err == nil {
		t.Fatal("Invalid fixture accepted")
	}
	if r := parvazRead(t, x.ParvazLabDNSState()); r.Status != "INACTIVE" || r.Session != "" {
		t.Fatal(r)
	}
}
func TestParvazBridgeConcurrentInvalidationsDoNotDuplicateRevision(t *testing.T) {
	x := parvazController(t, parvazConfig)
	state := parvazRead(t, x.ParvazLabDNSState())
	var wg sync.WaitGroup
	results := make(chan string, 16)
	for i := 0; i < 16; i++ {
		wg.Add(1)
		go func() { defer wg.Done(); results <- x.ParvazLabInvalidateDNS(state.Session, "0") }()
	}
	wg.Wait()
	close(results)
	applied := 0
	for data := range results {
		r := parvazRead(t, data)
		if r.LogicalApplied {
			applied++
		} else if r.Status != "BUSY" && r.Status != "STALE_REVISION" {
			t.Fatal(r)
		}
	}
	if applied != 1 {
		t.Fatalf("Applied %d times", applied)
	}
}

// An injected uncooperative routing initializer, exercised through the real
// DNS feature, core.Instance.Close and wrapper StopLoop/StartLoop. No TUN/socket.
type parvazStuckDispatcher struct {
	entered chan struct{}
	release chan struct{}
}

func (d *parvazStuckDispatcher) Type() interface{} { return routing.DispatcherType() }
func (d *parvazStuckDispatcher) Start() error      { return nil }
func (d *parvazStuckDispatcher) Close() error      { return nil }
func (d *parvazStuckDispatcher) DispatchLink(context.Context, xnet.Destination, *transport.Link) error {
	return errors.New("unused fixture method")
}
func (d *parvazStuckDispatcher) Dispatch(context.Context, xnet.Destination) (*transport.Link, error) {
	close(d.entered)
	<-d.release
	r, _ := pipe.New()
	_, w := pipe.New()
	return &transport.Link{Reader: r, Writer: w}, nil
}
func TestParvazBridgeRestartWaitsForRetiredUDPInitializerExit(t *testing.T) {
	dispatcher := &parvazStuckDispatcher{make(chan struct{}), make(chan struct{})}
	var release sync.Once
	defer release.Do(func() { close(dispatcher.release) })
	instance := new(core.Instance)
	if err := instance.AddFeature(dispatcher); err != nil {
		t.Fatal(err)
	}
	ctx := context.WithValue(context.Background(), core.XrayKey(1), instance)
	feature, err := appdns.New(ctx, &appdns.Config{QueryStrategy: appdns.QueryStrategy_USE_IP4, NameServer: []*appdns.NameServer{{Address: &xnet.Endpoint{Network: xnet.Network_UDP, Address: xnet.NewIPOrDomain(xnet.IPAddress([]byte{192, 0, 2, 53})), Port: 53}}}})
	if err != nil {
		t.Fatal(err)
	}
	if err = instance.AddFeature(feature); err != nil {
		t.Fatal(err)
	}
	x := NewCoreController(&parvazCallback{})
	x.coreInstance = instance
	x.IsRunning = true
	x.parvazDNSSession = parvazNewDNSSession()
	t.Cleanup(func() { x.StopLoop() })
	done := make(chan struct{})
	go func() { feature.LookupIP("fixture.invalid", dnsfeature.IPOption{IPv4Enable: true}); close(done) }()
	select {
	case <-dispatcher.entered:
	case <-time.After(time.Second):
		t.Fatal("Native initializer not entered")
	}
	x.StopLoop()
	select {
	case <-done:
	case <-time.After(time.Second):
		t.Fatal("Old lookup did not cancel")
	}
	for i := 0; i < 3; i++ {
		if err = x.StartLoop(parvazConfig, 0); !errors.Is(err, errParvazDNSDrainPending) {
			t.Fatalf("Restart claimed native exit too soon: %v", err)
		}
	}
	if x.coreInstance != nil || x.IsRunning {
		t.Fatal("A replacement was installed while old initializer still ran")
	}
	release.Do(func() { close(dispatcher.release) })
	deadline := time.Now().Add(3 * time.Second)
	for {
		r := feature.ParvazDNSControlState()
		if r.Status != "BUSY" && r.OwnedUDPLeases == 0 {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("Retired lease did not drain after exit")
		}
		time.Sleep(time.Millisecond)
	}
	if err = x.StartLoop(parvazConfig, 0); err != nil {
		t.Fatal(err)
	}
}

func TestParvazBridgePreservesStaticPolicyButCloseRevokesIt(t *testing.T) {
	config := `{"log":{"loglevel":"none"},"dns":{"hosts":{"fixture.invalid":"192.0.2.8"},"servers":["192.0.2.53"],"queryStrategy":"UseIPv4"},"outbounds":[{"protocol":"blackhole"}]}`
	x := parvazController(t, config)
	feature := x.coreInstance.GetFeature(dnsfeature.ClientType()).(dnsfeature.Client)
	lookup := func() {
		ips, _, err := feature.LookupIP("fixture.invalid", dnsfeature.IPOption{IPv4Enable: true})
		if err != nil || len(ips) != 1 || ips[0].String() != "192.0.2.8" {
			t.Fatalf("Static policy changed: %v %v", ips, err)
		}
	}
	lookup()
	state := parvazRead(t, x.ParvazLabDNSState())
	if r := parvazRead(t, x.ParvazLabInvalidateDNS(state.Session, state.Revision)); !r.LogicalApplied {
		t.Fatal(r)
	}
	lookup()
	x.StopLoop()
	if _, _, err := feature.LookupIP("fixture.invalid", dnsfeature.IPOption{IPv4Enable: true}); err == nil {
		t.Fatal("Closed DNS feature still served static policy")
	}
}
