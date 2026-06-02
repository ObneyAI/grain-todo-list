# Pattern Compendium

This project is intentionally small. Build the app in a minimal Clojure layout rather than a Polylith layout, but keep the todo domain organized as one local service component.

The goal is to keep application code in a few obvious places:

- `src/cjbarre/grain_todo_list.clj` - application composition, Integrant system, routes, lifecycle
- `src/cjbarre/grain_todo_list/ui.clj` - application shell and app-level UI chrome
- `src/cjbarre/grain_todo_list/ui/components.clj` - reusable app-wide Hiccup UI primitives
- `src/cjbarre/grain_todo_list/todo_list_service/schemas.clj` - schema constants, validation helpers, and primitive-specific `defschemas`
- `src/cjbarre/grain_todo_list/todo_list_service/read_models.clj` - read model reducers, `defreadmodel`s, and projection helpers
- `src/cjbarre/grain_todo_list/todo_list_service/commands.clj` - command helpers and `defcommand`s
- `src/cjbarre/grain_todo_list/todo_list_service/queries.clj` - query data assembly and `defquery`s
- `src/cjbarre/grain_todo_list/todo_list_service/ui.clj` - page-level todo UI composition
- `src/cjbarre/grain_todo_list/todo_list_service/ui/components.clj` - todo-specific UI controls, cards, lists, and gallery specimens
- `src/cjbarre/grain_todo_list/todo_list_service/todo_processors.clj` - todo processor lifecycle
- `src/cjbarre/grain_todo_list/todo_list_service/periodic_tasks.clj` - periodic trigger lifecycle

Supporting files:

- `css/main.css` - Tailwind and DaisyUI input CSS
- `resources/public/css/main.css` - generated CSS output
- `deps.edn` - Clojure classpath and dependencies
- `package.json` - CSS build scripts

No `bases/`, Polylith `components/`, `interface.clj`, or service bricks in this app. If a pattern from Grain examples mentions those directories, translate it into the local `todo_list_service` namespaces above.

---

## Architecture

This is a compact Grain app with server-rendered reactive UI.

Stack:

- Clojure
- Integrant
- Grain event store, command routes, query routes, todo processors, periodic tasks
- Datastar v2 server-rendered UI through Grain query routes
- Tailwind CSS and DaisyUI

Use the current Grain Datastar v2 adapter:

```clojure
obneyai/grain-datastar-v2
{:git/url "https://github.com/ObneyAI/grain.git"
 :sha "..."
 :deps/root "projects/grain-datastar-v2"}
```

Application namespace pattern:

```clojure
(ns cjbarre.grain-todo-list
  (:require [integrant.core :as ig]
            [com.brunobonacci.mulog :as u]
            [clojure.set :as set]
            [ai.obney.grain.command-processor-v2.interface :as cp :refer [defcommand]]
            [ai.obney.grain.query-processor.interface :as query-processor :refer [defquery]]
            [ai.obney.grain.datastar_v2.interface :as ds]
            [ai.obney.grain.event-store-v3.interface :as es :refer [->event]]
            [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]
            [ai.obney.grain.schema-util.interface :refer [defschemas]]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.periodic-task.interface :as pt]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.webserver.interface :as ws]
            [cjbarre.grain-todo-list.todo-list-service.commands]
            [cjbarre.grain-todo-list.todo-list-service.periodic-tasks :as periodic-tasks]
            [cjbarre.grain-todo-list.todo-list-service.queries]
            [cjbarre.grain-todo-list.todo-list-service.read-models]
            [cjbarre.grain-todo-list.todo-list-service.schemas]
            [cjbarre.grain-todo-list.todo-list-service.todo-processors :as todo-processors]))
```

Service namespaces require only the Grain interfaces they use. UI components that emit Datastar attributes should not require the Datastar v2 interface unless they need server-side helper functions. Use plain Hiccup attributes and app-local JSON/JavaScript literal helpers instead:

```clojure
(ns cjbarre.grain-todo-list.ui.components
  (:require [clojure.data.json :as json]))
```

---

## File Responsibilities

### App File

Use `src/cjbarre/grain_todo_list.clj` only for app composition:

- Integrant system map
- app-level lifecycle functions: `start`, `stop`
- event store, pubsub, cache, context, route, and webserver keys
- route assembly
- delegation to service processor and periodic-task lifecycle functions
- requiring service namespaces so schemas, read models, commands, and queries register

Do not put todo domain logic, command handlers, query handlers, schemas, read models, or page rendering in the root app namespace.

