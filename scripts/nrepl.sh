#!/usr/bin/env bash

# Start nREPL with dev and test aliases and common middleware
# Usage: ./scripts/nrepl.sh [port]

set -euo pipefail

PORT="${1:-7888}"

cd "$(dirname "$0")/.."

echo "Starting nREPL on port $PORT with :dev and :test aliases..."

clojure -A:dev:test \
  -Sdeps '{:deps {nrepl/nrepl {:mvn/version "1.3.0"}
                  cider/cider-nrepl {:mvn/version "0.50.2"}
                  refactor-nrepl/refactor-nrepl {:mvn/version "3.10.0"}}}' \
  -M -m nrepl.cmdline \
  --port "$PORT" \
  --middleware '[cider.nrepl/cider-middleware refactor-nrepl.middleware/wrap-refactor]'
