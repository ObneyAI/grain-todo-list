# grain-todo-list — agent guide

This is a **compact (non-Polylith)** Grain application — a small teaching app for the Grain
Sessions video series. You build features here by adding **new service directories** under
`src/cjbarre/grain_todo_list/service/` following the Grain golden path. The foundation (auth, app
shell, JWT, email) and the existing `todo-list-service` / `user-service` already ship — **do not
rebuild the foundation**. Keep the code small, direct, and idiomatic; this app is meant to *teach*
Grain patterns.

**Do NOT introduce Polylith** (`bases/`, `components/`, `interface.clj`, service bricks). Keep the
compact layout. When a Grain example uses Polylith paths, translate them with the table at the
bottom of this file — keep the conceptual pattern, drop the ceremony.

## The Charter — what every app you build MUST be
Everything below elaborates these seven. If a step conflicts with the Charter, the Charter wins.
1. **Correct & idiomatic** — service-first, event-sourced grain (commands → events → read-models →
   queries); validate before you invoke; `:missing-schemas` stays empty.
2. **Built to the brief** — implement the brief's capabilities and nothing more; **each acceptance check
   is the contract**, verified pass/fail in the live app.
3. **Live & reachable** — opens at `/` and works as a real user; never a 404, blank page, or dead sign-in.
4. **Secure where it matters** — public-first browsing, but any command/query touching per-user or
   account-scoped data declares a real `:authorized?`; no accidentally-public admin or mutation pages.
5. **Polished** — library-first UI (grow `foundation/ui/components.clj` + each service's
   `ui/components.clj`), every screen passes the `design-review` rubric; intentional empty states.
6. **Verified** — `clojure -T:build test` green; acceptance verified live; `:missing-schemas` empty; CSS built.
7. **Honest** — "done" means all of the above. If something fails and you can't fix it, say exactly what
   failed — **never** report a broken build as success.

## Stack
- **Grain** (event-sourced CQRS) on Clojure, **compact layout**, top namespace **`cjbarre.grain-todo-list`**.
- **In-memory** event store (`:conn {:type :in-memory}`) + a temporary **LMDB** read-model cache (under
  `/tmp`). No SQLite-on-disk, no AWS, no LocalStack — this is a local teaching repo.
- **Datastar** server-rendered reactive UI over SSE. Tailwind + DaisyUI for styling.
- HTTP/Datastar runs on **port 8080**; nREPL via `./scripts/nrepl.sh` on **port 7888** (a local dev
  repo — 7888 is the right port here). `code-agent-tools` is installed by `start` in `:dev` mode.

## Layout
```
src/cjbarre/grain_todo_list.clj                      Integrant system, routes, lifecycle, code-agent-tools install
src/cjbarre/grain_todo_list/
  foundation/
    ui.clj                  app shell + page chrome (DO NOT rebuild)
    ui/components.clj        reusable app-wide visual primitives (the app's library)
    auth_interceptors.clj    auth cookie interceptors
    jwt.clj                  JWT sign/unsign
    email.clj                email boundary (logger stub)
  service/
    todo_list_service/       todo domain: schemas, read_models, commands, queries, ui, ui/components,
                             todo_processors, periodic_tasks
    user_service/            accounts/auth domain (sign-up, login, …)
```
A new feature = a **new service directory** `service/<name>_service/` holding `schemas.clj`,
`read_models.clj`, `commands.clj`, `queries.clj`, `ui.clj`, `ui/components.clj`, and (if needed)
`todo_processors.clj` / `periodic_tasks.clj`. Wire it by **requiring its namespaces** from
`src/cjbarre/grain_todo_list.clj` so its schemas/read-models/commands/queries register, and adding
any non-Grain-provided routes to the `::routes` key. Only add a new service directory when the app
grows a genuinely separate domain; otherwise extend the existing service.

## The golden path (follow in order; never skip)
1. **Event-storm + reconcile against the live catalog FIRST.** `(tools/catalog)` / `(tools/schemas)`
   to see what exists. Model **service-first** — a service is a family of related domain
   functionality; **entities are emergent**, never the starting point. One capability has one
   canonical owning service; never duplicate it.
