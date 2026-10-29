#!/bin/bash
set -euo pipefail
exec 3<>/dev/tcp/127.0.0.1/8080
printf 'GET /api/v1/health HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: close\r\n\r\n' >&3
IFS= read -r -t 3 response <&3
[[ "$response" == 'HTTP/1.1 200 '* ]]
