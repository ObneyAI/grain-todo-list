---
name: grain-periodic-task
description: Create scheduled background jobs that run on cron schedules
---

# Grain Periodic Task Pattern

Periodic tasks are scheduled background jobs that run on cron or interval schedules.
They operate outside the request/response cycle and are used for maintenance, cleanup,
reminders, and other time-based operations.

In this compact app, periodic triggers are managed through the periodic-task interface
(`ai.obney.grain.periodic-task.interface`). A service exposes a `periodic_tasks.clj`
file with `start`/`stop` functions that wrap `pt/start-periodic-triggers!` and
`pt/stop-periodic-triggers!`. The root system wires this lifecycle through the
`::periodic-triggers` Integrant key in `src/cjbarre/grain_todo_list.clj`.

## Task File (`service/<svc>-service/periodic_tasks.clj`)

```clojure
(ns cjbarre.grain-todo-list.service.<svc>-service.periodic-tasks
  "Scheduled background triggers for <svc>-service."
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

`pt/start-periodic-triggers!` takes an `:append-fn` (used to append the trigger event
into the event store) and a `:tenant-ids-fn` returning the set of tenant ids to run for.
It returns a handle that `pt/stop-periodic-triggers!` shuts down. The individual triggers
(their schedules and the events they emit) are defined via the periodic-task interface;
prefer the real `pt/start-periodic-triggers!` / `pt/stop-periodic-triggers!` names over
any ad-hoc registry.

## Idempotency

A periodic task may run twice (e.g. overlapping ticks or a restart). Make each run safe
to repeat. Two strategies:

### CAS at the event store

Use a compare-and-swap append so the work only happens once for a given key/period:

```clojure
(require '[ai.obney.grain.event-store-v3.interface :as es :refer [->event]])

(es/append event-store
           {:tenant-id tenant-id
            :events [(->event {:type :todo/digest-sent
                               :tags #{[:user user-id]}
                               :body {:user-id user-id
                                      :date (str (java.time.LocalDate/now))}})]
            :cas {:types #{:todo/digest-sent}
                  :tags #{[:user user-id]}
                  :predicate-fn (fn [existing]
                                  (empty? (into [] existing)))}})
```

The CAS predicate checks no `:todo/digest-sent` event already exists for this user with
these tags. If another run already emitted one, the append silently fails — at most once.

### Command-level validation

Alternatively, let the triggered command's own validation reject a duplicate (e.g. the
command refuses if the entity is already in the target state). Either way, a task running
twice must not double-apply its effect.

## Multi-Tenant

`tenant-ids-fn` returns the set of tenants the triggers run for. In this single-tenant
dev app it is `(constantly #{tenant-id})`. To enumerate tenants dynamically, read them
from the event store: `es/tenants` (from `ai.obney.grain.event-store-v3.interface`)
returns `{tenant-id {:tenant/last-event-id uuid-or-nil}}` — use `(keys ...)` for the ids.

## Wiring

The root system already wires periodic triggers via the `::periodic-triggers` Integrant
key in `src/cjbarre/grain_todo_list.clj`:

```clojure
;; in the system map:
::periodic-triggers {:event-store (ig/ref ::event-store)
                     :tenant-id tenant-id}

;; init / halt:
(defmethod ig/init-key ::periodic-triggers [_ config]
  (periodic-tasks/start config))

(defmethod ig/halt-key! ::periodic-triggers [_ triggers]
  (periodic-tasks/stop triggers))
```

with `[cjbarre.grain-todo-list.service.todo-list-service.periodic-tasks :as periodic-tasks]`
in the ns require. To add periodic tasks for another service, either add triggers to the
existing `periodic-tasks/start`, or add a new service `periodic_tasks.clj` and a parallel
Integrant key. After changing it, restart the system:

```clojure
(app/stop app)
(def app (app/start))
```

## Reference File

- `src/cjbarre/grain_todo_list/service/todo_list_service/periodic_tasks.clj` — working `start`/`stop`
- `src/cjbarre/grain_todo_list.clj` — the `::periodic-triggers` Integrant wiring
