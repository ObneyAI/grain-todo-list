# Pattern Compendium

This project is intentionally small. Build the app in the compact Clojure
layout already present in the repo. Do not introduce Polylith `bases/`,
`components/`, `interface.clj`, or service bricks.

The goal is to keep application code in a few obvious places:

- `src/cjbarre/grain_todo_list.clj` - application composition, Integrant system, routes, lifecycle
- `src/cjbarre/grain_todo_list/foundation/ui.clj` - application shell and app-level UI chrome
- `src/cjbarre/grain_todo_list/foundation/ui/components.clj` - reusable app-wide visual primitives
- `src/cjbarre/grain_todo_list/service/todo_list_service/schemas.clj` - schema constants, validation helpers, and primitive-specific `defschemas`
- `src/cjbarre/grain_todo_list/service/todo_list_service/read_models.clj` - read model reducers, `defreadmodel`s, and projection helpers
- `src/cjbarre/grain_todo_list/service/todo_list_service/commands.clj` - command helpers and `defcommand`s
- `src/cjbarre/grain_todo_list/service/todo_list_service/queries.clj` - query data assembly and Datastar render boundary
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj` - page-level todo UI composition
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj` - todo-specific UI controls, cards, lists, review widgets, and gallery specimens
- `src/cjbarre/grain_todo_list/service/todo_list_service/todo_processors.clj` - todo processor lifecycle
- `src/cjbarre/grain_todo_list/service/todo_list_service/periodic_tasks.clj` - periodic trigger lifecycle

Supporting files:

- `css/main.css` - Tailwind and DaisyUI input CSS plus project-specific component classes
- `resources/public/css/main.css` - generated CSS output
- `deps.edn` - Clojure classpath and dependencies
- `package.json` - CSS build scripts

If a Grain example uses Polylith paths, translate it into the local namespaces
above. Keep the conceptual pattern and drop the ceremony.

---

## Architecture

This is a compact Grain app with server-rendered reactive UI.

Stack:

- Clojure
- Integrant
- Grain event store, command routes, query routes, todo processors, periodic tasks
- Grain Datastar server routes and checked Datastar UI DSL
- Tailwind CSS and DaisyUI

Use the current Grain Datastar dependency:

```clojure
obneyai/grain-datastar
{:git/url "https://github.com/ObneyAI/grain.git"
 :sha "3db1781462d316570e53448b778d00c844053fd3"
 :deps/root "projects/grain-datastar"}
```

Server integration uses:

```clojure
[ai.obney.grain.datastar.interface :as ds]
```

Interactive UI authoring uses:

```clojure
[ai.obney.grain.datastar.ui :as ds-ui]
```

Application namespace pattern:

```clojure
(ns cjbarre.grain-todo-list
  (:require [ai.obney.grain.code-agent-tools.interface :as code-agent-tools]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            [ai.obney.grain.datastar.interface :as ds]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.query-processor.interface :as query-processor]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.webserver.interface :as ws]
            [cjbarre.grain-todo-list.service.todo-list-service.commands]
            [cjbarre.grain-todo-list.service.todo-list-service.periodic-tasks :as periodic-tasks]
            [cjbarre.grain-todo-list.service.todo-list-service.queries]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models]
            [cjbarre.grain-todo-list.service.todo-list-service.schemas]
            [cjbarre.grain-todo-list.service.todo-list-service.todo-processors :as todo-processors]
            [clojure.set :as set]
            [com.brunobonacci.mulog :as u]
            [integrant.core :as ig]
            [io.pedestal.http :as http]))
```

---

## File Responsibilities

### App File

Use `src/cjbarre/grain_todo_list.clj` only for app composition:

- Integrant system map
- app-level lifecycle functions: `start`, `stop`
- event store, pubsub, cache, context, processor, route, and webserver keys
- route assembly
- Datastar route and command action endpoint wiring
- delegation to service processor and periodic-task lifecycle functions
- requiring service namespaces so schemas, read models, commands, and queries register

