#!/bin/bash
# The Gradle "Can not add resource ... to table" message is useless. Running aapt2
# compile directly on the merged values.xml prints the real, specific errors.
source /home/user/tools/env.sh
M=/opt/pb/work/app/build/intermediates/incremental/release/mergeReleaseResources/merged.dir/values/values.xml
ls -la "$M"
mkdir -p /tmp/aapt2out
aapt2 compile "$M" -o /tmp/aapt2out --verbose 2>&1 | head -60