### App UI File

Use `src/cjbarre/grain_todo_list/ui.clj` for application-level UI chrome:

- `app-shell` and shared page container structure
- app-level error display
- product-level labels, title composition, and page chrome

This namespace should not alias todo service pages. Future service components should be able to use the same shell without depending on todo-specific widgets.

### Service Files

Use `src/cjbarre/grain_todo_list/todo_list_service/` for todo domain behavior:

- `schemas.clj` owns enum constants, validation helpers, and separate `defschemas` forms for common types, events, commands, queries, and read models.
- `read_models.clj` owns event-type sets, reducer multimethods, `defreadmodel`s, and projection helper functions.
- `commands.clj` owns anomaly helpers, command helper functions, and `defcommand`s.
- `queries.clj` owns query data assembly and `defquery`s.
- `ui.clj` owns pure page-level todo Hiccup composition.
- `ui/components.clj` owns todo-specific controls, task/project/review widgets, and todo gallery specimens.
- `todo_processors.clj` owns todo processor start/stop functions.
- `periodic_tasks.clj` owns periodic trigger start/stop functions.

### Service UI File

Use `src/cjbarre/grain_todo_list/todo_list_service/ui.clj` for page-level todo UI concerns:

- pure Hiccup page functions
- page composition from reusable UI elements
- route-specific page shells
- light formatting that is only used by page composition

UI functions should be pure. They receive data and return Hiccup. They should not read from the event store, mutate state, run commands, or access Integrant components.

Example:

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.ui
  (:require [cjbarre.grain-todo-list.todo-list-service.ui.components :as tc]
            [cjbarre.grain-todo-list.ui :as app-ui]
            [cjbarre.grain-todo-list.ui.components :as c]))

(defn home-page
  [{:keys [tasks projects]}]
  (app-ui/app-shell {:title "Grain Todo"}
    (tc/quick-add {})
    (tc/task-list tasks "No tasks yet." projects)))
```

### App Components UI File

Use `src/cjbarre/grain_todo_list/ui/components.clj` for reusable app-wide Hiccup primitives and small value-encoding helpers:

- buttons, chips, fields, panels, lists, cards, and surfaces
- shell-neutral field and action primitives
- generic command assignment helpers when repeated JavaScript must be generated safely
- JSON and JavaScript literal helpers for `data-signals` and command payloads

Datastar UI behavior is plain Hiccup attributes. Keep helpers shallow: they may encode values or assemble repeated assignments, but they should not hide the fact that the markup uses `data-bind`, `data-signals`, `data-on:*`, `@post($__grainStream)`, and `@post($__grainAction)`.

### Service Components UI File

Use `src/cjbarre/grain_todo_list/todo_list_service/ui/components.clj` for todo-specific UI:

- add-task and project-add forms
- task/project cards, summaries, detail panels, and action controls
- due-within controls, planning summaries, and task/project review controls
- todo dev gallery fixtures and specimens

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

Because `deps.edn` includes `resources` on the classpath, files under `resources/public` are the app's static public assets.

---

## Backend Patterns

### Integrant System

The system map lives directly in `cjbarre.grain-todo-list/system`.

Keep additions local and explicit:

```clojure
(def system
  {::logger {}
   ::event-store {:conn {:type :in-memory}}
   ::cache {}
   ::context {:event-store (ig/ref ::event-store)
              :cache (ig/ref ::cache)
              :tenant-id tenant-id}
   ::processors {:event-store (ig/ref ::event-store)
                 :cache (ig/ref ::cache)
                 :tenant-id tenant-id}
   ::periodic-triggers {:event-store (ig/ref ::event-store)
                        :tenant-id tenant-id}
   ::routes {:context (ig/ref ::context)}
   ::webserver {:http/routes (ig/ref ::routes)
                :http/port 8080
                :http/join? false}})
```

Prefer adding app-level wiring keys to this map. Put todo domain behavior in `todo-list-service` namespaces and call it from Integrant methods.

### Routes

Routes are assembled in the `::routes` Integrant key.

Use `ds/routes` for Datastar query pages and `ds/action-route` for the standard command endpoint:

```clojure
(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   (ds/routes context
              {}
              {:datastar/shim-opts {:head datastar-head
                                     :html-attrs {:data-theme "workshop"}}})
   #{(ds/action-route context {})
     ["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]
     ["/favicon.ico" :get [(fn [_] {:status 204 :body ""})] :route-name ::favicon]}))
