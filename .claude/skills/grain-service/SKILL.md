---
name: grain-service
description: Create a complete Grain domain service with schemas, read models, commands, queries, views, and tests
---

# Create a Complete Grain Domain Service

This skill walks through creating a full CRUD domain service from scratch. Follow each step
mechanically. The example builds a "Widgets" service -- replace `widgets`/`widget` with your actual
domain entity throughout.

This repo is **compact** (a single `src/` tree under one app namespace), not Polylith — do **not**
introduce Polylith `components/`, `bases/`, `interface` namespaces, or per-service `deps.edn` files.
A service is just a directory of namespaces under `src/cjbarre/grain_todo_list/service/`, registered
into the running system by side-effect `require`s from the root namespace `cjbarre.grain-todo-list`.

**Naming conventions used below:**
- Service name: `widgets` (used as the event/command/query keyword prefix)
- Entity name: `widget` (singular)
- Service dir: `src/cjbarre/grain_todo_list/service/widget_service/`
- Namespace prefix: `cjbarre.grain-todo-list.service.widget-service`

---

## Step 1: Create the Service Directory

Create the service namespace tree (no `deps.edn`, no `interface.clj`, no `core/` segment):

```
src/cjbarre/grain_todo_list/service/widget_service/
  schemas.clj
  read_models.clj
  commands.clj
  queries.clj
  ui.clj
  ui/components.clj
  todo_processors.clj        ;; only if the service has event-driven side effects
test/cjbarre/grain_todo_list/service/widget_service/
  commands_test.clj
```

Each namespace maps directly to its file:

| File | Namespace |
| --- | --- |
| `schemas.clj` | `cjbarre.grain-todo-list.service.widget-service.schemas` |
| `read_models.clj` | `cjbarre.grain-todo-list.service.widget-service.read-models` |
| `commands.clj` | `cjbarre.grain-todo-list.service.widget-service.commands` |
| `queries.clj` | `cjbarre.grain-todo-list.service.widget-service.queries` |
| `ui.clj` | `cjbarre.grain-todo-list.service.widget-service.ui` |
| `ui/components.clj` | `cjbarre.grain-todo-list.service.widget-service.ui.components` |
| `todo_processors.clj` | `cjbarre.grain-todo-list.service.widget-service.todo-processors` |

There is **no** interface namespace and **no** schema re-export indirection. Cross-service code
requires the concrete local namespace directly, e.g.
`[cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]`.

---

## Step 2: Define Schemas (`schemas.clj`)

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.schemas
  "Malli schemas for widget service commands, events, queries, and read models."
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas common-schemas
  {::widget [:map
             [:user-id :uuid]
             [:widget-id :uuid]
             [:name [:string {:min 1 :error/message "Name is required"}]]
             [:description {:optional true} [:maybe :string]]
             [:active :boolean]]})

(defschemas event-schemas
  {:widgets/widget-created [:map
                           [:user-id :uuid]
                           [:widget-id :uuid]
                           [:name :string]
                           [:description [:maybe :string]]]
   :widgets/widget-updated [:map
                           [:user-id :uuid]
                           [:widget-id :uuid]
                           [:name :string]
                           [:description [:maybe :string]]]
   :widgets/widget-deleted [:map
                           [:user-id :uuid]
                           [:widget-id :uuid]]})

(defschemas command-schemas
  {:widgets/create-widget [:map
                           [:name [:string {:min 1 :error/message "Name is required"}]]
                           [:description {:optional true} [:maybe :string]]]
   :widgets/update-widget [:map
                           [:widget-id :uuid]
                           [:name [:string {:min 1 :error/message "Name is required"}]]
                           [:description {:optional true} [:maybe :string]]]
   :widgets/delete-widget [:map
                           [:widget-id :uuid]]})

(defschemas query-schemas
  {:widgets/widgets-page [:map]
   :widgets/widget-detail-page [:map [:widget-id :uuid]]})

(defschemas read-model-schemas
  {:widgets/widgets [:map-of :uuid ::widget]})
