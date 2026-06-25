---
name: grain-command-handler
description: Implement command handlers that validate state, emit events, and return signals using the Grain framework
---

# Grain Command Handler Pattern

## Required Imports

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.commands
  (:require [ai.obney.grain.command-processor-v2.interface :refer [defcommand]]
            [ai.obney.grain.command-processor-v2.interface :as cp]
            [ai.obney.grain.event-store-v3.interface :refer [->event]]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [cognitect.anomalies :as anom]))
```

## Command Template

```clojure
(defcommand :todo capture-task
  {:authorized? can-capture-task?}
  "Add a new task."
  [{{:keys [task-id title project-id]} :command :as ctx}]

  ;; 1. Read current state from read models (pass ctx, NOT bare event-store)
  (let [task-id (or task-id (random-uuid))
        user-id (current-user-id ctx)
        project (when project-id (require-project ctx project-id))]
    (cond
      ;; Return anomaly for validation failures
      (contains? project ::anom/category) project

      (and project (not= :active (:status project)))
      {::anom/category ::anom/conflict
       ::anom/message "Cannot assign a task to an inactive project."}

      ;; Success: return events + optional signals
      :else
      {:command-result/events
       [(->event
         {:type :todo/task-captured
          :tags #{[:task task-id] [:user user-id]}
          :body (cond-> {:user-id user-id
                         :task-id task-id
                         :title title
                         :status :active
                         :order (next-order (rm/active-tasks ctx))}
                  project-id (assoc :project-id project-id))})]
       :datastar/signals {:__toast "Task added."}})))
```

## Authorization Metadata

The `{:authorized? auth-fn}` metadata map goes between the namespace keyword and
the (optional) docstring.

**Hybrid-auth rule:** a command that **writes** or touches **per-user /
account-scoped** data MUST use a real predicate — **never `(constantly true)` on
a mutation.** `(constantly true)` is only for genuinely public, non-account
actions (e.g. sign-up / login before there is a user).

In this repo, predicates are small named fns over `ctx`. `authenticated?` checks
for a current user; ownership predicates verify the entity belongs to the caller:

```clojure
(defn authenticated? [ctx]
  (some? (:current-user/id ctx)))

(defn can-capture-task?
  [{{:keys [project-id]} :command :as ctx}]
  (and (authenticated? ctx)
       (or (nil? project-id)
           (owns-project? ctx project-id))))

(defn can-use-task?
  [{{:keys [task-id]} :command :as ctx}]
  (owns-task? ctx task-id))   ;; verifies the task is in the caller's projection
```

```clojure
;; Public — ONLY for non-account-scoped actions (sign-up / login)
(defcommand :user sign-up
  {:authorized? (constantly true)}
  "..." [ctx] ...)

;; Requires login — the default for anything per-user/account-scoped
(defcommand :todo create-project
  {:authorized? authenticated?}
  "..." [ctx] ...)

;; Ownership check — for mutating an existing per-user entity
(defcommand :todo rename-task
  {:authorized? can-use-task?}
  "..." [ctx] ...)
```

## Key Variations

### Success with Events Only

```clojure
{:command-result/events
 [(->event {:type :todo/task-completed
            :tags #{[:task task-id] [:user user-id]}
            :body {:user-id user-id :task-id task-id}})]}
```

### Success with Events + Datastar Signals

The action handler sends these signals back to the client via SSE patch:

```clojure
{:command-result/events [...]
 :datastar/signals {:__toast "Task added."}}
```

Special signal prefixes:
- `__toast` -- show a brief notification
- `__redirect` -- navigate to a URL
- `__refresh` -- force page refresh

**Do NOT return form-field-reset signals from a command handler.** Clearing an
input (e.g. emptying a `:title` signal after submit) is *UI behavior* and belongs
in the component's effect via `ds-ui/reset-signal`, not in the command result.
Command results carry domain outcomes (`__toast`, `__redirect`), not view state.

### Anomaly Return (Error)

Just return the anomaly. The action handler auto-converts it to `{:error "msg"}`
signal, which `form-error` displays. Schema validation errors also produce
`:fieldErrors` automatically.

```clojure
{::anom/category ::anom/conflict
 ::anom/message "Only active tasks can be completed."}
;; -> auto-converted to signal: {:error "Only active tasks can be completed."}
```

### CAS (Optimistic Concurrency)

Prevents races by checking state hasn't changed between read and write:

```clojure
{:command-result/events [...]
 :command-result/cas {:types task-event-types
                      :predicate-fn (fn [_events]
                                      (= :active (:status (rm/get-task ctx task-id))))}}
```

### Calling Other Commands

```clojure
(let [result (cp/process-command
              (assoc ctx :command {:command/name :todo/complete-task
                                   :task-id task-id}))]
  ;; result is either an anomaly or {:command-result/events [...]}
  result)
```

## Common Anomaly Categories

- `::anom/not-found` -- resource does not exist
- `::anom/conflict` -- business rule violation (duplicate, invalid state)
- `::anom/incorrect` -- bad input data
- `::anom/forbidden` -- not authorized for this action

## Conventions

- Commands modify state by emitting events; never write to a database directly
- Always validate via read models before emitting events
- Read model helpers take `ctx` (the full context), not bare `event-store`
- Tag events with `#{[:task task-id] [:user user-id]}` for filtered reads & ownership
- Return anomalies for all error cases; the action handler converts them to signals
- Include `:user-id` in event bodies for per-user data
- Use `{:authorized? auth-fn}` metadata between the namespace keyword and the docstring
- Never return form-field-reset signals — field reset is UI behavior (`ds-ui/reset-signal`)

## Reference Files

- `src/cjbarre/grain_todo_list/service/user_service/commands.clj` -- User auth commands (sign-up, login, password reset) — best example for auth predicates
- `src/cjbarre/grain_todo_list/service/todo_list_service/commands.clj` -- Todo commands (capture/rename/complete tasks, projects, weekly review)
