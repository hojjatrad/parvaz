# Third-party additions in the subscription-import branch

## SnakeYAML 2.4

- Maven artifact: `org.yaml:snakeyaml:2.4`
- Purpose: parse Clash/Mihomo YAML into safe Java maps/lists; not a YAML-to-Java object loader.
- License: Apache License, Version 2.0, as declared by the artifact's Maven POM.
- License copy shipped in `app/src/main/assets/licenses/snakeyaml-LICENSE.txt`.
- Artifact source: https://repo.maven.apache.org/maven2/org/yaml/snakeyaml/2.4/
- JAR SHA-256 checked by the JVM test script:
  `ef779af5d29a9dde8cc70ce0341f5c6f7735e23edff9685ceaa9d35359b7bb7f`

The Android dependency is version-pinned; this is not a claim that the entire Gradle
dependency graph has dependency verification enabled. The test JAR is fetched into
ignored `.cache/audit`, not checked into Git.

Only `SafeConstructor` is used. Duplicate keys, arbitrary Java tags, alias references,
excessive events/depth and oversized inputs are rejected. Plain YAML scalars are kept
as strings so implicit YAML 1.1 typing cannot corrupt passwords or leading-zero keys.
Unknown connection options reject their node instead of silently stripping options.

This document records this branch's new dependency, not a complete license inventory
of the older application's existing dependencies/native cores.
