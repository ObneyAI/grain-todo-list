# Pattern Compendium

This project is intentionally small. Build the app in a minimal Clojure layout rather than a Polylith layout.

The goal is to keep nearly all application code in two files:

- `src/cjbarre/grain_todo_list.clj` - backend/system/domain file
- `src/cjbarre/grain_todo_list/ui.clj` - UI/Hiccup file

Supporting files:

- `css/main.css` - Tailwind and DaisyUI input CSS
- `resources/public/css/main.css` - generated CSS output
- `deps.edn` - Clojure classpath and dependencies
- `package.json` - CSS build scripts

No `bases/`, `components/`, `interface.clj`, or service bricks. If a pattern from Grain examples mentions those directories, translate it into the two-file structure above.

---

## Architecture

This is a compact Grain app with server-rendered reactive UI.

Stack:

- Clojure
- Integrant
- Grain event store, command routes, query routes, todo processors, periodic tasks
- Datastar-style server-rendered UI through Grain query routes
- Tailwind CSS and DaisyUI

Current backend entry point:

```clojure
(ns cjbarre.grain-todo-list
  (:require [integrant.core :as ig]
            [com.brunobonacci.mulog :as u]
            [clojure.set :as set]
            [ai.obney.grain.event-store-v3.interface :as es]
            [ai.obney.grain.kv-store.interface :as kv]
            [ai.obney.grain.kv-store-lmdb.interface :as lmdb]
            [ai.obney.grain.todo-processor-v2.interface :as tp]
            [ai.obney.grain.periodic-task.interface :as pt]
            [ai.obney.grain.command-request-handler-v2.interface :as crh]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.webserver.interface :as ws]))
```

Planned UI namespace:

```clojure
(ns cjbarre.grain-todo-list.ui)
```

The backend namespace should require the UI namespace once page/query rendering is added:

```clojure
[cjbarre.grain-todo-list.ui :as ui]
```

---

## File Responsibilities

### Backend File

Use `src/cjbarre/grain_todo_list.clj` for backend concerns:

- Integrant system map
- lifecycle functions: `start`, `stop`
- command handlers
- query handlers
- read model definitions and helpers
- todo processors
- periodic task handlers
- route assembly
- small backend utilities

Keep the file organized with comment sections instead of splitting into many namespaces too early.

Suggested order:

```clojure
;; Constants
;; Event helpers
;; Read models
;; Command handlers
;; Query handlers
;; Todo processors
;; Periodic tasks
;; Integrant system
;; Integrant init/halt methods
;; Lifecycle functions
;; REPL helpers
```

### UI File

Use `src/cjbarre/grain_todo_list/ui.clj` for UI concerns:

- pure Hiccup page functions
- reusable Hiccup components
- form helpers
- table/list/empty-state helpers
- Datastar attributes and signal helpers
- formatting helpers that are only used for display

UI functions should be pure. They receive data and return Hiccup. They should not read from the event store, mutate state, run commands, or access Integrant components.

Example:

```clojure
(ns cjbarre.grain-todo-list.ui)

(defn page-shell
  [{:keys [title]} & body]
  [:div#app
   [:main {:class "min-h-screen bg-base-100 text-base-content"}
    [:div {:class "mx-auto max-w-4xl px-4 py-8"}
     [:h1 {:class "mb-6 text-2xl font-semibold"} title]
     body]]])
```

---

## CSS

The CSS build is already adapted to this project layout.

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

The current system map lives directly in `cjbarre.grain-todo-list/system`.

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

Prefer adding small keys to this map over creating a new subsystem file.

### Routes

Routes are assembled in the `::routes` Integrant key:

```clojure
(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   #{["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]}))
```

Keep route wiring here unless the file becomes genuinely hard to navigate.

### Command Handlers

Commands should live in the backend file under a command section.

Use Grain command definitions from the relevant Grain command processor namespace. Keep each command small:

```clojure
;; Command handlers

(defcommand :todo create-item
  {:authorized? (constantly true)}
  "Create a todo item."
  [{{:keys [title]} :command :as ctx}]
  (cond
    (not (seq title))
    {::anom/category ::anom/incorrect
     ::anom/message "Title is required."}

    :else
    (let [item-id (random-uuid)]
      {:command-result/events
       [(->event {:type :todo/item-created
                  :tags #{[:todo-item item-id]}
                  :body {:item-id item-id
                         :title title}})]
       :datastar/signals {:title ""
                          :__toast "Item added"}})))
```

