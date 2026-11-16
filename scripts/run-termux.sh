#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
data=${MINA_WORK_DIR:-$HOME/.mina-android/devnet}
mkdir -p "$data"
exec "$root/mina" --network devnet node --work-dir "$data" \
  --http-addr 127.0.0.1 --port "${MINA_HTTP_PORT:-3000}" "$@"
