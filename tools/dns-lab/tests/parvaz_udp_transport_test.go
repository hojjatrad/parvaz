// SPDX-License-Identifier: MPL-2.0
package dns

import (
	"context"
	"errors"
	wire "github.com/miekg/dns"
	"github.com/xtls/xray-core/common/buf"
	xnet "github.com/xtls/xray-core/common/net"
	"github.com/xtls/xray-core/core"
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"github.com/xtls/xray-core/features/routing"
	"github.com/xtls/xray-core/transport"
	"github.com/xtls/xray-core/transport/pipe"
	"io"
	stdnet "net"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type udpFixtureConn struct {
	queries *pipe.Reader
	replies *pipe.Writer
}

func (c *udpFixtureConn) request(t *testing.T) *wire.Msg {
	t.Helper()
	mb, err := c.queries.ReadMultiBufferTimeout(3 * time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer buf.ReleaseMulti(mb)
	if len(mb) != 1 {
		t.Fatalf("Unexpected datagrams: %d", len(mb))
	}
	m := new(wire.Msg)
	if err = m.Unpack(mb[0].Bytes()); err != nil {
		t.Fatal(err)
	}
	return m
}
func udpReply(t *testing.T, q *wire.Msg, ip string) *wire.Msg {
	t.Helper()
	m := new(wire.Msg)
	m.SetReply(q.Copy())
	a, err := wire.NewRR(q.Question[0].Name + " 60 IN A " + ip)
	if err != nil {
		t.Fatal(err)
	}
	m.Answer = []wire.RR{a}
	return m
}
func udpPayload(t *testing.T, m *wire.Msg) *buf.Buffer {
	t.Helper()
	data, err := m.Pack()
	if err != nil {
		t.Fatal(err)
	}
	b := buf.New()
	if _, err = b.Write(data); err != nil {
		b.Release()
		t.Fatal(err)
	}
	return b
}
func (c *udpFixtureConn) reply(t *testing.T, m *wire.Msg) {
	t.Helper()
	if err := c.replies.WriteMultiBuffer(buf.MultiBuffer{udpPayload(t, m)}); err != nil {
		t.Fatal(err)
	}
}

type udpFixtureDispatcher struct {
	failClose    bool
	openFailure  error
	blockClose   <-chan struct{}
	closeEntered chan struct{}
	opened       chan *udpFixtureConn
	entered      chan struct{}
	blockOpen    <-chan struct{}
	stuckRead    <-chan struct{}
	readEntered  chan struct{}
	stuckWrite   <-chan struct{}
	calls        atomic.Int32
}

func (d *udpFixtureDispatcher) Type() interface{} { return routing.DispatcherType() }
func (d *udpFixtureDispatcher) Start() error      { return nil }
func (d *udpFixtureDispatcher) Close() error      { return nil }
func (d *udpFixtureDispatcher) DispatchLink(context.Context, xnet.Destination, *transport.Link) error {
	return errors.New("unused fixture entry")
}
func (d *udpFixtureDispatcher) Dispatch(ctx context.Context, dest xnet.Destination) (*transport.Link, error) {
	d.calls.Add(1)
	if d.entered != nil {
		d.entered <- struct{}{}
	}
	if d.openFailure != nil {
		return nil, d.openFailure
	}
	if d.blockOpen != nil {
		<-d.blockOpen
	} // Deliberately ignores cancellation, testing ownership.
	queries, out := pipe.New()
	in, replies := pipe.New()
	d.opened <- &udpFixtureConn{queries, replies}
	var reader buf.Reader = in
	var writer buf.Writer = out
	if d.failClose {
		reader = &failingCloseDNSReader{in}
	}
	if d.blockClose != nil {
		reader = &closingDNSReader{in, d.blockClose, d.closeEntered}
	}
	if d.stuckRead != nil {
		reader = &stuckDNSReader{d.stuckRead, d.readEntered}
	}
	if d.stuckWrite != nil {
		writer = &stuckDNSWriter{d.stuckWrite}
	}
	return &transport.Link{Reader: reader, Writer: writer}, nil
}

type stuckDNSReader struct {
	release <-chan struct{}
	entered chan struct{}
}

func (r *stuckDNSReader) ReadMultiBuffer() (buf.MultiBuffer, error) {
	if r.entered != nil {
		r.entered <- struct{}{}
	}
	<-r.release
	return nil, io.EOF
}
func (r *stuckDNSReader) Interrupt() {} // Models an uncooperative native read.
type stuckDNSWriter struct{ release <-chan struct{} }

func (w *stuckDNSWriter) WriteMultiBuffer(mb buf.MultiBuffer) error {
	defer buf.ReleaseMulti(mb)
	<-w.release
	return io.EOF
}
func (w *stuckDNSWriter) Interrupt() {}
func udpServer(t *testing.T, d routing.Dispatcher) *ClassicNameServer {
	t.Helper()
	dest := xnet.UDPDestination(xnet.IPAddress(stdnet.IPv4(127, 0, 0, 1)), 53)
	s := NewClassicNameServer(dest, d, false, false, 0, nil)
	t.Cleanup(func() {
		s.cacheController.FlushGeneration()
		s.Lock()
		e := s.current
		s.Unlock()
		if e != nil {
			e.retire()
		}
		waitUDPSlots(t, s, 0)
		s.requestsCleanup.Close()
		s.cacheController.cacheCleanup.Close()
	})
	return s
}
func waitUDPSlots(t *testing.T, s *ClassicNameServer, count int) {
	t.Helper()
	deadline := time.Now().Add(4 * time.Second)
	for len(s.slots) != count && time.Now().Before(deadline) {
		time.Sleep(time.Millisecond)
	}
	if len(s.slots) != count {
		t.Fatalf("Live resource leases=%d want %d", len(s.slots), count)
	}
}
func udpEpoch(s *ClassicNameServer) *dnsTransportEpoch { s.Lock(); defer s.Unlock(); return s.current }
func udpOpened(t *testing.T, d *udpFixtureDispatcher) *udpFixtureConn {
	t.Helper()
	select {
	case c := <-d.opened:
		return c
	case <-time.After(4 * time.Second):
		t.Fatal("No transport opened")
		return nil
	}
}
func udpLookup(s *ClassicNameServer, domain string) chan labResult {
	done := make(chan labResult, 1)
	go func() {
		ctx, cancel := context.WithTimeout(udpContext(), 3*time.Second)
		defer cancel()
		ips, _, err := s.QueryIP(ctx, domain, dnsfeature.IPOption{IPv4Enable: true})
		done <- labResult{ips, err}
	}()
	return done
}
func udpExpect(t *testing.T, done chan labResult, ip string) {
	t.Helper()
	r := labAwaitResult(t, done)
	if r.err != nil || len(r.ips) != 1 || r.ips[0].String() != ip {
		t.Fatalf("Unexpected lookup %+v", r)
	}
}

// Test-only context using the upstream exported test key; not an Android bridge.
func udpContext() context.Context {
	return context.WithValue(context.Background(), core.XrayKey(1), &core.Instance{})
}
func newUDPFixture() *udpFixtureDispatcher {
	return &udpFixtureDispatcher{opened: make(chan *udpFixtureConn, 8)}
}

func TestParvazUDPRejectsOldTransportWithReusedID(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	oldDone := udpLookup(s, "fixture.invalid")
	oldConn := udpOpened(t, d)
	oldQuery := oldConn.request(t)
	old := udpEpoch(s)
	oldReply := udpReply(t, oldQuery, "192.0.2.1")
	s.cacheController.FlushGeneration()
	freshDone := udpLookup(s, "fixture.invalid")
	freshConn := udpOpened(t, d)
	freshQuery := freshConn.request(t)
	fresh := udpEpoch(s)
	if oldQuery.Id != freshQuery.Id || old == fresh {
		t.Fatal("Fixture did not reuse ID on distinct transport")
	}
	old.handle(udpPayload(t, oldReply)) // A callback already queued before retirement.
	fresh.mu.Lock()
	pending := len(fresh.requests)
	fresh.mu.Unlock()
	if pending != 1 || s.cacheController.findRecords("fixture.invalid.") != nil {
		t.Fatal("Old transport consumed/published into new request")
	}
	if r := labAwaitResult(t, oldDone); !errors.Is(r.err, errCacheGenerationChanged) {
		t.Fatalf("Old lookup survived policy flush: %v", r.err)
	}
	freshConn.reply(t, udpReply(t, freshQuery, "192.0.2.2"))
	udpExpect(t, freshDone, "192.0.2.2")
	t.Log("UDP_OLD_TRANSPORT_REPLAY_REJECTED: same transaction ID did not cross transport ownership")
}
func TestParvazUDPQuestionAndHeaderMismatchCannotConsumeRequest(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	e := udpEpoch(s)
	for _, bad := range []string{"name", "type", "class", "request", "opcode", "extra"} {
		m := udpReply(t, q, "192.0.2.99")
		switch bad {
		case "name":
			m.Question[0].Name = "wrong.invalid."
		case "type":
			m.Question[0].Qtype = wire.TypeAAAA
		case "class":
			m.Question[0].Qclass = wire.ClassCHAOS
		case "request":
			m.Response = false
		case "opcode":
			m.Opcode = wire.OpcodeStatus
		case "extra":
			m.Question = append(m.Question, m.Question[0])
		}
		e.handle(udpPayload(t, m))
		e.mu.Lock()
		pending := len(e.requests)
		e.mu.Unlock()
		if pending != 1 {
			t.Fatalf("%s consumed live request", bad)
		}
	}
	conn.reply(t, udpReply(t, q, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
}
func TestParvazUDPExpiredResponseCannotPopulateCache(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	e := udpEpoch(s)
	e.mu.Lock()
	e.requests[q.Id].expire = time.Now().Add(-time.Second)
	e.mu.Unlock()
	e.handle(udpPayload(t, udpReply(t, q, "192.0.2.9")))
	if r := labAwaitResult(t, done); r.err == nil {
		t.Fatal("Expired request accepted")
	}
	if s.cacheController.findRecords("fixture.invalid.") != nil {
		t.Fatal("Expired response entered cache")
	}
}
func TestParvazUDPTruncationRetryUsesFreshIDAndSameOwner(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	truncated := udpReply(t, q, "192.0.2.1")
	truncated.Truncated = true
	truncated.Answer = nil
	conn.reply(t, truncated)
	retry := conn.request(t)
	if retry.Id == q.Id || len(retry.Extra) != 1 {
		t.Fatal("Retry reused transaction or lost EDNS")
	}
	conn.reply(t, udpReply(t, retry, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
}
func TestParvazUDPSecondTruncationFailsWithoutRetryLoop(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	m := udpReply(t, q, "192.0.2.1")
	m.Truncated = true
	m.Answer = nil
	conn.reply(t, m)
	retry := conn.request(t)
	m = udpReply(t, retry, "192.0.2.1")
	m.Truncated = true
	m.Answer = nil
	conn.reply(t, m)
	if r := labAwaitResult(t, done); r.err == nil {
		t.Fatal("Second truncation accepted")
	}
}
func TestParvazUDPOldTruncationCannotStartFreshRetry(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	old := udpEpoch(s)
	s.cacheController.FlushGeneration()
	m := udpReply(t, q, "192.0.2.1")
	m.Truncated = true
	m.Answer = nil
	old.handle(udpPayload(t, m))
	_ = labAwaitResult(t, done)
	if d.calls.Load() != 1 {
		t.Fatal("Stale TC packet opened another transport")
	}
}
func TestParvazUDPTransactionExhaustionRotatesInsteadOfReusing(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	oldDone := udpLookup(s, "fixture.invalid")
	oldConn := udpOpened(t, d)
	q := oldConn.request(t)
	old := udpEpoch(s)
	old.mu.Lock()
	old.nextID = 65536
	old.mu.Unlock()
	fresh, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil || fresh == old {
		t.Fatalf("No fresh transport: %v", err)
	}
	if r := labAwaitResult(t, oldDone); r.err == nil {
		t.Fatal("Exhausted transport's pending request survived retirement")
	}
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	next := conn.request(t)
	if next.Id != q.Id {
		t.Fatal("Fixture did not exercise ID reuse")
	}
	old.handle(udpPayload(t, udpReply(t, q, "192.0.2.99")))
	conn.reply(t, udpReply(t, next, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
}
func TestParvazUDPUncooperativeInitializationKeepsCapacityBounded(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	d := newUDPFixture()
	d.blockOpen = release
	d.entered = make(chan struct{}, 4)
	s := udpServer(t, d)
	for i := 0; i < 2; i++ {
		_, err := s.transportFor(udpContext(), s.cacheController.scope())
		if err != nil {
			t.Fatal(err)
		}
		select {
		case <-d.entered:
		case <-time.After(time.Second):
			t.Fatal("Initialization not entered")
		}
		s.cacheController.FlushGeneration()
	}
	if _, err := s.transportFor(udpContext(), s.cacheController.scope()); !errors.Is(err, errUDPTransportBusy) {
		t.Fatalf("Capacity not retained: %v", err)
	}
	if d.calls.Load() != 2 {
		t.Fatal("Unbounded initializers")
	}
	if len(s.slots) != 2 {
		t.Fatal("Lease released before initializer returned")
	}
}
func TestParvazUDPUncooperativeReaderKeepsLeaseUntilExit(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	d := newUDPFixture()
	d.stuckRead = release
	d.readEntered = make(chan struct{}, 2)
	s := udpServer(t, d)
	for i := 0; i < 2; i++ {
		e, err := s.transportFor(udpContext(), s.cacheController.scope())
		if err != nil {
			t.Fatal(err)
		}
		udpOpened(t, d)
		<-e.ready
		select {
		case <-d.readEntered:
		case <-time.After(time.Second):
			t.Fatal("Reader not entered")
		}
		s.cacheController.FlushGeneration()
	}
	if _, err := s.transportFor(udpContext(), s.cacheController.scope()); !errors.Is(err, errUDPTransportBusy) {
		t.Fatalf("Reader lease freed early: %v", err)
	}
}
func TestParvazUDPUncooperativeWriterKeepsLeaseUntilExit(t *testing.T) {
	release := make(chan struct{})
	d := newUDPFixture()
	d.stuckWrite = release
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	defer func() { close(release); _ = labAwaitResult(t, done) }()
	udpOpened(t, d)
	e := udpEpoch(s)
	deadline := time.Now().Add(time.Second)
	for {
		e.mu.Lock()
		writing := e.active >= 2 && len(e.requests) > 0
		e.mu.Unlock()
		if writing {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("Write not entered")
		}
		time.Sleep(time.Millisecond)
	}
	s.cacheController.FlushGeneration()
	time.Sleep(10 * time.Millisecond)
	if len(s.slots) != 1 {
		t.Fatal("Writer lease released while write is still blocked")
	}
}
func TestParvazUDPCompletedCallerDoesNotOwnSharedTransport(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "one.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	conn.reply(t, udpReply(t, q, "192.0.2.1"))
	udpExpect(t, done, "192.0.2.1")
	done = udpLookup(s, "two.invalid")
	q = conn.request(t)
	conn.reply(t, udpReply(t, q, "192.0.2.2"))
	udpExpect(t, done, "192.0.2.2")
	if d.calls.Load() != 1 {
		t.Fatal("Caller completion closed shared transport")
	}
}
func TestParvazUDPConcurrentFlushAdmissionAndRetirement(t *testing.T) {
	d := newUDPFixture()
	d.opened = make(chan *udpFixtureConn, 512)
	s := udpServer(t, d)
	var wg sync.WaitGroup
	for i := 0; i < 4; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 40; j++ {
				_, _ = s.transportFor(udpContext(), s.cacheController.scope())
				s.cacheController.FlushGeneration()
			}
		}()
	}
	wg.Wait()
	s.cacheController.FlushGeneration()
	waitUDPSlots(t, s, 0)
}

// A separate boundary: UDP bytes freshly injected into the NEW routing link
// with correct source/question/ID cannot be authenticated by cache ownership.
// This is not the old-transport callback bug; do not call the primitive a
// cryptographic DNS replay defense or a device/network validation.
func TestParvazKnownLimitReplayInjectedIntoNewTransport(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	conn := udpOpened(t, d)
	q := conn.request(t)
	oldBytes := udpReply(t, q, "192.0.2.99")
	s.cacheController.FlushGeneration()
	_ = labAwaitResult(t, done)
	done = udpLookup(s, "fixture.invalid")
	conn = udpOpened(t, d)
	fresh := conn.request(t)
	if fresh.Id != oldBytes.Id {
		t.Fatal("Replay ID fixture mismatch")
	}
	conn.reply(t, oldBytes)
	udpExpect(t, done, "192.0.2.99")
	t.Log("UDP_NEW_CHANNEL_REPLAY_OUT_OF_SCOPE: transport ownership does not authenticate UDP wire bytes; PROMOTION_BLOCKED: not a shipped feature")
}

type closingDNSReader struct {
	inner   *pipe.Reader
	release <-chan struct{}
	entered chan struct{}
}

func (r *closingDNSReader) ReadMultiBuffer() (buf.MultiBuffer, error) {
	return r.inner.ReadMultiBuffer()
}
func (r *closingDNSReader) Interrupt() { r.inner.Interrupt(); r.entered <- struct{}{}; <-r.release }
func TestParvazUDPUncooperativeCleanupKeepsLeaseUntilExit(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	d := newUDPFixture()
	d.blockClose = release
	d.closeEntered = make(chan struct{}, 1)
	s := udpServer(t, d)
	e, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil {
		t.Fatal(err)
	}
	udpOpened(t, d)
	<-e.ready
	e.retire()
	select {
	case <-d.closeEntered:
	case <-time.After(time.Second):
		t.Fatal("Cleanup not entered")
	}
	if len(s.slots) != 1 {
		t.Fatal("Lease freed while cleanup is still executing")
	}
}
func TestParvazUDPInitializationErrorReleasesCapacity(t *testing.T) {
	d := newUDPFixture()
	d.openFailure = io.ErrUnexpectedEOF
	s := udpServer(t, d)
	for i := 0; i < 4; i++ {
		e, err := s.transportFor(udpContext(), s.cacheController.scope())
		if err != nil {
			t.Fatal(err)
		}
		<-e.ready
		waitUDPSlots(t, s, 0)
		e.mu.Lock()
		got := e.openErr
		e.mu.Unlock()
		if !errors.Is(got, io.ErrUnexpectedEOF) {
			t.Fatal("Initialization error lost")
		}
	}
}
func TestParvazUDPPendingAndWriteAdmissionAreBounded(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	e, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil {
		t.Fatal(err)
	}
	udpOpened(t, d)
	<-e.ready
	reqs, err := buildReqMsgs("fixture.invalid.", dnsfeature.IPOption{IPv4Enable: true}, func() uint16 { return 0 }, nil)
	if err != nil {
		t.Fatal(err)
	}
	req := reqs[0]
	req.cacheScope = e.scope
	e.writes <- struct{}{}
	if err = e.send(udpContext(), req, nil); !errors.Is(err, errUDPTransportBusy) {
		t.Fatalf("Busy writer admitted another operation: %v", err)
	}
	<-e.writes
	e.mu.Lock()
	for i := 0; i < dnsMaxPending; i++ {
		e.requests[uint16(i)] = &udpDnsRequest{}
	}
	e.mu.Unlock()
	if err = e.send(udpContext(), req, nil); !errors.Is(err, errUDPTransportBusy) {
		t.Fatalf("Pending capacity exceeded: %v", err)
	}
	e.mu.Lock()
	if e.nextID != 0 {
		t.Error("Rejected request consumed transaction budget")
	}
	e.mu.Unlock()
}
func TestParvazUDPIdleTimerDoesNotRetireFreshActivity(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	e, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil {
		t.Fatal(err)
	}
	udpOpened(t, d)
	<-e.ready
	e.expireIdle()
	e.mu.Lock()
	retired := e.retired
	e.lastActivity = time.Now().Add(-2 * time.Minute)
	e.mu.Unlock()
	if retired {
		t.Fatal("Freshly active transport retired")
	}
	e.expireIdle()
	waitUDPSlots(t, s, 0)
}

type failingCloseDNSReader struct{ inner *pipe.Reader }

func (r *failingCloseDNSReader) ReadMultiBuffer() (buf.MultiBuffer, error) {
	return r.inner.ReadMultiBuffer()
}
func (r *failingCloseDNSReader) Close() error { r.inner.Interrupt(); return io.ErrUnexpectedEOF }
func TestParvazUDPCleanupErrorDoesNotClaimReleasedCapacity(t *testing.T) {
	d := newUDPFixture()
	d.failClose = true
	s := NewClassicNameServer(xnet.UDPDestination(xnet.IPAddress(stdnet.IPv4(127, 0, 0, 1)), 53), d, false, false, 0, nil)
	defer s.requestsCleanup.Close()
	defer s.cacheController.cacheCleanup.Close()
	defer s.cacheController.cancelGeneration()
	e, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil {
		t.Fatal(err)
	}
	udpOpened(t, d)
	<-e.ready
	e.retire()
	deadline := time.Now().Add(time.Second)
	for {
		e.mu.Lock()
		done := e.cleanupErr != nil && e.active == 0
		e.mu.Unlock()
		if done {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("Cleanup did not finish")
		}
		time.Sleep(time.Millisecond)
	}
	if len(s.slots) != 1 {
		t.Fatal("Cleanup error falsely freed capacity")
	}
}
func TestParvazUDPLastTransactionIDDoesNotWrapInLiveTransport(t *testing.T) {
	d := newUDPFixture()
	s := udpServer(t, d)
	e, err := s.transportFor(udpContext(), s.cacheController.scope())
	if err != nil {
		t.Fatal(err)
	}
	conn := udpOpened(t, d)
	<-e.ready
	reqs, err := buildReqMsgs("fixture.invalid.", dnsfeature.IPOption{IPv4Enable: true}, func() uint16 { return 0 }, nil)
	if err != nil {
		t.Fatal(err)
	}
	req := reqs[0]
	req.cacheScope = e.scope
	e.mu.Lock()
	e.nextID = 65535
	e.mu.Unlock()
	if err = e.send(udpContext(), req, nil); err != nil {
		t.Fatal(err)
	}
	if q := conn.request(t); q.Id != 65535 {
		t.Fatal("Last wire transaction ID wrong")
	}
	if err = e.send(udpContext(), req, nil); !errors.Is(err, errUDPRequestSpace) {
		t.Fatalf("Live transport transaction wrapped: %v", err)
	}
}
func TestParvazUDPCancelledInitializationWaiterReturnsBeforeNativeExit(t *testing.T) {
	release := make(chan struct{})
	defer close(release)
	d := newUDPFixture()
	d.blockOpen = release
	d.entered = make(chan struct{}, 1)
	s := udpServer(t, d)
	done := udpLookup(s, "fixture.invalid")
	select {
	case <-d.entered:
	case <-time.After(time.Second):
		t.Fatal("Initializer not entered")
	}
	s.cacheController.FlushGeneration()
	if r := labAwaitResult(t, done); !errors.Is(r.err, errCacheGenerationChanged) {
		t.Fatalf("Waiter cancellation missing: %v", r.err)
	}
	if len(s.slots) != 1 {
		t.Fatal("Cancelled waiter released still-running native initializer")
	}
}