```

The five group names (`common-schemas`, `event-schemas`, `command-schemas`, `query-schemas`,
`read-model-schemas`) match the real services exactly — see `todo_list_service/schemas.clj`.

---

## Step 3: Build Read Model (`read_models.clj`)

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.read-models
  "Widget read models -- projections built from events."
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp :refer [defreadmodel]]))

;; Event type set
(def widget-event-types
  #{:widgets/widget-created
    :widgets/widget-updated
    :widgets/widget-deleted})

;; Reducer multimethod
(defmulti widgets* (fn [_state event] (:event/type event)))

(defmethod widgets* :widgets/widget-created
  [state {:keys [user-id widget-id name description]}]
  (assoc state widget-id {:user-id user-id
                          :widget-id widget-id
                          :name name
                          :description description
                          :active true}))

(defmethod widgets* :widgets/widget-updated
  [state {:keys [widget-id name description]}]
  (update state widget-id merge {:name name :description description}))

(defmethod widgets* :widgets/widget-deleted
  [state {:keys [widget-id]}]
  (dissoc state widget-id))

(defmethod widgets* :default [state _event] state)

;; Register read model
(defreadmodel :widgets widgets
  {:events widget-event-types :version 1}
  [state event]
  (widgets* state event))

;; Projection helpers -- take ctx, read via rmp/project.
;; Scope reads to the current user with a tag (matches todo-list-service).
(defn user-scope
  [ctx]
  (when-let [user-id (:current-user/id ctx)]
    {:tags #{[:user user-id]}}))

(defn all-widgets
  [ctx]
  (if-let [scope (user-scope ctx)]
    (rmp/project ctx :widgets/widgets scope)
    (rmp/project ctx :widgets/widgets {:tags #{[:user nil]}})))

(defn get-widget
  [ctx widget-id]
  (get (rmp/project ctx :widgets/widgets {:tags #{[:widget widget-id]}})
       widget-id))

(defn active-widgets
  [ctx]
  (->> (vals (all-widgets ctx))
       (filter :active)
       (sort-by :name)))
```

---

## Step 4: Implement Commands (`commands.clj`)

Authorization functions take `ctx` and read identity from `(:current-user/id ctx)`. Errors return
anomaly maps (`{::anom/category ... ::anom/message ...}`). Do **NOT** return form-field-reset signals
(e.g. `:name ""`) from commands — clearing a field is UI behavior, done with `ds-ui/reset-signal` in
the component effect (see Step 6).

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.commands
  "Command handlers for widget service."
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [cjbarre.grain-todo-list.service.widget-service.read-models :as rm]
            [cognitect.anomalies :as anom]))

(defn current-user-id [ctx] (:current-user/id ctx))

(defn authenticated? [ctx] (some? (current-user-id ctx)))

(defn owns-widget?
  [{{:keys [widget-id]} :command :as ctx}]
  (boolean (and (authenticated? ctx)
                widget-id
                (rm/get-widget ctx widget-id))))

(defcommand :widgets create-widget
  {:authorized? authenticated?}
  "Create a new widget."
  [{{:keys [name description]} :command :as ctx}]
  (let [widget-id (random-uuid)
        user-id (current-user-id ctx)]
    {:command-result/events
     [(->event {:type :widgets/widget-created
                :tags #{[:widget widget-id] [:user user-id]}
                :body {:user-id user-id
                       :widget-id widget-id
                       :name name
                       :description description}})]
     :datastar/signals {:__toast "Widget created!"}}))

(defcommand :widgets update-widget
  {:authorized? owns-widget?}
  "Update an existing widget."
  [{{:keys [widget-id name description]} :command :as ctx}]
  (let [widget (rm/get-widget ctx widget-id)
        user-id (current-user-id ctx)]
    (cond
      (not widget)
      {::anom/category ::anom/not-found
       ::anom/message "Widget not found."}

      :else
      {:command-result/events
       [(->event {:type :widgets/widget-updated
                  :tags #{[:widget widget-id] [:user user-id]}
                  :body {:user-id user-id
                         :widget-id widget-id
                         :name name
                         :description description}})]
       :datastar/signals {:__toast "Widget updated!"}})))

(defcommand :widgets delete-widget
  {:authorized? owns-widget?}
  "Delete a widget."
  [{{:keys [widget-id]} :command :as ctx}]
  (let [widget (rm/get-widget ctx widget-id)
        user-id (current-user-id ctx)]
    (cond
      (not widget)
      {::anom/category ::anom/not-found
       ::anom/message "Widget not found."}

      :else
      {:command-result/events
       [(->event {:type :widgets/widget-deleted
                  :tags #{[:widget widget-id] [:user user-id]}
                  :body {:user-id user-id :widget-id widget-id}})]
       :datastar/signals {:__toast "Widget deleted."}})))
