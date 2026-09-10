#!/usr/bin/env python3
"""Read-only, opt-in environment summary. No serial, IP, SSID, endpoint, account,
logcat or configuration is retained. This is NOT a leak/battery test result."""
import json
import re
import shutil
import subprocess


def adb(*args):
    try:
        result = subprocess.run(['adb', *args], capture_output=True, text=True, timeout=15)
        return result.stdout.strip() if result.returncode == 0 else ''
    except (OSError, subprocess.TimeoutExpired):
        return ''


def number(text):
    return int(text) if re.fullmatch(r'[0-9]{1,6}', text or '') else None


def main():
    report = {'format': 'PARVAZ_HARDWARE_ENVIRONMENT_V1', 'physical_tests': 'NOT_RUN'}
    if not shutil.which('adb') or adb('get-state') != 'device':
        report['device_access'] = 'UNAVAILABLE'
    else:
        report['device_access'] = 'AVAILABLE'
        report['sdk'] = number(adb('shell', 'getprop', 'ro.build.version.sdk'))
        package = adb('shell', 'dumpsys', 'package', 'com.parvaz.tunnel')
        version = re.search(r'\bversionName=([0-9.]{1,40})(?:\s|$)', package)
        report['installed_version'] = version.group(1) if version else None
        always_on = adb('shell', 'settings', 'get', 'secure', 'always_on_vpn_app')
        lockdown = adb('shell', 'settings', 'get', 'secure', 'always_on_vpn_lockdown')
        report['parvaz_always_on'] = always_on == 'com.parvaz.tunnel'
        report['os_lockdown_setting'] = lockdown == '1'
        idle = adb('shell', 'dumpsys', 'deviceidle', 'get', 'deep')
        report['idle_state'] = idle if re.fullmatch(r'[A-Z_]{2,40}', idle) else None
        battery = adb('shell', 'dumpsys', 'battery')
        for name in ['level', 'status', 'temperature']:
            match = re.search(r'^\s*' + name + r':\s*([0-9]{1,6})\s*$', battery, re.M)
            report['battery_' + name] = number(match.group(1)) if match else None
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == '__main__':
    main()