Do not put todo domain logic, command handlers, query handlers, schemas, read
models, or page rendering in the root app namespace.

### App UI File

Use `src/cjbarre/grain_todo_list/foundation/ui.clj` for application-level UI chrome:

- `app-shell` and shared page container structure
- app-level error display
- product-level labels, title composition, and page chrome

This namespace may use checked Datastar bindings for app-owned adapter signals
such as `error`, but it should not depend on todo service widgets.

### App Components UI File

Use `src/cjbarre/grain_todo_list/foundation/ui/components.clj` for reusable app-wide visual
primitives:

- buttons, chips, fields, panels, lists, cards, and surfaces
- shell-neutral field and action primitives
- typography and metadata helpers

Do not put command payload builders, signal string helpers, route strings, or
Datastar JavaScript construction helpers here. Todo behavior belongs in the
todo service UI component namespace and should use the checked DSL.

### Service Files

Use `src/cjbarre/grain_todo_list/service/todo_list_service/` for todo domain behavior:

- `schemas.clj` owns enum constants, validation helpers, and separate `defschemas` forms for common types, events, commands, queries, and read models.
- `read_models.clj` owns event-type sets, reducer multimethods, `defreadmodel`s, and projection helper functions.
- `commands.clj` owns anomaly helpers, command helper functions, and `defcommand`s.
- `queries.clj` owns query data assembly and the `ds-ui/hiccup` render boundary.
- `ui.clj` owns pure page-level todo Hiccup composition.
- `ui/components.clj` owns todo-specific controls, task/project/review widgets, and todo gallery specimens.
- `todo_processors.clj` owns todo processor start/stop functions.
- `periodic_tasks.clj` owns periodic trigger start/stop functions.

### Service UI Files

UI functions should be pure. They receive data and return Hiccup or checked DSL
Hiccup. They should not read the event store, mutate state, run commands, or
access Integrant components.

Page functions live in `todo_list_service/ui.clj`:

```clojure
(defn tasks-page
  [{:keys [tasks projects]}]
  (shell {:title "Tasks"}
         (app-ui/action-error)
         [:div {:class "mb-6"} (tc/quick-add {})]
         (tc/task-list tasks "No active tasks." projects)))
```

Todo-specific component functions live in `todo_list_service/ui/components.clj`
and may use the checked Datastar UI DSL for interaction.

---

## Backend Patterns

### Integrant System

The system map lives directly in `cjbarre.grain-todo-list/system`.

Keep additions local and explicit:

```clojure
(def system
  {::event-store {:event-pubsub (ig/ref ::event-pubsub)
                  :conn {:type :in-memory}}
   ::event-pubsub {:type :core-async
                   :topic-fn :event/type}
   ::cache {}
   ::context {:event-store (ig/ref ::event-store)
              :cache (ig/ref ::cache)
              :tenant-id tenant-id
              :event-pubsub (ig/ref ::event-pubsub)}
   ::processors {:event-store (ig/ref ::event-store)
                 :cache (ig/ref ::cache)
                 :tenant-id tenant-id}
   ::periodic-triggers {:event-store (ig/ref ::event-store)
                        :tenant-id tenant-id}
   ::routes {:context (ig/ref ::context)}
   ::webserver {::http/routes (ig/ref ::routes)
                ::http/port 8080
                ::http/join? false
                ::http/resource-path "public"}})
```

### Routes

Routes are assembled in the `::routes` Integrant key.

Use `ds/routes` for Datastar query pages and stream routes. Wire Datastar
commands at `/actions` with `ds/action-handler`; the shim emits this endpoint as
the reserved `__grainAction` signal.

```clojure
(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (ds/routes context
              {}
              {:datastar/shim-opts {:head datastar-head
                                     :html-attrs {:data-theme "workshop"}}})
   #{["/actions" :post [(ds/action-handler context {})] :route-name ::actions]
     ["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]
     ["/favicon.ico" :get [(fn [_] {:status 204 :body ""})] :route-name ::favicon]}))
```

### Command Handlers

