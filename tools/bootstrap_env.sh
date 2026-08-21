#!/bin/bash
# Full environment bootstrap after a sandbox restart.
# swap -> JDK21 -> /opt/pb -> android toolchain
set -x
export DEBIAN_FRONTEND=noninteractive
export PATH=/usr/sbin:/sbin:$PATH

# --- swap (4G) ---
if ! /sbin/swapon --show 2>/dev/null | grep -q swapfile4; then
  sudo fallocate -l 4G /swapfile4 || sudo dd if=/dev/zero of=/swapfile4 bs=1M count=4096
  sudo chmod 600 /swapfile4
  sudo mkswap /swapfile4
  sudo /sbin/swapon /swapfile4
fi
/sbin/swapon --show

# --- packages ---
sudo apt-get update -y
sudo apt-get install -y openjdk-21-jdk unzip zip wget git python3 >/dev/null
ls /usr/lib/jvm

# --- /opt/pb ---
sudo mkdir -p /opt/pb
sudo chown -R $(id -u):$(id -g) /opt/pb

# --- android toolchain ---
bash /home/user/setup_android.sh
echo "BOOTSTRAP_RC=$?"
echo "BOOTSTRAP_DONE"
