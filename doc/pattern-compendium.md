# Pattern Compendium

This project is intentionally small. Build the app in a minimal Clojure layout rather than a Polylith layout, but keep the todo domain organized as one local service component.

The goal is to keep application code in a few obvious places:

- `src/cjbarre/grain_todo_list.clj` - application composition, Integrant system, routes, lifecycle
- `src/cjbarre/grain_todo_list/todo_list_service/schemas.clj` - schema constants, validation helpers, and `defschemas`
- `src/cjbarre/grain_todo_list/todo_list_service/read_models.clj` - read model reducers, `defreadmodel`s, and projection helpers
- `src/cjbarre/grain_todo_list/todo_list_service/commands.clj` - command helpers and `defcommand`s
- `src/cjbarre/grain_todo_list/todo_list_service/queries.clj` - query data assembly and `defquery`s
- `src/cjbarre/grain_todo_list/todo_list_service/ui.clj` - page-level todo UI composition
- `src/cjbarre/grain_todo_list/todo_list_service/todo_processors.clj` - todo processor lifecycle
- `src/cjbarre/grain_todo_list/todo_list_service/periodic_tasks.clj` - periodic trigger lifecycle
- `src/cjbarre/grain_todo_list/ui/components.clj` - reusable UI elements and Datastar v2 DSL usage

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

Service namespaces require only the Grain interfaces they use. UI components that emit Datastar attributes should require the same v2 interface:

```clojure
(ns cjbarre.grain-todo-list.ui.components
  (:require [ai.obney.grain.datastar_v2.interface :as ds]))
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

### Service Files

Use `src/cjbarre/grain_todo_list/todo_list_service/` for todo domain behavior:

- `schemas.clj` owns enum constants, validation helpers, and `defschemas`.
- `read_models.clj` owns event-type sets, reducer multimethods, `defreadmodel`s, and projection helper functions.
- `commands.clj` owns anomaly helpers, command helper functions, and `defcommand`s.
- `queries.clj` owns query data assembly and `defquery`s.
- `ui.clj` owns pure page-level todo Hiccup composition.
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
  (:require [cjbarre.grain-todo-list.ui.components :as c]))

(defn home-page
  [{:keys [tasks projects]}]
  [:div#app
   (c/app-shell {:title "Grain Todo"}
    (c/quick-add {})
    (c/task-list tasks projects))])
```

### Components UI File

Use `src/cjbarre/grain_todo_list/ui/components.clj` for reusable Hiccup components and Datastar v2 DSL calls:

- buttons, chips, fields, panels, lists, cards
- command forms
- command buttons
- inline edit controls
- stream repost controls for search, filters, and pagination

Prefer adapter-owned DSL helpers over local string builders. New code should not create app-owned Datastar helpers that duplicate `ds/signal`, `ds/assign`, `ds/signals`, `ds/bind`, `ds/action-form`, `ds/on-click-command`, or `ds/on-command`.

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
  "Capture a task."
  [{{:keys [title bucket]} :command :as ctx}]
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
                         :bucket bucket}})]
       :datastar/signals {:__toast "Task captured."}})))
```

Validation pattern:

- return an anomaly for invalid input
- return `:command-result/events` on success
- return `:datastar/signals` for app-level feedback such as `:__toast`

Do not return form field reset signals such as `:title ""` or `:projectName ""` from command handlers when the form is built with `ds/action-form`. Scoped form reset belongs to the adapter-owned form helper.

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
  [state {:keys [task-id title bucket]}]
  (assoc state task-id {:task-id task-id
                        :title title
                        :bucket bucket
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

## Datastar V2 DSL Patterns

Use `ai.obney.grain.datastar_v2.interface` as the shallow, adapter-owned Datastar layer. Raw Datastar attributes still work as an escape hatch, but normal app code should prefer the DSL.

### Literal Values And Raw Expressions

Plain Clojure values are escaped as JavaScript literals:

```clojure
(ds/assign :task-id task-id)
(ds/assign :bucket "next")
```

Use `ds/expr` only when the value is intentionally raw Datastar or JavaScript:

```clojure
(ds/assign :title (ds/expr "$evt.target.value"))
(ds/assign :order (ds/expr "$order + 1"))
```

Use `ds/signal` when custom JavaScript must reference a signal:

```clojure
(str "if (" (ds/signal :title) ".trim()) { el.blur(); }")
```

### Signal Attributes

Use `ds/signals` for explicit signal initialization:

```clojure
[:div (ds/signals {:title ""
                   :bucket "inbox"})]
```

Use `ds/bind` for two-way input binding:

```clojure
[:input
 (merge {:class "input input-bordered"
         :aria-label "Task title"
         :required true}
        (ds/bind :title))]
```

Use `ds/show` and `ds/text` for standard reactive display:

```clojure
[:div (merge {:class "alert alert-error"} (ds/show "$error"))
 [:span (ds/text "$error")]]