Commands live in `todo_list_service/commands.clj`.

Keep each command small:

```clojure
(defcommand :todo capture-task
  {:authorized? (constantly true)}
  "Add a new task."
  [{{:keys [task-id title project-id due-within-days order]} :command :as ctx}]
  ;; validate read-model state, then emit events
  {:command-result/events
   [(->event {:type :todo/task-captured
              :tags #{[:task task-id]}
              :body {:task-id task-id
                     :title title
                     :status :active
                     :order order}})]
   :datastar/signals {:__toast "Task added."}})
```

Validation pattern:

- return an anomaly for invalid input
- return `:command-result/events` on success
- return `:datastar/signals` for app-level feedback such as `:__toast`

Do not return form field reset signals such as `:title ""` from command
handlers. Field reset is browser UI behavior and belongs in the component effect
with `ds-ui/reset-signal`.

### Read Models

Read models and read helpers live in `todo_list_service/read_models.clj`.

Pattern:

```clojure
(def todo-event-types
  #{:todo/task-captured
    :todo/task-completed
    :todo/task-deleted})

(defmulti tasks* (fn [_state event] (:event/type event)))

(defreadmodel :todo tasks
  {:events todo-event-types
   :version 1}
  [state event]
  (tasks* state event))
```

Increment `:version` when reducer behavior changes in a way that should rebuild
cached projections.

### Query Handlers

Queries live in `todo_list_service/queries.clj` and call page functions from
`todo_list_service/ui.clj`.

Every Datastar query should lower checked UI through `ds-ui/hiccup` before
returning `:datastar/hiccup`:

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.queries
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.service.todo-list-service.ui :as ui]))

(defn render [page]
  (ds-ui/hiccup page))

(defquery :todo tasks-page
  {:authorized? (constantly true)
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [tasks (vec (rm/active-tasks ctx))
        projects (vec (rm/active-project-summaries ctx))]
    {:query/result {:tasks tasks :projects projects}
     :datastar/hiccup (render (ui/tasks-page {:tasks tasks
                                              :projects projects}))}))
```

Choose query behavior by metadata:

| Behavior | Metadata | Use for |
| --- | --- | --- |
| Static one-shot render | `:datastar/fps 0` | static pages and galleries |
| Polling render | no `:grain/read-models` | fallback only; avoid relying on it |
| Event-driven render | `:grain/read-models {...}` | app state stored through events |

Omitting `:grain/read-models` does not make a page one-shot. Without read
models the query falls back to polling, and `:datastar/fps` defaults to 30:
the server re-runs the query ~30 times per second per open tab, diffing
`:datastar/hiccup` and patching only on change. The browser sees a static
page, but the server is in a polling loop. For a genuinely static page, set
`:datastar/fps 0` explicitly — this is the one intended use of
`:datastar/fps`; do not add it for normal Grain state, where event-driven
reads are the right tool.

### Todo Processors And Periodic Tasks

Event-driven side effects and tenant-poller lifecycle live in
`todo_processors.clj`. Scheduled trigger lifecycle lives in
`periodic_tasks.clj`.

Keep periodic tasks idempotent. If a task can run twice, use event-store CAS or
command-level validation to avoid duplicate effects.

---

## Datastar UI DSL

`ai.obney.grain.datastar.ui` is the checked UI layer for Datastar attributes.
Application code should require it as `ds-ui`.

The DSL has three jobs:

- build Datastar attributes from data, not handwritten strings
- keep command/query payloads explicit so ambient page state is not sent by accident
- own signal scoping so components do not manually name or isolate local signals

The normal production boundary is:

```clojure
(ds-ui/hiccup view)
```

### Signals

Use `ds-ui/with-signals` for component-local client state:

```clojure
(ds-ui/with-signals [title {:init ""}]
  [:input {:bind/value title}])
