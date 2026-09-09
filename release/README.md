# Release binaries

APK and checksum files belong in [GitHub Releases](https://github.com/hojjatrad/parvaz/releases), not new source commits.

The redundant 1.16/1.17/1.18 ZIPs were removed from the current source tree after verifying that the version release pages remain available. No release asset, tag or Git history was deleted or rewritten.

Exact original ZIP blobs remain recoverable at immutable commit `f6d0b2977691e34d7574febcb7fbfddceffd46fd` under this directory. Historical blob IDs:

- `Parvaz-1.16-APK.zip`: `51bef02c9702e8b6cfb5b193d6ffe23584fecece`
- `Parvaz-1.17-APK.zip`: `5495a75a9a46bb55da7dfb65996132284153eb4f`
- `Parvaz-1.18-APK.zip`: `ed9e330160f46adbc5c18379c6f015ee8419e443`

This reduces clean/shallow checkouts, not the existing repository's full historical pack size. History rewriting requires separate owner approval.
