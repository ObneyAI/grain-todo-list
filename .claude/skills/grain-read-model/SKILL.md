---
name: grain-read-model
description: Build read models by reducing events into queryable state with defreadmodel
---

# Grain Read Model Pattern

## Required Imports

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.read-models
  (:require [ai.obney.grain.read-model-processor-v2.interface :as rmp
             :refer [defreadmodel]]))
```

This repo registers read models with `defreadmodel` (a cached, event-driven
projection) rather than reducing the event store by hand in each helper.

## Event Type Sets

Define which events affect each read model:

```clojure
(def task-event-types
  #{:todo/task-captured
    :todo/task-renamed
    :todo/task-assigned-to-project
    :todo/task-completed
    :todo/task-archived
    :todo/task-canceled
    :todo/task-reactivated})

(def project-event-types
  #{:todo/project-created
    :todo/project-renamed
    :todo/project-completed
    :todo/project-canceled
    :todo/project-reactivated})
```

## Projection Multimethod

```clojure
;; Dispatch on event type
(defmulti tasks* (fn [_state event] (:event/type event)))

;; Handle creation
(defmethod tasks* :todo/task-captured
  [state {:keys [user-id task-id title status order project-id]}]
  (assoc state task-id
         (cond-> {:user-id user-id
                  :task-id task-id
                  :title title
                  :status status
                  :order order}
           project-id (assoc :project-id project-id))))

;; Handle rename
(defmethod tasks* :todo/task-renamed
  [state {:keys [task-id title]}]
  (assoc-in state [task-id :title] title))

;; Handle assignment
(defmethod tasks* :todo/task-assigned-to-project
  [state {:keys [task-id project-id]}]
  (assoc-in state [task-id :project-id] project-id))

;; Handle status transition
(defmethod tasks* :todo/task-completed
  [state {:keys [task-id]}]
  (assoc-in state [task-id :status] :completed))

;; Default: ignore unknown events
(defmethod tasks* :default [state _event] state)
```

## Registering the Read Model

`defreadmodel` ties the dispatch multimethod, the event-type set, and a version
together. The framework maintains the cached projection and rebuilds it from
events as needed:

```clojure
(defreadmodel :todo tasks
  {:events task-event-types :version 1}
  [state event]
  (tasks* state event))
```

- State is a map keyed by entity id: `{uuid1 {...} uuid2 {...}}`.
- Always provide a `:default` method so unrelated events are ignored.
- **Bump `:version`** whenever the reducer's behavior changes in a way that
  should rebuild the cached projection (new fields, changed shape, etc.).
- The **state schema** for each read model is declared in the
  `read-model-schemas` group in `schemas.clj` (e.g. `:todo/tasks
  [:map-of :uuid ::task]`).

## Reading a Projection

Helpers take `ctx` (the full context) and read the cached projection via
`rmp/project` — never `event-store/read` directly:

```clojure
;; Whole projection (a map keyed by entity id)
(defn all-tasks [ctx]
  (rmp/project ctx :todo/tasks))

;; A single entity, using tag-scoped read options
(defn get-task [ctx task-id]
  (get (rmp/project ctx :todo/tasks {:tags #{[:task task-id]}}) task-id))
```

## Query Helper Functions

Helpers derive views over the projection. They all take `ctx`:

### Get All Entities

```clojure
(defn all-tasks [ctx]
  (vals (rmp/project ctx :todo/tasks)))
```

### Get by Filter

```clojure
(defn active-task? [task] (= :active (:status task)))

(defn active-tasks
  "Active tasks, ordered."
  [ctx]
  (->> (vals (rmp/project ctx :todo/tasks))
       (filter active-task?)
       (sort-by (juxt :order :title :task-id))))
```

### Scoping Reads (e.g. per-user)

Use read options on `rmp/project` to scope by tag instead of scanning
everything:

```clojure
(defn user-scope [ctx]
  (when-let [user-id (:current-user/id ctx)]
    {:tags #{[:user user-id]}}))

(defn tasks-for-user [ctx]
  (rmp/project ctx :todo/tasks (user-scope ctx)))
```

### Composing Projections

Combine multiple read models in a single helper, each via `rmp/project`:

```clojure
(defn project-task-counts
  "Count tasks by status for one project."
  [ctx project-id]
  (->> (vals (rmp/project ctx :todo/tasks))
       (filter #(= project-id (:project-id %)))
       (group-by :status)
       (reduce-kv (fn [m status tasks] (assoc m status (count tasks)))
                  {:active 0 :completed 0 :canceled 0 :archived 0})))

(defn project-summaries [ctx]
  (->> (vals (rmp/project ctx :todo/projects))
       (map (fn [project]
              (assoc project :task-counts
                     (project-task-counts ctx (:project-id project)))))))
```

## Read Options (passed to `rmp/project`)

```clojure
;; By tags (entities)
{:tags #{[:task task-id]}}

;; Multiple tags (batch / scope)
{:tags #{[:user user-id]}}
```

## Conventions

- Register each read model with `defreadmodel :ns name {:events ... :version N}`
- State is a map keyed by entity ID: `{uuid1 {...} uuid2 {...}}`
- Use `assoc` for create, `update`/`assoc-in` for modify
- Always provide a `:default` method to ignore unknown events
- Bump `:version` when reducer behavior changes
- Helpers take `ctx` and read via `rmp/project` (never `event-store/read`)
- Use tag-scoped read options to avoid scanning all events
- Declare the projection's state shape in `read-model-schemas` in `schemas.clj`

## Reference Files

- `src/cjbarre/grain_todo_list/service/todo_list_service/read_models.clj` - Task/project/review projections
- `src/cjbarre/grain_todo_list/service/user_service/read_models.clj` - User projections