```

`ds/action-route` defaults to `POST /actions` and delegates to the v2 action handler. Prefer it over manually constructing the `/actions` route.

### Command Handlers

Commands live in `cjbarre.grain-todo-list.todo-list-service.commands`.

Use Grain command definitions from the command processor namespace. Keep each command small:

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [cjbarre.grain-todo-list.todo-list-service.read-models :as rm]
            [cognitect.anomalies :as anom]))

(defcommand :todo capture-task
  {:authorized? (constantly true)}
  "Add a task."
  [{{:keys [title]} :command :as ctx}]
  (cond
    (not (seq title))
    {::anom/category ::anom/incorrect
     ::anom/message "Title is required."}

    :else
    (let [task-id (random-uuid)]
      {:command-result/events
       [(->event {:type :todo/task-captured
                  :tags #{[:todo-task task-id]}
                  :body {:task-id task-id
                         :title title
                         :status :active}})]
       :datastar/signals {:__toast "Task added."}})))
```

Validation pattern:

- return an anomaly for invalid input
- return `:command-result/events` on success
- return `:datastar/signals` for app-level feedback such as `:__toast`

Do not return form field reset signals such as `:title ""` or `:projectName ""` from command handlers. Field reset is browser UI behavior and belongs in the form's Datastar attributes when the UI needs it.

Use event tags consistently:

```clojure
:tags #{[:todo-task task-id]}
```

### Read Models

Read models and read helpers live in `cjbarre.grain-todo-list.todo-list-service.read-models`.

Pattern:

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

(def todo-event-types
  #{:todo/task-captured
    :todo/task-completed
    :todo/task-deleted})

(defmulti tasks* (fn [_state event] (:event/type event)))

(defmethod tasks* :todo/task-captured
  [state {:keys [task-id title]}]
  (assoc state task-id {:task-id task-id
                        :title title
                        :status :active}))

(defmethod tasks* :todo/task-completed
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :completed))

(defmethod tasks* :todo/task-deleted
  [state {:keys [task-id]}]
  (dissoc state task-id))

(defmethod tasks* :default [state _event]
  state)

(defreadmodel :todo tasks
  {:events todo-event-types
   :version 1}
  [state event]
  (tasks* state event))

(defn get-tasks [ctx]
  (->> (rmp/project ctx :todo/tasks)
       vals
       (sort-by :title)))
```

Increment `:version` when reducer behavior changes in a way that should rebuild cached projections.

### Query Handlers

Queries live in `cjbarre.grain-todo-list.todo-list-service.queries` and call page functions from `cjbarre.grain-todo-list.todo-list-service.ui`.

Static page:

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.queries
  (:require [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.todo-list-service.ui :as ui]))

(defquery :todo dev-gallery-page
  {:authorized? (constantly true)
   :datastar/path "/dev/gallery"
   :datastar/title "Dev UI Gallery"}
  "Render the dev gallery once."
  [_ctx]
  {:query/result {}
   :datastar/hiccup (ui/dev-gallery-page)})
```

Event-driven page:

```clojure
(defquery :todo tasks-page
  {:authorized? (constantly true)
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1
                       :todo/projects 1}
   :datastar/debounce-ms 50}
  "Render tasks and update when todo events fire."
  [ctx]
  (let [tasks (get-tasks ctx)
        projects (active-projects ctx)]
    {:query/result {:tasks tasks
                    :projects projects}
     :datastar/hiccup (ui/tasks-page {:tasks tasks
                                      :projects projects})}))
```

Choose query behavior by metadata:

| Behavior | Metadata | Use for |
| --- | --- | --- |
| Static one-shot render | no `:grain/read-models` | static pages and galleries |
| Event-driven render | `:grain/read-models {...}` | app state stored through events |

For Datastar v2 app guidance, do not use `:datastar/fps`. Use event-driven reads for Grain state, and use explicit stream repost helpers for signal-carrying reads such as search, filters, pagination, and typeahead.

### Todo Processors

Event-driven side effects and tenant-poller lifecycle live in `cjbarre.grain-todo-list.todo-list-service.todo-processors`.

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :as tp]))

(defn start
  [{:keys [event-store cache tenant-id]}]
  (tp/start-tenant-poller
   {:event-store event-store
    :tenant-ids #{tenant-id}
    :context {:cache cache}
    :poll-interval-ms 250}))

(defn stop
  [poller]
  (tp/stop-tenant-poller poller))