```

---

## Step 5: Implement Queries with Datastar (`queries.clj`)

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.queries
  "Query handlers for widget service pages."
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.service.widget-service.read-models :as rm]
            [cjbarre.grain-todo-list.service.widget-service.ui :as ui]))

(defn render [page] (ds-ui/hiccup page))

(defn current-user-id [ctx] (:current-user/id ctx))
(defn authenticated? [ctx] (some? (current-user-id ctx)))

;; Event-driven page -- re-renders when widget events fire.
;; Hybrid auth: widgets are account-scoped here, so the page requires login. A PUBLIC browsing/landing
;; page would use `:authorized? (constantly true)` instead — gate only per-user/account-scoped data.
(defquery :widgets widgets-page
  {:authorized? authenticated?
   :datastar/path "/widgets"
   :datastar/title "Widgets"
   :grain/read-models {:widgets/widgets 1}}
  "Widgets management page."
  [ctx]
  (let [widgets (vec (rm/active-widgets ctx))]
    {:query/result {:widgets widgets}
     :datastar/hiccup (render (ui/widgets-page {:widgets widgets}))}))

;; Detail page.
;; ROUTES USE QUERY PARAMS, NEVER PATH PARAMS. e.g. /widget?widget-id=<uuid>
;; Read it from (:query ctx). Never write "/widgets/:widget-id".
(defquery :widgets widget-detail-page
  {:authorized? authenticated?
   :datastar/path "/widget"
   :datastar/title "Widget Detail"
   :grain/read-models {:widgets/widgets 1}}
  "Widget detail page."
  [{{:keys [widget-id]} :query :as ctx}]
  (let [widget (rm/get-widget ctx widget-id)]
    {:query/result {:widget widget}
     :datastar/hiccup (render (ui/widget-detail-page {:widget widget}))}))
```

**Rendering mode — always event-driven. NEVER use `:datastar/fps`.**

Every query you write shows domain data, so every query declares `:grain/read-models {…}` for the read
models it renders. The page then re-renders automatically (and *only*) when those events fire — no
polling, never stale.

```clojure
:grain/read-models {:widgets/widgets 1}   ;; <rm-name> <version> — event-driven
```

Do **NOT** use `:datastar/fps`. It's the polling/one-shot knob, and it's a footgun: if you omit
`:grain/read-models`, grain defaults to **30fps polling** (wasteful + laggy). The only non-polling use
of `:datastar/fps 0` is a truly static, contentless page (render-once) — your feature pages always
have data, so they always use `:grain/read-models`.

**Path params:** read query params from `(:query ctx)`. The `:widget-id` here arrives via
`/widget?widget-id=<uuid>` and is coerced to `:uuid` by the query schema.

---

## Step 6: Build UI (`ui.clj` + `ui/components.clj`)

Pages are **thin compositions of the app's component library**. App-wide primitives live in
`cjbarre.grain-todo-list.foundation.ui` (`app-shell`, `action-error`) and
`cjbarre.grain-todo-list.foundation.ui.components` (`surface`, `action-button`, `text-field`, `chip`,
`badge`, `empty-state`, `page-section`, `panel`, `section-heading`, `metadata-text`, …). Domain widgets
(forms, rows, anything with `ds-ui` effects) live in your service's `ui/components.clj`. Never hand-roll
one-off layout — factor a component instead. See the `grain-ui-component` skill for the checked
`ds-ui` DSL.

### `ui.clj` — pure page functions

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.ui
  "Pure Hiccup pages -- thin compositions of foundation + service components."
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.foundation.ui :as app-ui]
            [cjbarre.grain-todo-list.foundation.ui.components :as c]
            [cjbarre.grain-todo-list.service.widget-service.ui.components :as wc]))

(defn widgets-page [{:keys [widgets]}]
  (app-ui/app-shell {:title "Widgets"}
    (app-ui/action-error)
    (c/panel {:title "Widgets" :status "Manage your widgets."}
             (wc/widget-add)
             (c/page-section {:title "All Widgets" :count (count widgets)}
                             (wc/widget-list widgets)))))