2. **schema** (`grain-schema`) → events, commands, queries, read-models as Malli `defschemas` in
   `service/<svc>/schemas.clj`.
3. **command handler** (`grain-command-handler`) → validate business rules, emit events.
4. **read-model** (`grain-read-model`) → pure reducer `(state, event) -> state`.
5. **query** (`grain-query-handler`) → read projections, return `:datastar/hiccup`.
6. **view** (`grain-view` / `grain-ui-component`) → pure Hiccup, using `foundation/ui` primitives and
   the checked `ds-ui` DSL. **Building reactive UI beyond a simple form/list — modals/dialogs,
   contenteditable, live updates, multi-signal coordination?** Read grain's authoritative Datastar UI
   DSL reference (`docs/datastar-ui.md` in the **grain-datastar** dep; it has a *Code Agent Checklist*)
   FIRST — locate it dynamically:
   `find "${GITLIBS:-$HOME/.gitlibs}" -path '*grain-datastar*/docs/datastar-ui.md' | head -1`. Don't
   reverse-engineer the DSL from library source.
7. **wire** the service namespaces into `cjbarre.grain-todo-list`, and **test** (`test` skill).

Cross-service collaboration: call the owning service's **canonical command** with
`:skip-event-storage true` so its events fold into the caller's single atomic append. Never reach
into another service's data directly.

## Self-verification discipline (use code-agent-tools over nREPL)
Always `validate` before you mutate, then prove the slice works against the **live** app:
```clojure
(require '[ai.obney.grain.code-agent-tools.interface :as tools])
(tools/catalog)                                  ; discover
(tools/explain-schema :your/command)             ; inspect shape
(tools/validate :command :your/command {…})      ; MUST pass before invoking
(tools/invoke-command! {:command/name :your/command …})
(tools/events {:types #{:your/event-happened}})  ; confirm event landed
(tools/projection :your/read-model)              ; confirm projection
(tools/diagnostics)                              ; runtime/health
```
Then verify visually in the browser — see **Definition of done** below.

## Definition of done — the quality gate (every item, every build)
Backend-correct is **not** done. A build is done only when **all** of these pass — and you report each:
1. **Routes live** — bring changes up with the smallest reload (see below); for new routes restart the
   Integrant system: `(app/stop app)` then `(def app (app/start))`.
2. **Tests green** — `clojure -T:build test` passes (the `test` skill). Run tests from the **shell**,
   not nREPL (loading test nses into the live image hits stale-class errors).
3. **No schema drift** — `(:missing-schemas (tools/catalog))` is empty.
4. **Auth where it matters** — every routed query/command touching per-user or account-scoped data has a
   real `:authorized?` (not `(constantly true)`); no accidentally-public admin/mutation page.
5. **CSS built** — no `404 /css/main.css`.
6. **Acceptance verified** — open `/` and exercise **EACH acceptance check as a user** (click, type, see
   the result); record pass/fail. Acceptance is the contract. Use the **`verify-ui`** skill: the correct
   Playwright snapshot→ref→**target** contract, signing in first on auth apps (the dev mailer only logs
   the token), and asserting data via `tools/projection` instead of clicking where you can.
7. **Design pass** — every screen composes from the app's UI library (`foundation/ui/components.clj` +
   the service's `ui/components.clj`), no ad-hoc layout; run **`design-review`** on each primary screen
   and refine until it passes the rubric.
8. **Honest** — if any item fails and you can't fix it, your summary states exactly which items failed and
   why. **Never report a broken build as success.**

A 404, blank/placeholder, forced sign-in, failing test, schema drift, an unauthorized account-scoped page,
or a cramped/jammed/misaligned screen means it is **not** done.

## Running & reloading — DANGER ZONE, read carefully
Start the app from the REPL:
```clojure
(require '[cjbarre.grain-todo-list :as app])
(def app (app/start))   ; HTTP/Datastar on :8080; installs code-agent-tools
;; open http://localhost:8080/   (sign-in at /auth/sign-in)
(app/stop app)
```
Get your changes live with the SMALLEST reload:
- **Edited an existing namespace** → `(require 'the.exact.ns :reload)` — that one namespace only.
  Command/query/read-model registries update live.