Validation pattern:

- return an anomaly for invalid input
- return `:command-result/events` on success
- return `:datastar/signals` when the UI should update local signals

Use event tags consistently:

```clojure
:tags #{[:todo-item item-id]}
```

### Read Models

Read models and read helpers also live in the backend file.

Pattern:

```clojure
;; Read models

(def todo-event-types
  #{:todo/item-created
    :todo/item-completed
    :todo/item-deleted})

(defmulti todos* (fn [_state event] (:event/type event)))

(defmethod todos* :todo/item-created
  [state {:keys [item-id title]}]
  (assoc state item-id {:item-id item-id
                        :title title
                        :completed? false}))

(defmethod todos* :todo/item-completed
  [state {:keys [item-id]}]
  (assoc-in state [item-id :completed?] true))

(defmethod todos* :todo/item-deleted
  [state {:keys [item-id]}]
  (dissoc state item-id))

(defmethod todos* :default [state _event]
  state)

(defreadmodel :todo items
  {:events todo-event-types
   :version 1}
  [state event]
  (todos* state event))

(defn get-items [ctx]
  (->> (rmp/project ctx :todo/items)
       vals
       (sort-by :title)))
```

Increment `:version` when the reducer behavior changes in a way that should rebuild cached projections.

### Query Handlers

Queries live in the backend file and call UI functions from `cjbarre.grain-todo-list.ui`.

Static page:

```clojure
(defquery :todo home-page
  {:authorized? (constantly true)
   :datastar/path "/"
   :datastar/title "Todo"
   :datastar/fps 0}
  "Render the home page once."
  [ctx]
  {:query/result {}
   :datastar/hiccup (ui/home-page {})})
```

Event-driven page:

```clojure
(defquery :todo items-page
  {:authorized? (constantly true)
   :datastar/path "/"
   :datastar/title "Todo"
   :grain/read-models {:todo/items 1}
   :datastar/debounce-ms 50}
  "Render todos and update when todo events arrive."
  [ctx]
  (let [items (get-items ctx)]
    {:query/result {:items items}
     :datastar/hiccup (ui/items-page {:items items})}))
```

Choose one render mode:

| Mode | Metadata | Use for |
|---|---|---|
| One-shot | `:datastar/fps 0` | static pages |
| Polling | `:datastar/fps N` | external data that changes outside events |
| Event-driven | `:grain/read-models {...}` | app state stored through events |

Do not combine `:datastar/fps` with `:grain/read-models` on the same query.

### Todo Processors

Event-driven side effects live in the backend file.

```clojure
;; Todo processors

(defn send-follow-up
  [{{:keys [item-id]} :event :as ctx}]
  ;; Dispatch a command, send a notification, etc.
  )

(def todo-processors
  {:todo/send-follow-up
   {:handler-fn #'send-follow-up
    :topics [:todo/item-created]}})
```

Use var references (`#'send-follow-up`) so REPL reloading updates behavior without rebuilding the whole system.

### Periodic Tasks

Scheduled tasks live in the backend file.

```clojure
;; Periodic tasks

(defn prune-completed-items
  [ctx _time]
  ;; scan read model, dispatch commands, or append events
  )

(def periodic-tasks
  {:todo/prune-completed-items
   {:handler-fn #'prune-completed-items
    :schedule {:every 60 :duration :seconds}}})
```

Keep periodic tasks idempotent. If a task can run twice, use event-store CAS or command-level validation to avoid duplicate effects.

---

## UI Patterns

### Page Shape

Every Datastar page should return a `div#app` root so the server can patch the page consistently.

```clojure
(defn items-page
  [{:keys [items]}]
  [:div#app
   [:div {:class "min-h-screen bg-base-100"}
    [:main {:class "mx-auto max-w-4xl px-4 py-8"}
     [:header {:class "mb-6 flex items-center justify-between gap-4"}
      [:h1 {:class "text-2xl font-semibold"} "Todo"]
      [:button {:class "btn btn-primary btn-sm"
                :data-on-click "$addOpen = true"}
       "Add"]]
     (if (seq items)
       (items-list items)
       (empty-state "No items yet."))]]])
```

### Small Components

Prefer tiny functions in the UI file over a broad component library:

```clojure
(defn empty-state [message]
  [:div {:class "rounded-box border border-base-300 p-8 text-center text-base-content/60"}
   message])

(defn field
  [{:keys [label data-bind type required?]}]
  [:label {:class "form-control w-full"}
   [:div {:class "label"} [:span {:class "label-text"} label]]
   [:input {:type (or type "text")
            :data-bind data-bind
            :required required?
            :class "input input-bordered w-full"}]])
```

