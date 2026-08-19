#!/bin/bash
# aapt2 only reports a batch of duplicate-resource errors at a time, so strip, re-merge
# and re-check until the merged values.xml compiles clean.
source /home/user/tools/env.sh
cd /opt/pb/work || exit 1
M=/opt/pb/work/app/build/intermediates/incremental/release/mergeReleaseResources/merged.dir/values/values.xml

for i in 1 2 3 4 5 6 7 8; do
  echo "===== pass $i ====="
  gradle mergeReleaseResources --console=plain > /tmp/merge.log 2>&1
  if [ ! -f "$M" ]; then
    echo "no merged values.xml; merge failed early"
    tail -20 /tmp/merge.log
    exit 1
  fi
  rm -rf /tmp/aapt2out; mkdir -p /tmp/aapt2out
  aapt2 compile "$M" -o /tmp/aapt2out 2> /tmp/aapt2.err
  DUPS=$(grep "duplicate value" /tmp/aapt2.err | sed "s/.*resource '//; s/' with config.*//" | sort -u)
  if [ -z "$DUPS" ]; then
    echo "CLEAN after $i pass(es)"
    grep -c "error:" /tmp/aapt2.err
    head -10 /tmp/aapt2.err
    exit 0
  fi
  echo "$DUPS" | tr '\n' ' '; echo
  python3 /home/user/tools/stripattrs.py $DUPS
done
echo "still dirty after 8 passes"
exit 1
