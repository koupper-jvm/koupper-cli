#!/bin/bash
set -e

# Load env vars (works in both interactive and non-interactive shells)
[ -f "$HOME/.profile" ] && source "$HOME/.profile"

octopus_running=false
if (echo > /dev/tcp/127.0.0.1/9998) >/dev/null 2>&1; then
  octopus_running=true
fi

if [ "$octopus_running" = false ]; then
  echo "🐙 Octopus Engine is offline. Booting background daemon..."
  nohup java -jar "/home/tdn-dell/.koupper/libs/octopus.jar" >/dev/null 2>&1 &
  sleep 2
fi

java -Dfile.encoding=UTF-8 -jar "/home/tdn-dell/.koupper/libs/koupper-cli.jar" "$@"