- **Created a NEW service** → add its `require`s to `cjbarre.grain-todo-list`, reload the root ns
  (`(require 'cjbarre.grain-todo-list :reload)`), then restart the system: `(app/stop app)` /
  `(def app (app/start))`.
- **Changed routes** (any new/renamed `:datastar/path` or `::routes` entry) → restart the system
  (the router is snapshotted at boot): `(app/stop app)` / `(def app (app/start))`.

**NEVER `:reload-all`. NEVER reload `ai.obney.*` / library / grain namespaces.** Reloading a namespace
that *defines a protocol* (event-store, kv-store, pedestal, core.async, mulog, …) redefines that
protocol, so the already-running instances stop satisfying it. Symptom: a cascade of
`No implementation of method :X of protocol :Y found for class :Z`. Once you see that, the image is
poisoned — STOP reloading and do a clean restart (`(app/stop app)` then `(def app (app/start))`, or
restart the JVM). Do not try to hand-rebuild protocols.

- CSS: `npm run css:dev` (watch) / `npm run css:build`.
- Tests: `clojure -T:build test` (run from the shell, not nREPL). Lint: `clj-kondo --lint src test`.

## Production-grade defaults (keep them)
- **Routes use query params, NEVER path params.** Use `:datastar/path "/widget"` and read the id from
  `:query` (`/widget?widget-id=<uuid>`) — never `:datastar/path "/widget/:widget-id"`. Links/redirects
  use `?param=` too (resolve with `ds-ui/href`, never literal app URLs).
- **Queries are event-driven. NEVER use `:datastar/fps` for normal Grain state.** Every page query
  declares `:grain/read-models {<rm> <version>}` so it re-renders only when those events fire. Omitting
  both `:grain/read-models` and `:datastar/fps` does **not** make a page one-shot — grain defaults to
  **30fps polling** (the server re-runs the query ~30×/sec/tab, diffs `:datastar/hiccup`, patches on
  change; wasteful + stale). The **one** sanctioned use of `:datastar/fps` is `:datastar/fps 0` on a
  genuinely static page (e.g. the dev gallery) — that is the only way to get a true one-shot render.
- **The app always has a home at `/`.** Make the user's primary experience the home page. Keep exactly
  one `/`. Add secondary pages as query-param routes.
- **Hybrid auth — public-first browsing, real auth where it matters.** Public-read pages (the home /
  primary browsing experience) use `:authorized? (constantly true)` so the app is reachable without
  signing in. BUT any command or query that **writes** or reads **per-user / account-scoped** data MUST
  declare a real `:authorized?` (`auth/authenticated?` or a role/tenant check) — **never `(constantly
  true)` on a mutation or account-scoped page**. A single-user app with no accounts can stay public; the
  moment there are accounts/roles, gate them.
- **UI is library-first.** Grow the app's component library — app-wide visual primitives in
  `foundation/ui/components.clj`, domain widgets in each service's `ui/components.clj` — and compose
  **every** screen from it; never hand-roll one-off layout in a page (`ui.clj`). See the
  `grain-ui-component` skill. UI functions are **pure**: they receive data and return Hiccup / checked
  DSL Hiccup; they do not read the event store, mutate state, run commands, or touch Integrant
  components.
- **Design a polished empty state** — never a blank screen (no demo data is seeded; make "nothing yet"
  look intentional and inviting).
- **New routes register only on restart.** Iterate on backend logic with code-agent-tools/nREPL, but a
  feature isn't done until you restart the system and verify the live route in the browser as a user
  (see **Definition of done**).
- **Tenant isolation is structural** — never add `[:tenant …]` tags.
- **No schema drift** — `(:missing-schemas (tools/catalog))` must stay empty.

## Avoid these patterns
Do not use these for new code:
- `obneyai/grain-datastar-v2` / `ai.obney.grain.datastar_v2.interface` (use `grain-datastar` →
  `ai.obney.grain.datastar.interface :as ds` and `ai.obney.grain.datastar.ui :as ds-ui`).
