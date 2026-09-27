# Local workspace budget and safe cleanup

`python3 tools/workspace/guard.py` is read-only. It prints `STOP_HEAVY_WORK` and exits2 above120MiB workspace usage or below256MiB disk free. This is a preflight check, not a filesystem quota or background daemon. Run before/after downloads/builds; put large Android/native builds and APK/source archives in GitHub CI rather than this workspace. Do not claim the disk can never fill.

`python3 tools/workspace/guard.py --clean-audit-cache` removes only the named JVM audit cache after the audit process releases `.cache/audit.lock`. Permitted entries: downloaded `json.jar`, `snakeyaml.jar`, generated `classes/**/*.class`, and synthetic `localhost.p12` created by `tools/audit/run.sh` (NOT an APK signer). Unknown content, symlinks, tracked paths, invalid Git metadata or a busy lock refuse the cleanup without deleting any content. Any other cleanup needs its own evidence and classification; no general `rm -rf .cache`, source-clone pruning or key/backup cleanup is authorized by this tool.

Cleanup writes a content-free persistent record in `docs/workspace-cleanup.jsonl`. Commit that record with the work report. Do not put credentials, subscription data, keys or user backups in GitHub. Source, config, tests, documentation, backup, user data and unknown files are protected. Routine checks create no files.