```

Put processor handler definitions in this namespace as they are added. Use var references (`#'send-follow-up`) for handler functions so REPL reloading updates behavior without rebuilding the whole system.

### Periodic Tasks

Scheduled trigger lifecycle lives in `cjbarre.grain-todo-list.todo-list-service.periodic-tasks`.

```clojure
(ns cjbarre.grain-todo-list.todo-list-service.periodic-tasks
  (:require [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.periodic-task.interface :as pt]))

(defn start
  [{:keys [event-store tenant-id]}]
  (pt/start-periodic-triggers!
   {:append-fn (partial es/append event-store)
    :tenant-ids-fn (constantly #{tenant-id})}))

(defn stop
  [triggers]
  (pt/stop-periodic-triggers! triggers))
```

Keep periodic tasks idempotent. If a task can run twice, use event-store CAS or command-level validation to avoid duplicate effects.

---

## Datastar V2 Markup Patterns

Use `ai.obney.grain.datastar_v2.interface` for server integration: `ds/routes`, `ds/action-route`, custom stream/action interceptors, and SSE patch helpers. The adapter no longer provides UI DSL helpers. Application UI should author plain Datastar attributes in Hiccup.

### Signal Attributes

Initialize signals with JSON `data-signals`, bind inputs with `data-bind`, and display reactive values with Datastar attributes:

```clojure
[:div {:data-signals (json/write-str {"title" ""
                                      "due-within-days" ""})}
 [:input {:class "input input-bordered"
          :aria-label "Task title"
          :required true
          :data-bind "title"}]
 [:div {:class "alert alert-error"
        :data-show "$error"}
  [:span {:data-text "$error"}]]]
```

Keep browser signal names explicit. Namespaced command keys use slash strings in JSON and JavaScript, such as `"command/name"`.

### Reads

Read interactions update browser signals and repost to the current stream. The v2 shim initializes `$__grainStream`, so view code should not hardcode a stream route.

```clojure
[:input {:data-bind "search"
         :data-on:input__debounce.300ms "@post($__grainStream)"}]

[:select {:data-bind "tab"
          :data-on:change "@post($__grainStream)"}
 [:option {:value "active"} "Active"]
 [:option {:value "archived"} "Archived"]]

[:button {:data-on:click "$page = $page + 1; @post($__grainStream)"}
 "Next"]
```

Prefer `@post($__grainStream)` for live filters, search, pagination, typeahead, and other signal-carrying reads. Avoid raw `@get` for signal-bearing interactions because Datastar serializes current signals into the URL.

### Command Buttons

Commands post to the shim-owned `$__grainAction` endpoint. Set `"command/name"` and command fields before posting:

```clojure
(defn complete-action
  [{:keys [task-id]}]
  [:button {:class "status-action"
            :type "button"
            :aria-label "Complete task"
            :data-on:click
            (str "$['command/name'] = 'todo/complete-task'; "
                 "$['task-id'] = '" task-id "'; "
                 "@post($__grainAction)")}
   "Complete"])
```

Use JSON/JavaScript literal encoding helpers for dynamic strings, UUIDs, and namespaced keys. Do not interpolate unescaped user text into `data-on:*` JavaScript.

### Conditional Command Writes

Inline edits and set/clear controls use plain Datastar JavaScript. Keep conditions compact and let command schemas validate the submitted payload.

```clojure
[:input {:class "inline-edit-title"
         :aria-label "Task title"
         :type "text"
         :data-bind title-signal
         :data-on:blur
         (str "const next = $[" (json/write-str title-signal) "].trim(); "
              "if (next && next !== " (json/write-str title) ") { "
              "$['command/name'] = 'todo/rename-task'; "
              "$['task-id'] = " (json/write-str (str task-id)) "; "
              "$['title'] = next; "
              "@post($__grainAction); }")}]
```

```clojure
[:select {:data-bind project-signal
          :data-on:change
          (str "if ($[" (json/write-str project-signal) "]) { "
               "$['command/name'] = 'todo/assign-task-to-project'; "
               "$['task-id'] = " (json/write-str (str task-id)) "; "
               "$['project-id'] = $[" (json/write-str project-signal) "]; "
               "@post($__grainAction); } else { "
               "$['command/name'] = 'todo/remove-task-from-project'; "
               "$['task-id'] = " (json/write-str (str task-id)) "; "
               "@post($__grainAction); }")}]
```

For numeric fields, coerce in JavaScript before posting so Malli receives a JSON number:

```clojure
[:input {:type "number"
         :data-bind due-signal
         :data-on:change
         (str "const raw = $[" (json/write-str due-signal) "]; "
              "if (raw !== '' && raw !== null) { "
              "$['command/name'] = 'todo/set-task-due-within'; "
              "$['task-id'] = " (json/write-str (str task-id)) "; "
              "$['due-within-days'] = Number(raw); "
              "@post($__grainAction); }")}]
```

### Command Forms

Forms declare command signals and initial field signals on the form or an ancestor, bind inputs to those signals, and submit with `data-on:submit__prevent`.

```clojure
(defn quick-add
  [{:keys [project-id]}]
  (let [title-signal (str "quick_add_" (or project-id "global") "_title")]
    [:form {:data-signals (json/write-str {title-signal ""})
            :data-on:submit__prevent
            (str "$['command/name'] = 'todo/capture-task'; "
                 "$['title'] = $[" (json/write-str title-signal) "]; "
                 (when project-id
                   (str "$['project-id'] = " (json/write-str (str project-id)) "; "))
                 "@post($__grainAction); "
                 "$[" (json/write-str title-signal) "] = '';")}
     [:input {:class "input input-bordered min-w-0 flex-1"
              :aria-label "Task title"
              :placeholder "Add a task"
              :required true
              :data-bind title-signal}]
     [:button {:type "submit" :class "btn btn-primary"} "Add"]]))
```

Because command signals are ambient browser state, forms should set `"command/name"` on submit and use unique input signal names when several forms with the same field names can share a page. Command handlers should still validate duplicate or stale submissions defensively.

### Reserved Shim Signals

Generated Datastar v2 shim pages initialize these adapter-owned signals:

| Signal | Purpose |
| --- | --- |
| `__grainStream` | Current page stream endpoint, including resolved path params and nonce |
| `__grainAction` | Command endpoint, defaulting to `/actions` |
| `dsNonce` | Page-load nonce used for stream reuse and tab isolation |
| `error` | Standard command/query error signal |
| `fieldErrors` | Standard field error map signal |

Application code may read `error` and `fieldErrors` for display. Do not overwrite `__grainStream`, `__grainAction`, or `dsNonce`.

---

## UI Patterns

### Page Shape

Every Datastar page should return a `div#app` root so the server can patch the page consistently.

```clojure
(defn tasks-page
  [{:keys [tasks projects]}]
  (app-ui/app-shell {:title "Tasks"}
    (tc/quick-add {})
    (if (seq tasks)
      (tc/task-list tasks "No tasks yet." projects)
      (c/empty-state "No tasks yet."))))
```

### Small Components

Prefer small component functions over a broad component library:

```clojure
(defn empty-state [message]
  [:div {:class "rounded-box border border-base-300 p-8 text-center text-base-content/60"}
   message])

(defn field
  [{:keys [label signal-name type required?]}]
  [:label {:class "form-control w-full"}
   [:div {:class "label"} [:span {:class "label-text"} label]]
   [:input {:type (or type "text")
            :required required?
            :class "input input-bordered w-full"
            :data-bind signal-name}]])
```

### Error Display

Use the adapter-owned `error` signal for top-level command failures. The app-level implementation lives in `cjbarre.grain-todo-list.ui/action-error`:

```clojure
(defn action-error []
  [:div {:class "alert alert-error mb-4"
         :data-show "$error"}
   [:span {:data-text "$error"}]])
```

Field-level errors should read from `fieldErrors` when commands return field error maps.

### Lists

Keep list rendering boring and durable. Todo-specific lists belong in the todo service component namespace:

```clojure
(defn task-list [tasks projects]
  [:ul {:class "divide-y divide-base-300 rounded-box border border-base-300"}
   (for [{:keys [task-id title status]} tasks]
     [:li {:key task-id
           :class "flex items-center justify-between gap-4 p-3"}
      [:span {:class (when (= status :completed) "line-through opacity-60")} title]
      [:button {:class "btn btn-ghost btn-sm"
                :type "button"
                :data-on:click
                (str "$['command/name'] = 'todo/complete-task'; "
                     "$['task-id'] = '" task-id "'; "
                     "@post($__grainAction)")}
       "Done"]])])
```

---

## Avoid These Old Patterns

Do not use these patterns for new code:

- hand-built command submissions that post directly to `/actions`
- unescaped string interpolation of user text into `data-on:*` JavaScript
- broad app-owned Datastar wrappers that hide plain `data-*` attributes
- raw hand-written JSON strings for `data-signals` when `clojure.data.json/write-str` can encode them
- old kebabless event attrs such as `:data-on-click`
- `:datastar/fps` query metadata in Datastar v2 app guidance

