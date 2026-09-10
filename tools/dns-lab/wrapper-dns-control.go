// SPDX-License-Identifier: LGPL-3.0-only
// Experimental additions to pinned AndroidLibXrayLite. Not consumed by APK builds.
package libv2ray

import (
	"crypto/rand"
	"encoding/json"
	"errors"
	appdns "github.com/xtls/xray-core/app/dns"
	dnsfeature "github.com/xtls/xray-core/features/dns"
	"strconv"
)

// This is an ownership stamp, not a credential or a public network controller.
var errParvazDNSDrainPending = errors.New("retired DNS UDP operations have not exited")

func parvazNewDNSSession() string { return rand.Text() }

type parvazDNSWireResult struct {
	Status              string `json:"status"`
	Session             string `json:"session"`
	Revision            string `json:"revision"`
	LogicalApplied      bool   `json:"logical_applied"`
	CacheControllers    int    `json:"cache_controllers"`
	UncoveredConditions int    `json:"uncovered_conditions"`
	OwnedUDPLeases      int    `json:"owned_udp_leases"`
	FullChainFlushed    bool   `json:"full_chain_flushed"`
	RebuildRequired     bool   `json:"rebuild_required"`
}

func parvazDNSResult(status string) string {
	b, _ := json.Marshal(parvazDNSWireResult{Status: status, RebuildRequired: true})
	return string(b)
}
func (x *CoreController) parvazDNSResult(report appdns.ParvazDNSControlResult) string {
	b, _ := json.Marshal(parvazDNSWireResult{Status: report.Status, Session: x.parvazDNSSession, Revision: strconv.FormatUint(report.Revision, 10), LogicalApplied: report.LogicalApplied, CacheControllers: report.CacheControllers, UncoveredConditions: report.UncoveredConditions, OwnedUDPLeases: report.OwnedUDPLeases, RebuildRequired: true})
	return string(b)
}

// Lab API intentionally cannot claim full-chain success or internet readiness.
// TryLock means busy native start/stop is not queued behind a blocking Close.
func (x *CoreController) ParvazLabDNSState() string {
	if !x.coreMutex.TryLock() {
		return parvazDNSResult("BUSY")
	}
	defer x.coreMutex.Unlock()
	if !x.IsRunning || x.coreInstance == nil || x.parvazDNSSession == "" {
		return parvazDNSResult("INACTIVE")
	}
	feature, ok := x.coreInstance.GetFeature(dnsfeature.ClientType()).(*appdns.DNS)
	if !ok {
		return parvazDNSResult("UNSUPPORTED_REBUILD_REQUIRED")
	}
	return x.parvazDNSResult(feature.ParvazDNSControlState())
}
func (x *CoreController) ParvazLabInvalidateDNS(session, revision string) string {
	if !x.coreMutex.TryLock() {
		return parvazDNSResult("BUSY")
	}
	defer x.coreMutex.Unlock()
	if !x.IsRunning || x.coreInstance == nil || x.parvazDNSSession == "" {
		return parvazDNSResult("INACTIVE")
	}
	if session == "" || session != x.parvazDNSSession {
		return parvazDNSResult("STALE_SESSION")
	}
	expected, err := strconv.ParseUint(revision, 10, 64)
	if err != nil || strconv.FormatUint(expected, 10) != revision {
		return parvazDNSResult("INVALID_REVISION")
	}
	feature, ok := x.coreInstance.GetFeature(dnsfeature.ClientType()).(*appdns.DNS)
	if !ok {
		return parvazDNSResult("UNSUPPORTED_REBUILD_REQUIRED")
	}
	return x.parvazDNSResult(feature.ParvazTryInvalidateDNS(expected))
}

// Known UDP resource ownership survives StopLoop and gates reuse of THIS
// controller. It is not a process-global gate and proves nothing about other
// transport types or fresh controller objects created by an Android caller.
func (x *CoreController) parvazDNSRetiredReady() bool {
	if x.parvazRetiredDNS == nil {
		return true
	}
	r := x.parvazRetiredDNS.ParvazDNSControlState()
	if r.Status == "BUSY" || r.OwnedUDPLeases != 0 {
		return false
	}
	x.parvazRetiredDNS = nil
	return true
}
func (x *CoreController) parvazRetireDNSSession() {
	x.parvazDNSSession = ""
	if x.coreInstance != nil {
		if feature, ok := x.coreInstance.GetFeature(dnsfeature.ClientType()).(*appdns.DNS); ok {
			x.parvazRetiredDNS = feature
		}
	}
}