- `ds/action-route`; hand-built command submissions that set `"command/name"` manually.
- helper fns named like `command-click`, `command-assignments`, `signal-ref`, `data-signals`.
- unescaped string interpolation into Datastar event JavaScript.
- literal app hrefs such as `"/task?task-id=..."` (use `ds-ui/href`).
- raw `data-signals` / `data-bind` / `data-on:*` for behavior the checked DSL supports; raw
  `data-ignore-morph` when `:morph/ignore` fits; raw DOM property effects when `:bind/prop` fits.
- broad app-owned Datastar wrappers that hide the checked DSL.
- `:datastar/fps` for normal Grain state pages (`:datastar/fps 0` on a truly static page is the only
  exception).
- `ds-ui/defcomponent` as a default component style — ordinary pure functions are clearer in this
  teaching app.

Raw `data-*`, `ds-ui/action`, and `ds-ui/js` are migration/interop escape hatches. Any production use
should be small and easy to justify.

## Debugging (REPL-first — do not guess at Grain behavior)
Read raw events:
```clojure
(require '[ai.obney.grain.event-store-v3.interface :as es])
(into [] (es/read (:cjbarre.grain-todo-list/event-store app) {:tenant-id app/tenant-id}))
```
If a page does not update after a command: confirm the command emitted an event; the event type is in
the read model; the query's `:grain/read-models` includes that read model; `:query/result` changes
when the data does; `:datastar/hiccup` is lowered through `ds-ui/hiccup`; the page is a `ds/routes`
Datastar route. If a command appears to do nothing: check `/actions` is wired with `ds/action-handler`;
`ds-ui/dispatch` uses the registered command keyword; the payload keys match the command schema; test
the command directly in the REPL; check authorization. If a link is wrong: confirm `ds-ui/href`, that
the query keyword exists and declares `:datastar/path`, and params match the schema. If CSS is missing:
`npm run css:build`, confirm `resources/public/css/main.css` exists, and that classes appear in
`src/**/*.clj` so Tailwind can discover them.

## Skills
`grain-schema`, `grain-command-handler`, `grain-read-model`, `grain-query-handler`, `grain-view`,
`grain-ui-component`, `design-review` (visual quality gate), `grain-service` (scaffold a whole
service), `grain-todo-processor`, `grain-periodic-task`, `nrepl-connect`, `verify-ui` (acceptance in the
live app — Playwright snapshot→ref→target + sign-in recipe), `qa`, `test`, plus the **allium** spec
family (`allium`, `elicit`, `distill`, `tend`, `weed`, `propagate`). Use them — they encode the idioms.

## Translating Grain/Polylith examples to this repo
| Example path (Polylith) | This project (compact) |
| --- | --- |
| `bases/web-api/src/.../core.clj` | `src/cjbarre/grain_todo_list.clj` |
| new `components/<name>/` | new `src/cjbarre/grain_todo_list/service/<name>_service/` |
| `components/<svc>/core/schemas.clj` | `service/<svc>/schemas.clj` |
| `components/<svc>/core/commands.clj` | `service/<svc>/commands.clj` |
| `components/<svc>/core/queries.clj` | `service/<svc>/queries.clj` |
| `components/<svc>/core/read_models.clj` | `service/<svc>/read_models.clj` |
| `components/<svc>/core/views.clj` | `service/<svc>/ui.clj` (+ `ui/components.clj`) |
| `components/<svc>/interface.clj` | no app-local equivalent; require the local namespace directly |
| `app.ui-kit` / `app.ui` | `foundation/ui/components.clj` (app-wide) + service `ui/components.clj` (domain) |
| `(app.web-api.core/restart!)` / `load-component!` | `(app/stop app)` / `(def app (app/start))` |
| `clojure -M:poly test` | `clojure -T:build test` |
| `bases/.../public/css/main.css` | `resources/public/css/main.css` |

Keep the conceptual patterns. Drop the Polylith ceremony.
