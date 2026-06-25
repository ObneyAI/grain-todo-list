---
name: nrepl-connect
description: Connect to the project's nREPL server using the nrepl MCP tool
---

# Connect to nREPL

Connect to this project's running nREPL using the `mcp__nrepl__connect` tool.

This is a local dev repo. Its nREPL listens on the fixed port **7888** (started via
`./scripts/nrepl.sh`). The workspace root `.nrepl-port` file will also contain `7888`.

## Steps

1. **Use the port from your task** if it gives one.
2. Otherwise use port **7888** (or read it from the **`.nrepl-port`** file in the
   workspace root — it will be 7888 here).
3. Connect with `mcp__nrepl__connect` using host `localhost` and that port.
4. Verify you're on the right JVM: eval `(+ 1 1)`, then confirm the catalog matches THIS
   project — `(require '[ai.obney.grain.code-agent-tools.interface :as tools]) (keys (tools/catalog))`.