### Datastar Signals

Use plain `data-*` attributes directly until repetition justifies helpers.

Common attributes:

| Attribute | Purpose |
|---|---|
| `data-signals` | initial signal values |
| `data-bind` | two-way bind input value |
| `data-show` | show/hide element |
| `data-text` | render signal text |
| `data-on-click` or `data-on:click` | click behavior |
| `data-on-submit__prevent` or `data-on:submit__prevent` | intercept form submit |

Example in-page command submission:

```clojure
[:form {:data-on-submit__prevent
        "$['command/name'] = 'todo/create-item'; @post('/actions')"}
 (field {:label "Title" :data-bind "title" :required? true})
 [:button {:type "submit" :class "btn btn-primary"} "Add"]]
```

Use signal names intentionally:

| Prefix | Meaning |
|---|---|
| none | domain/input signal sent to server, such as `$title` |
| `_` | local UI-only signal, such as `$_menuOpen` |
| `__` | framework/internal signal, such as `$__toast` |
| `command/name` | command selected for action submission |

### Forms

For normal app actions, prefer Datastar submissions that call command routes and patch the existing page.

For browser-level flows that need a redirect or cookies, use a regular HTML form and a normal POST route.

Minimal Datastar form:

```clojure
(defn add-item-form []
  [:form {:class "space-y-4"
          :data-on-submit__prevent
          "$['command/name'] = 'todo/create-item'; @post('/actions')"}
   (field {:label "Title" :data-bind "title" :required? true})
   [:div {:data-show "$error" :class "alert alert-error"}
    [:span {:data-text "$error"}]]
   [:button {:type "submit" :class "btn btn-primary"} "Add item"]])
```

### Lists

Keep list rendering boring and durable:

```clojure
(defn items-list [items]
  [:ul {:class "divide-y divide-base-300 rounded-box border border-base-300"}
   (for [{:keys [item-id title completed?]} items]
     [:li {:key item-id
           :class "flex items-center justify-between gap-4 p-3"}
      [:span {:class (when completed? "line-through opacity-60")} title]
      [:button {:class "btn btn-ghost btn-sm"
                :data-on-click
                (str "$['command/name'] = 'todo/complete-item'; "
                     "$['item-id'] = '" item-id "'; "
                     "@post('/actions')")}
       "Done"]])])
```

---

## New Feature Checklist

For this project, do not create a new component or service directory. Add the feature in place.

1. Add event type names and read model logic to `src/cjbarre/grain_todo_list.clj`.
2. Add read helper functions in the same backend file.
3. Add command handlers in the same backend file.
4. Add query handlers in the same backend file.
5. Add pure page/component functions to `src/cjbarre/grain_todo_list/ui.clj`.
6. Make the query call the UI function.
7. Add route or action wiring in the existing `::routes` Integrant key only if the Grain route helpers do not already provide it.
8. Run `npm run css:build` after adding new Tailwind classes.
9. Start the app from the REPL with `(def app (start))`.

Only split a new namespace out when the two-file structure is clearly slowing development down.

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

If a command appears to do nothing:

- test the command directly in the REPL
- check authorization
- check that command fields match signal names
- check that event maps contain the expected `:type`, `:tags`, and `:body`

If CSS is missing:

- run `npm run css:build`
- confirm the generated file exists at `resources/public/css/main.css`
- confirm classes are present in `src/**/*.clj` so Tailwind can discover them

---

## Translation Guide From Grain Examples

When reading Grain or Polylith examples, translate paths like this:

| Example path | This project |
|---|---|
| `bases/web-api/src/.../core.clj` | `src/cjbarre/grain_todo_list.clj` |
| `components/{service}/core/commands.clj` | command section in `src/cjbarre/grain_todo_list.clj` |
| `components/{service}/core/queries.clj` | query section in `src/cjbarre/grain_todo_list.clj` |
| `components/{service}/core/read_models.clj` | read model section in `src/cjbarre/grain_todo_list.clj` |
| `components/{service}/core/views.clj` | `src/cjbarre/grain_todo_list/ui.clj` |
| `components/{service}/interface.clj` | no equivalent; require the local namespace directly |
| `bases/web-api/resources/.../public/css/main.css` | `resources/public/css/main.css` |

Keep the conceptual patterns. Drop the Polylith ceremony.
