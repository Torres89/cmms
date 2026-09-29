#!/bin/sh
# Runs from the nginx image's /docker-entrypoint.d before nginx starts.
# Writes the deployment's URLs where index.html loads them.
set -eu

escape() {
  printf '%s' "$1" | sed 's/\\/\\\\/g; s/"/\\"/g'
}

cat > /usr/share/nginx/html/env.js <<EOF
window.__ATLAS_ENV__ = { API_URL: "$(escape "${API_URL:-}")", AGENT_URL: "$(escape "${AGENT_URL:-}")" };
EOF