(defn widget-detail-page [{:keys [widget]}]
  (app-ui/app-shell {:title (or (:name widget) "Widget")}
    (app-ui/action-error)
    (if widget
      (c/surface {:tag :section :variant :card}
        (c/section-heading {:title (:name widget)})
        (c/metadata-text (:description widget)))
      [:div {:class "alert alert-warning"} "Widget not found."])))
```

### `ui/components.clj` — domain widgets with the checked `ds-ui` DSL

Commands dispatch through the single `/actions` route with `ds-ui/dispatch`. Reset form fields here
with `ds-ui/reset-signal` — never from the command.

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.ui.components
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.foundation.ui.components
             :refer [empty-state metadata-text surface text-field]]))

(defn widget-add []
  (ds-ui/with-signals [name {:init ""}]
    [:form {:on/submit {:effect (ds-ui/effects
                                 (ds-ui/dispatch :widgets/create-widget
                                                 {:name (ds-ui/trimmed name)})
                                 (ds-ui/reset-signal name))
                        :modifiers {:prevent true}}}
     (text-field {:bind/value name
                  :aria-label "Widget name"
                  :placeholder "Add a widget"
                  :required true})]))

(defn widget-row [widget]
  [:a {:href (ds-ui/href :widgets/widget-detail-page {:widget-id (:widget-id widget)})
       :class "block"}
   [:p {:class "font-medium"} (:name widget)]
   (metadata-text (:description widget))])

(defn widget-list [widgets]
  (if (seq widgets)
    (surface {:variant :list} (map widget-row widgets))
    (empty-state {:title "No widgets yet"
                  :description "Create your first widget to get started."})))
```

(Use the real component names you find in `foundation/ui/components.clj` — the list above is
representative, not exhaustive. Inspect that file for exact signatures.)

---

## Step 7: Write Tests

### Command Tests (`test/cjbarre/grain_todo_list/service/widget_service/commands_test.clj`)

Tests call the generated command function directly. `defcommand :widgets create-widget` generates a
function named `widgets-create-widget`.

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.commands-test
  (:require [clojure.test :refer [deftest testing is]]
            [cjbarre.grain-todo-list.service.widget-service.commands :as cmd]
            [cognitect.anomalies :as anom]))