Plain Datastar attributes are expected. Keep local helpers limited to value/key encoding and repeated command assignment snippets.

---

## New Feature Checklist

For this project, add todo behavior inside the existing local service component.

1. Add or update payload schemas in `todo_list_service/schemas.clj`.
2. Add event type names, reducer logic, and read helpers in `todo_list_service/read_models.clj`.
3. Add command handlers in `todo_list_service/commands.clj`.
4. Add query handlers and query data assembly in `todo_list_service/queries.clj`.
5. Add pure page functions to `todo_list_service/ui.clj`.
6. Add or update read-model state schemas in `read-model-schemas` when projection shapes change.
7. Add or update reusable app-wide UI primitives in `src/cjbarre/grain_todo_list/ui/components.clj`.
8. Add or update todo-specific UI widgets in `todo_list_service/ui/components.clj`.
9. Make the query call the service UI function.
10. Use plain Datastar attributes for UI behavior: `data-bind`, `data-signals`, `data-on:*`, `@post($__grainAction)`, and `@post($__grainStream)`.
11. Add route wiring in the root `::routes` only if the Grain route helpers do not already provide it.
12. Run `npm run css:build` after adding new Tailwind classes.
13. Start the app from the REPL with `(def app (app/start))`.

Only add another service directory if the app grows a second domain with its own schemas, commands, queries, read models, and UI.

---

## Migration Checklist To Datastar V2

Use this checklist when migrating this app from the deprecated adapter patterns to v2:

1. Depend on `obneyai/grain-datastar-v2`.
2. Keep backend route generation on `ai.obney.grain.datastar_v2.interface`.
3. Use `ds/action-route` for the standard command endpoint.
4. Remove UI component requires of `ai.obney.grain.datastar_v2.interface`; the latest adapter exports server helpers, not UI DSL helpers.
5. Convert quick-add and project-add forms to `data-signals`, `data-bind`, and `data-on:submit__prevent`.
6. Convert status buttons, chips, review actions, and primary actions to `data-on:click` command assignments plus `@post($__grainAction)`.
7. Convert inline rename, select assign/clear, and date set/clear flows to plain `data-on:blur` or `data-on:change` JavaScript.
8. Encode `data-signals` with `clojure.data.json/write-str`.
9. Use `@post($__grainStream)` for signal-carrying read interactions.
10. Remove `:datastar/fps` from query examples and app guidance.
11. Run the test suite.
12. Grep for old patterns:

```sh
rg -n ":datastar/fps|@post\\('/actions'\\)|data-on-submit__prevent|data-on-click|ds/(action-form|bind|signals|on-command|on-click-command|show|text|expr|lit|assign|with-scope)" src doc
```

Any remaining matches should be either removed or clearly justified. Normal UI component code may contain `@post($__grainAction)` and `@post($__grainStream)` because those are the current plain Datastar integration points.

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
- confirm the page is rendered by a Datastar v2 route

If a command appears to do nothing:

- test the command directly in the REPL
- check authorization
- check that command fields match submitted signal names
- check that `ds/action-route` is present in `::routes`
- check that command buttons/forms post through `$__grainAction`
- check that event maps contain the expected `:type`, `:tags`, and `:body`

If a signal-carrying read does not re-render:

- confirm the control posts to `$__grainStream`
- confirm the query reads the submitted signal from request/query context
- confirm the stream route is generated by `ds/routes`
- confirm the shim-owned `$__grainStream` signal is present

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
| `components/{service}/core/schemas.clj` | `src/cjbarre/grain_todo_list/todo_list_service/schemas.clj` |
| `components/{service}/core/commands.clj` | `src/cjbarre/grain_todo_list/todo_list_service/commands.clj` |
| `components/{service}/core/queries.clj` | `src/cjbarre/grain_todo_list/todo_list_service/queries.clj` |
| `components/{service}/core/read_models.clj` | `src/cjbarre/grain_todo_list/todo_list_service/read_models.clj` |
| `components/{service}/core/views.clj` | `src/cjbarre/grain_todo_list/todo_list_service/ui.clj` |
| `components/{service}/interface.clj` | no app-local equivalent; require the local namespace directly |
| `bases/web-api/resources/.../public/css/main.css` | `resources/public/css/main.css` |

Keep the conceptual patterns. Drop the Polylith ceremony.