```

Signal options:

- `:init` declares the initial client value
- `:name` gives a semantic generated-name base when tests or interop need one
- `:stable? true` uses the exact `:name` as intentional page-level shared state

Nested or repeated components may reuse local signal names. The DSL owns signal
isolation and generates scoped Datastar names during `ds-ui/hiccup` lowering:

```clojure
[:div
 (quick-add {})
 (quick-add {:project-id project-id})]
```

Application code should not manually build `data-signals` strings or call
`ds-ui/with-signal-scope` for normal UI. Reserve explicit signal scopes for
framework code, tests, or rare raw interop that the checked DSL cannot express.

Use stable signals only for page-level state that independent patches must
share, such as a selected row id used by a list, detail panel, and dialog. Do
not use stable signals to make component-local generated names pretty.

Use `ds-ui/indexed` only when repeated inputs edit elements inside one collection
signal and the whole collection is later sent in a command or refresh payload.

### Bindings

Checked bindings lower to Datastar attributes:

```clojure
[:input {:bind/value title}]
[:p {:bind/text (ds-ui/trimmed title)}]
[:section {:bind/show (ds-ui/js "$error")}]
[:button {:bind/attr {:disabled saving?}}]
[:input {:type "checkbox"
         :bind/prop {:checked selected?}}]
[:canvas {:morph/ignore true}]
```

Use `:bind/value` for form values, `:bind/text` for text content,
`:bind/show` for visibility, `:bind/class` for class expressions, and
`:bind/attr` for attribute maps. Use `:bind/prop` for DOM properties such as
checkbox `checked`. Use `:morph/ignore` for browser-owned DOM state that
Datastar should not morph, such as canvas or third-party controlled content.

Use element-level `:effect` only for reactive Datastar effects that should run
when the element is initialized or patched:

```clojure
[:section {:effect (ds-ui/when-effect stale?
                     (ds-ui/refresh :todo/tasks-page {}))}]
```

### Events And Effects

Checked events use explicit maps:

```clojure
{:on/click {:effect (ds-ui/dispatch :todo/complete-task
                                    {:task-id task-id})}}

{:on/submit {:effect (ds-ui/effects
                      (ds-ui/dispatch :todo/capture-task
                                      {:title (ds-ui/trimmed title)})
                      (ds-ui/reset-signal title))
             :modifiers {:prevent true}}}
```

Use `:on/signal-patch` for Datastar's signal patch hook when a signal change
should refresh a server-rendered region:

```clojure
{:on/signal-patch
 {:effect (ds-ui/when-effect selected-task-id
            (ds-ui/refresh :todo/task-page
                           {:task-id selected-task-id}))}}
```

Common effects:

- `ds-ui/dispatch` posts an explicit command payload to `__grainAction`
- `ds-ui/refresh` posts an explicit query payload to a stream route
- `ds-ui/href` resolves a registered query to a page URL
- `ds-ui/set-signal` sets one signal
- `ds-ui/reset-signal` restores a signal's declared `:init`
- `ds-ui/clear-errors` clears Grain's conventional Datastar error signals
- `ds-ui/blur` calls `el.blur()`
- `ds-ui/effects` runs effects in order
- `ds-ui/when-effect` runs an effect when a predicate is truthy
- `ds-ui/choose-effect` chooses between effects
- `ds-ui/on-keys` maps keyboard keys to effects

### Expressions

Use signal handles directly in bindings, expressions, and payloads.

Useful expression helpers:

```clojure
(ds-ui/trimmed title)
(ds-ui/num due-within-days)
(ds-ui/num-cents amount)
(ds-ui/present? title)
(ds-ui/changed? title old-title)
(ds-ui/evt :key)
(ds-ui/indexed amounts idx)
(ds-ui/js "Math.max(0, " (ds-ui/num amount) ")")
```

Prefer checked expression helpers. Use `ds-ui/js` only when the checked
vocabulary cannot express the condition cleanly.

Use `ds-ui/num-cents` for money inputs whose command schema expects integer
cents. Use `ds-ui/indexed` for a single element inside a collection signal; pass
the collection signal itself when dispatching the whole collection.

### Forms

Forms should declare local signals, submit explicit command payloads, and reset
field signals in the UI effect:

```clojure
(defn quick-add [{:keys [project-id]}]
  (ds-ui/with-signals [title {:init ""}]
    [:form {:on/submit
            {:effect
             (ds-ui/effects
              (ds-ui/dispatch :todo/capture-task
                              (cond-> {:title (ds-ui/trimmed title)}
                                project-id (assoc :project-id project-id)))
              (ds-ui/reset-signal title))
             :modifiers {:prevent true}}}
     [:input {:class "input input-bordered min-w-0 flex-1"
              :aria-label "Task title"
              :required true
              :bind/value title}]
     [:button {:class "btn btn-primary" :type "submit"} "Add"]]))