(defn find-event [result event-type]
  (->> (:command-result/events result)
       (filter #(= event-type (:event/type %)))
       first
       :event/body))

(defn event-of-type? [result event-type]
  (some? (find-event result event-type)))

(deftest create-widget-test
  (testing "creates a widget successfully"
    (let [user-id (random-uuid)
          ctx {:current-user/id user-id
               :command {:name "Test Widget" :description "A test widget"}}
          result (cmd/widgets-create-widget ctx)]
      (is (contains? result :command-result/events))
      (is (event-of-type? result :widgets/widget-created))
      (let [event (find-event result :widgets/widget-created)]
        (is (some? (:widget-id event)))
        (is (= "Test Widget" (:name event)))))))
```

For commands whose authorization or behavior depends on existing read-model state (`update-widget`,
`delete-widget`), drive the full event-store round trip with the Grain test utilities — model your
setup on the real command tests in the repo (auth, append events, then re-query). The
`:authorized?` fn and the read-model projection are what make state-dependent commands meaningful.

---

## Step 8: Wire into the root app ns (`cjbarre.grain-todo-list`)

Edit `src/cjbarre/grain_todo_list.clj`. The system picks up your service entirely through side-effect
`require`s — there is **no** `deps.edn` change and **no** per-command/per-query route to add.

### 8a. Add side-effect requires

In the `:require` block of `cjbarre.grain-todo-list`, add (matching the existing
`todo-list-service` / `user-service` entries):

```clojure
[cjbarre.grain-todo-list.service.widget-service.commands]
[cjbarre.grain-todo-list.service.widget-service.queries]
[cjbarre.grain-todo-list.service.widget-service.read-models]
[cjbarre.grain-todo-list.service.widget-service.schemas]
;; only if the service has event-driven side effects:
[cjbarre.grain-todo-list.service.widget-service.todo-processors]
```

- `commands` / `queries` registering by side-effect puts them in the global registries
  (`cp/global-command-registry`, `query-processor/global-query-registry`).
- `read-models` / `schemas` register the read model and Malli schemas.
- Datastar query pages **auto-register** their routes from query metadata via `ds/routes` (already
  wired in the `::routes` init-key). You do **not** add a route per page.
- Commands POST to the single `/actions` route, already wired with `ds/action-handler` in
  `::routes`. You do **not** add a route per command.

That's it. No processors map to wire (see Step 10), no Integrant key to touch.

---

## Step 9: Pass the Definition of Done (the gate)

Adding the requires is **not** done. Finish at the gate (see the Charter + Definition of done in
CLAUDE.md) — all must pass, and report each:

1. **Routes live** — restart the Integrant system from the REPL:
   ```clojure
   (require '[cjbarre.grain-todo-list :as app])
   (app/stop app)
   (def app (app/start))
   ```
   After editing a single namespace you can reload just it: `(require 'the.ns :reload)`, but a clean
   restart is the reliable way to pick up new registrations.
2. `clojure -T:build test` green (`test` skill).
3. `(:missing-schemas (tools/catalog))` empty.
4. **Auth:** account-scoped queries/commands declare a real `:authorized?` (mutations are never
   `(constantly true)`).
5. CSS built (no `404 /css/main.css`). Lint clean: `clj-kondo --lint src test`.
6. Open `/widgets` and verify **EACH acceptance check as a user** (pass/fail) — acceptance is the
   contract.
7. Run the **`design-review`** skill on each screen until it passes the rubric.
8. **Honest:** if anything fails and you can't fix it, say which — never call a broken build done.

---

## Step 10: Event-Driven Side Effects (`todo_processors.clj`)

If the service needs to react to its own (or another service's) events — send an email, fire a
follow-up command — add `defprocessor` forms. They register by the side-effect `require` you already
added in Step 8a; the root system already runs a tenant poller, so a new service usually needs
**nothing else**. There is **no** `(def todo-processors {})` map and **no** Integrant wiring of a
processors map.

```clojure
(ns cjbarre.grain-todo-list.service.widget-service.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :refer [defprocessor]]
            [cjbarre.grain-todo-list.foundation.email :as email]))

(defprocessor :widgets notify-on-create
  {:topics #{:widgets/widget-created}}
  [{{:keys [name]} :event
    :keys [app-base email-client email-from]}]
  {:result/effect
   (fn []
     (email/send
      email-client
      {:from email-from
       :to ["ops@grain-todo.local"]
       :subject "New widget created"
       :body-html (format "<p>Widget created: %s</p>" name)}))
   :result/checkpoint :after})
```

See `user_service/todo_processors.clj` for the real, working pattern (auth-domain emails).

If your service additionally needs its own lifecycle (rare), `todo_processors.clj` may also hold
`start`/`stop` fns wrapping `tp/start-tenant-poller` / `tp/stop-tenant-poller` — but the root system
already runs a poller, so most services only need `defprocessor` forms.

If no side effects are needed, **do not create this file at all** — skip it.

---

## Reference Files

The fullest worked example is the todo-list service; the user service shows the auth domain and
`defprocessor`. Read these for exact idioms:

- `src/cjbarre/grain_todo_list/service/todo_list_service/schemas.clj` — five schema groups
- `src/cjbarre/grain_todo_list/service/todo_list_service/read_models.clj` — read model + projection helpers
- `src/cjbarre/grain_todo_list/service/todo_list_service/commands.clj` — command handlers + auth fns
- `src/cjbarre/grain_todo_list/service/todo_list_service/queries.clj` — query handlers
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj` — pure page functions
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj` — domain widgets (ds-ui DSL)
- `src/cjbarre/grain_todo_list/service/todo_list_service/todo_processors.clj` — `defprocessor` (if present)
- `src/cjbarre/grain_todo_list/service/todo_list_service/periodic_tasks.clj` — periodic triggers
- `src/cjbarre/grain_todo_list/service/user_service/{commands,queries,read_models,schemas}.clj` — auth domain
- `src/cjbarre/grain_todo_list/service/user_service/todo_processors.clj` — `defprocessor` examples
- `src/cjbarre/grain_todo_list/foundation/ui.clj` — `app-shell`, `action-error`
- `src/cjbarre/grain_todo_list/foundation/ui/components.clj` — the app UI component library
- `src/cjbarre/grain_todo_list.clj` — root: Integrant system, routes, side-effect requires, wiring
- `grain-ui-component` + `design-review` skills — the app component library + visual quality gate
