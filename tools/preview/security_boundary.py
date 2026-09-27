"""Non-regression boundary for explicitly labelled manual TEST, never stable approval."""
def verify(report, binaries, boundary, commit, scanner_exit):
    if scanner_exit not in (0,2):raise ValueError('Scanner execution/transport failure')
    if report.get('source_commit')!=commit or not report.get('native_graphs_included') or not report.get('binary_runtime_verified'):
        raise ValueError('Incomplete or stale runtime scan')
    actual={b['binary']+'|'+b['abi']:b['sha256'] for b in binaries}
    if len(binaries)!=6 or len(actual)!=6 or actual!=boundary['binary_sha256']:
        raise ValueError('Changed or missing native binaries require a new security review')
    known={tuple(item) for item in boundary['known_go_findings']}
    active=0
    for row in report['findings']:
        if row['disposition']!='REVIEW_REQUIRED':continue
        active+=1
        c=row['component']
        if c['ecosystem']!='Go':raise ValueError('Java or unknown ecosystem findings cannot use native TEST boundary')
        for v in row['vulnerabilities']:
            detail=report['advisories'][v['id']]
            if not detail.get('withdrawn') and (c['name'],c['version'],v['id']) not in known:
                raise ValueError('New active advisory; TEST publication blocked')
    if bool(active)!=(scanner_exit==2):raise ValueError('Scanner result/status mismatch')
    if active and report.get('status')!='REVIEW_REQUIRED':raise ValueError('Missing unresolved finding status')
    if not active and report.get('status') not in ('NO_KNOWN_MATCHES_IN_SCANNED_SCOPE','NO_APPLICABLE_MATCHES_IN_BUILT_RUNTIME'):
        raise ValueError('Unknown review status')
    return active
