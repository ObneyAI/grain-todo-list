# AGENTS.md

This is **grain-todo-list**: a small **compact (non-Polylith)** Grain teaching app for the Grain
Sessions video series. Full agent guidance — stack, layout, the Grain golden path, self-verification
with `code-agent-tools`, and running/reloading — lives in **`CLAUDE.md`**. Read it first.

**The Charter — what every app must be** (see CLAUDE.md): correct & idiomatic · built to the brief
(acceptance = the contract) · live & reachable at `/` · secure where it matters · polished · verified ·
honest. The Charter wins over any single step.

**Do NOT introduce Polylith** (`bases/`, `components/`, `interface.clj`, service bricks). Keep the
compact layout; translate Polylith examples with the table in `CLAUDE.md`.

Quick contract:
- Build features as **new service directories** (`src/cjbarre/grain_todo_list/service/<name>_service/`,
  namespace `cjbarre.grain-todo-list.service.<name>-service.*`). Don't rebuild the shipped foundation
  (`foundation/ui`, `auth-interceptors`, `jwt`, `email`) or the existing `todo-list-service` /
  `user-service` unless that's the work. Only add a new service dir for a genuinely separate domain.
- Follow the golden path in order: **event-storm + catalog reconcile → schema → command → read-model →
  query → view → wire → test**. Service-first; entities are emergent.
- **`validate` before `invoke-command!`**; prove each slice via `events`/`projection`.
- **UI is library-first and pure.** Grow the app's component library — app-wide primitives in
  `foundation/ui/components.clj`, domain widgets in each service's `ui/components.clj` — and compose
  every screen from it; never hand-roll one-off layout in a page. Use the real `ds-ui` checked DSL
  (`grain-ui-component` skill). UI fns receive data and return Hiccup — no event-store reads, mutations,
  commands, or Integrant access. **Building reactive UI beyond a simple form/list (modals/dialogs,
  contenteditable, live updates, multi-signal coordination)? Read grain's authoritative Datastar UI DSL
  reference first** — `docs/datastar-ui.md` in the **grain-datastar** dep (it has a *Code Agent
  Checklist*); locate it dynamically:
  `find "${GITLIBS:-$HOME/.gitlibs}" -path '*grain-datastar*/docs/datastar-ui.md' | head -1`.
- **Hybrid auth:** public-first browsing, but any command/query touching per-user/account-scoped data
  declares a real `:authorized?` (never `(constantly true)` on a mutation/account page).
- **Definition of done = the gate (all of):** restart system (`(app/stop app)` / `(def app (app/start))`)
  → `clojure -T:build test` green → `:missing-schemas` empty → auth on account-scoped pages → CSS built
  → open `/` and verify **each acceptance check** pass/fail as a user → **`design-review`** passes on
  each screen. Keep exactly one `/`. **Honest:** if any gate item fails and you can't fix it, say which —
  never report a broken build as success.
- Reloading: edited ns → `(require 'the.ns :reload)` (that ns only); NEW service → add its requires to
  `cjbarre.grain-todo-list`, reload the root ns, restart the system; route change → restart the system.
  **NEVER `:reload-all` or reload `ai.obney.*`/library namespaces** — it redefines protocols and breaks
  the running app (`No implementation of method … of protocol …`). Tests: `clojure -T:build test`.
- Queries are **event-driven**: every page query declares `:grain/read-models {<rm> <version>}`; **never
  use `:datastar/fps`** for normal state (omitting both keys defaults to 30fps polling; `:datastar/fps 0`
  on a truly static page is the only sanctioned use).
- Polished empty states (no demo data), structural tenancy, zero schema drift.