```

### Buttons And Chips

Buttons and chips should dispatch commands directly:

```clojure
[:button {:type "button"
          :on/click {:effect (ds-ui/dispatch :todo/archive-task
                                             {:task-id task-id})}}
 "Archive"]
```

Do not set `"command/name"` manually.

### Inline Edits

Inline edits should bind a draft signal, dispatch only meaningful changes, and
support Enter/Escape behavior:

```clojure
(ds-ui/with-signals [title-draft {:init title}]
  [:input {:class "inline-edit inline-edit-title"
           :required true
           :bind/value title-draft
           :on/blur
           {:effect
            (ds-ui/choose-effect
             (ds-ui/present? (ds-ui/trimmed title-draft))
             (ds-ui/when-effect
              (ds-ui/changed? (ds-ui/trimmed title-draft) title)
              (ds-ui/dispatch :todo/rename-task
                              {:task-id task-id
                               :title (ds-ui/trimmed title-draft)}))
             (ds-ui/set-signal title-draft title))}
           :on/keydown
           {:effect
            (ds-ui/on-keys
             {"Enter" (ds-ui/blur)
              "Escape" (ds-ui/effects
                        (ds-ui/set-signal title-draft title)
                        (ds-ui/blur))})}}])
```

### Select And Numeric Controls

Selects and numeric inputs should bind local signals and dispatch explicit
payloads. Use `ds-ui/num` for numeric command fields:

```clojure
[:input {:type "number"
         :bind/value due-draft
         :on/change
         {:effect
          (ds-ui/choose-effect
           (ds-ui/present? due-draft)
           (ds-ui/dispatch :todo/set-task-due-within
                           {:task-id task-id
                            :due-within-days (ds-ui/num due-draft)})
           (ds-ui/dispatch :todo/clear-task-due-within
                           {:task-id task-id}))}}]
```

### Links

Use route refs instead of literal app URLs:

```clojure
[:a {:href (ds-ui/href :todo/task-page
                       {:query-params {:task-id task-id}})}
 title]
```

### Errors

Use the adapter-owned `error` signal for top-level command failures:

```clojure
(defn action-error []
  [:div {:class "alert alert-error mb-4"
         :bind/show (ds-ui/js "$error")}
   [:span {:bind/text (ds-ui/js "$error")}]])
```

Field-level errors should read from `fieldErrors` when commands return field
error maps.

### Static Gallery Rendering

The dev gallery should render canonical specimens without active command events
or navigation. Use `ds-ui/static` on the checked IR:

```clojure
(defn static-specimen [node]
  (ds-ui/static (ds-ui/ir node) {:strip-href? true
                                 :strip-raw-events? true}))