```

### Reads

Read interactions update browser signals and repost to the current stream. The v2 shim initializes `$__grainStream`, so view code should not hardcode a stream route.

Use these helpers for search, filters, pagination, tabs, and other signal-carrying reads:

```clojure
(ds/on-input-repost
  {:debounce-ms 300
   :assign {:search (ds/expr "$evt.target.value")}})

(ds/on-change-repost
  {:assign {:bucket (ds/expr "$evt.target.value")}})

(ds/on-click-repost
  {:assign {:page (ds/expr "$page + 1")}})
```

These helpers emit `@post($__grainStream)` and preserve the current SSE stream reuse behavior.

### Command Buttons

Use `ds/on-click-command` for button-like writes:

```clojure
(defn complete-action
  [{:keys [task-id]}]
  [:button
   (merge {:class "status-action"
           :type "button"
           :aria-label "Complete task"}
          (ds/on-click-command :todo/complete-task
            {:extra {:task-id task-id}}))
   "Complete"])
```

`on-click-command` sets `command/name`, applies `:extra` signal assignments, and posts to `$__grainAction`.

### Conditional Command Writes

Use `ds/on-command` when a browser event needs to submit a command only under a condition, derive a value before submit, or choose between two commands. Do not build these flows by concatenating `ds/assign-all` with raw `@post($__grainAction)`.

For inline text edits, keep the raw JavaScript to the minimum expression positions owned by the DSL:

```clojure
(defn task-title-edit
  [{:keys [task-id title title-signal]}]
  [:input
   (merge {:class "inline-edit-title"
           :aria-label "Task title"
           :type "text"}
          (ds/bind title-signal)
          (ds/on-command
            :blur
            {:let [['next (ds/expr (str (ds/signal title-signal) ".trim()"))]]
             :when (ds/expr (str "next && next !== " (ds/lit title)))
             :command :todo/rename-task
             :extra {:task-id task-id
                     :title (ds/expr "next")}}))])
```

For select controls that either set or clear a relationship, use the branch form:

```clojure
(ds/on-command
  :change
  (cond-> {:when (ds/expr (ds/signal project-signal))
           :then {:command :todo/assign-task-to-project
                  :extra {:task-id task-id
                          :project-id (ds/expr (ds/signal project-signal))}}}
    project-id
    (assoc :else {:command :todo/remove-task-from-project
                  :extra {:task-id task-id}})))
```

For date inputs, derive the ISO value with `:let` and branch between set and clear commands:

```clojure
(ds/on-command
  :change
  (cond-> {:let [['selected (ds/expr (ds/signal due-signal))]
                 ['dueAt (ds/expr "new Date(selected + 'T00:00:00').toISOString()")]]
           :when (ds/expr "selected")
           :then {:command :todo/set-task-due-at
                  :extra {:task-id task-id
                          :due-at (ds/expr "dueAt")}}}
    due-at
    (assoc :else {:command :todo/clear-task-due-at
                  :extra {:task-id task-id}})))
```

`on-command` posts through `$__grainAction`, sets `command/name`, and preserves event modifiers such as `:prevent?`, `:stop?`, and `:debounce-ms`. `:when` and `:let` values must be `ds/expr` because they are JavaScript contexts. `:extra` values are escaped as literals unless wrapped in `ds/expr`.

### Command Forms

Use `ds/action-form` for command forms. The helper owns Datastar behavior; the caller owns visual markup.

```clojure
(defn quick-add
  [{:keys [bucket project-id]}]
  (ds/action-form
    {:command :todo/capture-task
     :fields (cond-> {:title ""
                      :bucket (name (or bucket :inbox))}
               project-id (assoc :project-id project-id))}
    [:input
     (merge {:class "input input-bordered min-w-0 flex-1"
             :aria-label "Task title"
             :placeholder "Capture a task"
             :required true}
            (ds/bind :title))]
    [:select
     (merge {:class "select select-bordered sm:w-40"
             :aria-label "Bucket"}
            (ds/bind :bucket))
     (for [[value label] bucket-labels]
       [:option {:key value :value (name value)} label])]
    [:button {:type "submit" :class "btn btn-primary"} "Add"]))
```

`action-form` initializes signals, sets `command/name`, clears `error` and `fieldErrors`, and posts to `$__grainAction`.

### Scoped Forms

Use `ds/with-scope` when multiple forms on the same page share field names:

```clojure
(ds/with-scope (str "project-" project-id)
  (ds/action-form
    {:command :todo/rename-project
     :fields {:project-id project-id
              :name name}}
    [:input
     (merge {:class "inline-edit"
             :aria-label "Project name"}
            (ds/bind :name))]))
```

Inside the scope, `ds/bind` creates scoped browser signals. On submit, `action-form` copies scoped field values into unscoped command fields before posting, then restores previous unscoped values so sibling forms are not clobbered.

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
  [:div#app
   [:main {:class "min-h-screen bg-base-100"}
    [:div {:class "mx-auto max-w-4xl px-4 py-8"}
     [:header {:class "mb-6 flex items-center justify-between gap-4"}
      [:h1 {:class "text-2xl font-semibold"} "Tasks"]]
     (c/quick-add {})
     (if (seq tasks)
       (c/task-list tasks projects)
       (c/empty-state "No tasks yet."))]]])
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
   [:input
    (merge {:type (or type "text")
            :required required?
            :class "input input-bordered w-full"}
           (ds/bind signal-name))]])
```

### Error Display

Use the adapter-owned `error` signal for top-level command failures:

```clojure
(defn action-error []
  [:div (merge {:class "alert alert-error mb-4"} (ds/show "$error"))
   [:span (ds/text "$error")]])
```

Field-level errors should read from `fieldErrors` when commands return field error maps.

### Lists

Keep list rendering boring and durable:

```clojure
(defn task-list [tasks projects]
  [:ul {:class "divide-y divide-base-300 rounded-box border border-base-300"}
   (for [{:keys [task-id title status]} tasks]
     [:li {:key task-id
           :class "flex items-center justify-between gap-4 p-3"}
      [:span {:class (when (= status :completed) "line-through opacity-60")} title]
      [:button
       (merge {:class "btn btn-ghost btn-sm"
               :type "button"}
              (ds/on-click-command :todo/complete-task
                {:extra {:task-id task-id}}))
       "Done"]])])
```

---

## Avoid These Old Patterns

Do not use these patterns for new code:

- hand-built command submissions that post directly to `/actions`
- hand-built conditional command submissions that concatenate `if (...)`, `ds/assign-all`, and `@post($__grainAction)` instead of using `ds/on-command`
- app-owned helpers such as `command-click`, `command-expr`, `ds-str`, `ds-assign`, or `signal-ref`
- raw `data-signals` strings when `ds/signals` or `ds/action-form` can own signal initialization
- old kebabless event attrs such as `:data-on-click` when the DSL can emit attrs
- `:datastar/fps` query metadata in Datastar v2 app guidance
- raw signal reference strings when `ds/signal` can build the reference safely

Only keep raw Datastar strings when the DSL does not yet cover a needed behavior. Conditional command submissions are covered by `ds/on-command`; raw JavaScript inside them should be limited to explicit `ds/expr` conditions and derived values.

---

## New Feature Checklist

For this project, add todo behavior inside the existing local service component.

1. Add or update payload schemas in `todo_list_service/schemas.clj`.
2. Add event type names, reducer logic, and read helpers in `todo_list_service/read_models.clj`.
3. Add command handlers in `todo_list_service/commands.clj`.
4. Add query handlers and query data assembly in `todo_list_service/queries.clj`.
5. Add pure page functions to `todo_list_service/ui.clj`.
6. Add or update reusable UI elements in `src/cjbarre/grain_todo_list/ui/components.clj`.
7. Make the query call the service UI function.
8. Use `ds/action-form`, `ds/on-click-command`, `ds/on-command`, or stream repost helpers for Datastar behavior.
9. Add route wiring in the root `::routes` only if the Grain route helpers do not already provide it.
10. Run `npm run css:build` after adding new Tailwind classes.
11. Start the app from the REPL with `(def app (app/start))`.

Only add another service directory if the app grows a second domain with its own schemas, commands, queries, read models, and UI.

---

## Migration Checklist To Datastar V2

Use this checklist when migrating this app from the deprecated adapter patterns to v2:

1. Depend on `obneyai/grain-datastar-v2`.
2. Change backend and UI component requires to `ai.obney.grain.datastar_v2.interface`.
3. Use `ds/action-route` for the standard command endpoint.
4. Replace manual command string helpers with `ds/on-click-command`, `ds/on-command`, and `ds/action-form`.
5. Convert quick-add and project-add forms to `ds/action-form`.
6. Convert status buttons, chips, review actions, and primary actions to `ds/on-click-command`.
7. Convert inline rename, select assign/clear, and date set/clear flows to `ds/on-command`.
8. Convert hand-built `data-signals` strings to `ds/signals` or `ds/action-form`.
9. Use `ds/on-input-repost`, `ds/on-change-repost`, and `ds/on-click-repost` for signal-carrying read interactions.
10. Remove `:datastar/fps` from query examples and app guidance.
11. Run the test suite.
12. Grep for old patterns:

```sh
rg -n ":datastar/fps|@post\\('/actions'\\)|@post\\(\\$__grainAction\\)|data-on-submit__prevent|data-on-click|command-click|command-expr|ds-str|ds-assign|signal-ref" src doc
```

Any remaining matches should be either removed or clearly justified as an escape hatch. In normal UI component code, `@post($__grainAction)` should appear only in adapter output, tests that assert generated output, or rare JavaScript that the DSL does not yet cover.

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

- confirm the control uses `ds/on-input-repost`, `ds/on-change-repost`, `ds/on-click-repost`, or `ds/repost-stream`
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