```

Use this for gallery samples that reuse production components.

---

## Front-End Inventory

When changing UI, account for every front-end surface:

- App shell and error display in `ui.clj`
- Shared visual primitives in `ui/components.clj`
- Page composition in `todo_list_service/ui.clj`
- Todo controls and lists in `todo_list_service/ui/components.clj`
- Dev gallery and static specimens in `todo_list_service/ui/components.clj`
- Tailwind/DaisyUI source in `css/main.css`
- CSS build scripts in `package.json`

Production interactive flows to verify:

- quick add task
- create project
- complete, cancel, archive, and reactivate task
- complete, cancel, and reactivate project
- inline task title edit
- inline project name edit
- assign and remove a task project
- set and clear due-within days
- task and project detail navigation
- start weekly review
- mark task reviewed
- mark project reviewed
- complete weekly review
- action error display

Dev gallery flows to verify:

- `/dev/gallery` renders all specimens
- specimens are visually intact
- specimens are inert and do not submit commands
- static links are stripped from reused production components

---

## CSS

The CSS build is adapted to this project layout.

`package.json`:

```json
{
  "scripts": {
    "css:dev": "npx @tailwindcss/cli -i ./css/main.css -o ./resources/public/css/main.css --watch",
    "css:build": "npx @tailwindcss/cli -i ./css/main.css -o ./resources/public/css/main.css --minify"
  }
}
```

`css/main.css` scans Clojure source files:

```css
@source "../src/**/*.clj";
```

Run:

```sh
npm run css:dev
```

or:

```sh
npm run css:build
```

Generated CSS belongs at:

```text
resources/public/css/main.css
```

Run `npm run css:build` after adding or changing Tailwind classes or custom CSS.

---

## Avoid These Patterns

Do not use these patterns for new code:

- `obneyai/grain-datastar-v2`
- `ai.obney.grain.datastar_v2.interface`
- `ds/action-route`
- hand-built command submissions that set `"command/name"` manually
- helper functions named like `command-click`, `command-assignments`, `signal-ref`, or `data-signals`
- unescaped string interpolation into Datastar event JavaScript
- literal app hrefs such as `"/task?task-id=..."`
- raw `data-signals`, `data-bind`, or `data-on:*` for behavior the checked DSL supports
- raw `data-ignore-morph` when `:morph/ignore` can express the intent
- raw DOM property effects when `:bind/prop` can express the intent
- broad app-owned Datastar wrappers that hide the checked DSL
- `:datastar/fps` for normal Grain state pages (`:datastar/fps 0` on a truly
  static page is the exception — it is the only way to get a one-shot render)
- `ds-ui/defcomponent` as a default component style; ordinary pure functions are
  clearer in this teaching app

Raw `data-*` attributes, `ds-ui/action`, and `ds-ui/js` are migration or interop
escape hatches. Any production use should be small and easy to justify.

---

## Feature Checklist

For this project, add todo behavior inside the existing local service component.

1. Add or update payload schemas in `todo_list_service/schemas.clj`.
2. Add event type names, reducer logic, and read helpers in `todo_list_service/read_models.clj`.
3. Add command handlers in `todo_list_service/commands.clj`.
4. Add query handlers and query data assembly in `todo_list_service/queries.clj`.
5. Add pure page functions to `todo_list_service/ui.clj`.
6. Add or update read-model state schemas in `read-model-schemas` when projection shapes change.
7. Add or update reusable app-wide visual primitives in `ui/components.clj`.
8. Add or update todo-specific UI widgets in `todo_list_service/ui/components.clj`.
9. Make query handlers call page functions and lower with `ds-ui/hiccup`.
10. Use checked DSL behavior: `with-signals`, `:bind/*`, `:on/*`, `dispatch`, `refresh`, and `href`.
11. Use newer checked forms when they match the behavior:
   `:bind/prop`, `:morph/ignore`, element `:effect`, `:on/signal-patch`,
   `ds-ui/indexed`, `ds-ui/num-cents`, and stable signals.
12. Add route wiring in the root `::routes` only if Grain route helpers do not already provide it.
13. Run `npm run css:build` after adding new Tailwind classes.
14. Start the app from the REPL with `(def app (app/start))`.

Only add another service directory if the app grows a second domain with its own
schemas, commands, queries, read models, and UI.

---

## Verification

For behavior changes:

```sh
clojure -T:build test
```

For Clojure lint checks when available:

```sh
clj-kondo --lint src test
```

For UI or CSS changes:

```sh
npm run css:build
```

After a Datastar UI migration, grep for old patterns:

```sh
rg -n "grain-datastar-v2|datastar_v2|ds/action-route|command-click|command-assignments|data-signals|signal-ref|with-signal-scope|lower-effect|lower-expr|data-ignore-morph|@post\\(\\$__grainAction\\)|@post\\(\\$__grainStream\\)|:href \\\"/" src doc/pattern-compendium.md
```

Remaining doc matches should appear only in the avoid-pattern checklist or grep
command. Source matches should be justified, such as the static stylesheet link
in `datastar-head`.

Manual browser checks:

- `/`
- `/tasks`
- `/task?task-id=...`
- `/projects`
- `/project?project-id=...`
- `/review`
- `/dev/gallery`

Verify no overlapping controls or text, no unexpected layout shifts, and no
interactive gallery specimens.

---

## Debugging

Use the REPL and server logs. Do not guess at Grain behavior.

Start the app:

```clojure
(require '[cjbarre.grain-todo-list :as app])
(def system (app/start))
```

Stop it:

```clojure
(app/stop system)
```

Read raw events:

```clojure
(require '[ai.obney.grain.event-store-v3.interface :as es])
(into [] (es/read (:cjbarre.grain-todo-list/event-store system)
                  {:tenant-id app/tenant-id}))
```

If a page does not update after a command:

- confirm the command emitted an event
- confirm the event type is included in the read model
- confirm the query metadata includes the read model in `:grain/read-models`
- confirm `:query/result` changes when the underlying data changes
- confirm `:datastar/hiccup` is lowered through `ds-ui/hiccup`
- confirm the page is rendered by a `ds/routes` Datastar route

If a command appears to do nothing:

- check that `/actions` is wired with `ds/action-handler`
- inspect the lowered event attribute from `ds-ui/hiccup`
- confirm `ds-ui/dispatch` uses the registered command keyword
- confirm the dispatch payload keys match the command schema
- test the command directly in the REPL
- check authorization
- check that event maps contain the expected `:type`, `:tags`, and `:body`

If a link is wrong:

- confirm it uses `ds-ui/href`
- confirm the query keyword exists and declares `:datastar/path`
- confirm path or query params match the query schema

If a signal-carrying read does not re-render:

- confirm the interaction uses `ds-ui/refresh`
- confirm the target query declares `:datastar/path`
- confirm the query reads submitted params from request/query context
- confirm the stream route is generated by `ds/routes`

If page-level UI state must survive independent patches:

- prefer ordinary scoped `with-signals` first
- use `{:stable? true :name "..."}` only for intentional shared page state
- verify the stable signal is part of the page contract, not a component detail

If repeated row inputs edit one collection signal:

- bind row controls with `ds-ui/indexed`
- dispatch or refresh with the parent collection signal
- confirm row count changes replace the collection `:init` on re-render

If CSS is missing:

- run `npm run css:build`
- confirm the generated file exists at `resources/public/css/main.css`
- confirm classes are present in `src/**/*.clj` so Tailwind can discover them

---

## Translation Guide From Grain Examples

When reading Grain or Polylith examples, translate paths like this:

| Example path | This project |
| --- | --- |
| `bases/web-api/src/.../core.clj` | `src/cjbarre/grain_todo_list.clj` |
| `components/{service}/core/schemas.clj` | `src/cjbarre/grain_todo_list/service/todo_list_service/schemas.clj` |
| `components/{service}/core/commands.clj` | `src/cjbarre/grain_todo_list/service/todo_list_service/commands.clj` |
| `components/{service}/core/queries.clj` | `src/cjbarre/grain_todo_list/service/todo_list_service/queries.clj` |
| `components/{service}/core/read_models.clj` | `src/cjbarre/grain_todo_list/service/todo_list_service/read_models.clj` |
| `components/{service}/core/views.clj` | `src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj` |
| `components/{service}/interface.clj` | no app-local equivalent; require the local namespace directly |
| `bases/web-api/resources/.../public/css/main.css` | `resources/public/css/main.css` |

Keep the conceptual patterns. Drop the Polylith ceremony.
