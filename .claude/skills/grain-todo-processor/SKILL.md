---
name: grain-todo-processor
description: Implement event-driven side effects that react to domain events
---

# Grain Todo Processor Pattern

Todo processors are policies that react to domain events and perform side effects
(send email, call an external API) and/or chain follow-up commands. In this compact
app they are declared with the `defprocessor` macro and register **globally** the
moment their namespace is required — there is no registry map and no per-component
Integrant wiring. To activate a new processor you (1) write a `defprocessor` form and
(2) add a side-effect `require` of its namespace from the root app ns
`cjbarre.grain-todo-list` (file `src/cjbarre/grain_todo_list.clj`).

The root system already runs a tenant poller (the `::processors` Integrant key calls
`tp/start-tenant-poller`), so a new service does **not** start its own poller — it just
adds `defprocessor` forms and a require.

## Required Imports

```clojure
(ns cjbarre.grain-todo-list.service.<svc>-service.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :refer [defprocessor]]
            [cjbarre.grain-todo-list.foundation.email :as email]))
```

When a processor chains a follow-up command, also require:

```clojure
[ai.obney.grain.command-processor-v2.interface :as cp]
```

## Processor Template

`defprocessor` takes a processor *prefix* keyword (the service domain, e.g. `:user`,
`:todo`), a name symbol, an options map containing `:topics` (a **set** of event
types it reacts to), a single-arg binding vector that destructures `:event` plus any
context keys, and a body that returns a result map.

```clojure
(defprocessor :todo processor-name
  {:topics #{:todo/triggering-event}}
  [{{:keys [todo-id]} :event
    :keys [event-store email-client]}]
  {:result/effect
   (fn []
     ;; side effect runs here (email, external call, chained command)
     ...)
   :result/checkpoint :after})
```

Key points:

- The prefix + name form the processor's identity; reusing the same prefix/name
  redefines it (REPL-friendly).
- `:topics` is a **set** of event types. A processor fires once per matching event.
  One processor can react to several topics, and several processors can react to the
  same topic.
- The binding destructures the triggering `:event` and whatever context keys the
  processor needs (e.g. `:event-store`, `:email-client`, `:email-from`, `:app-base`).
- Return `{:result/effect (fn [] ...) :result/checkpoint :after}`. The `:result/effect`
  thunk performs the side effect. `:result/checkpoint :after` means the effect runs
  **after** the triggering event has been durably checkpointed (at-least-once: the
  effect only fires once the event is safely recorded).

## Real Example — send email on a domain event

From `src/cjbarre/grain_todo_list/service/user_service/todo_processors.clj`:

```clojure
(defprocessor :user email-verification-email
  {:topics #{:user/email-verification-requested}}
  [{{:keys [email-address verification-token]} :event
    :keys [app-base email-client email-from]}]
  {:result/effect
   (fn []
     (email/send
      email-client
      {:from email-from
       :to [email-address]
       :subject "Verify your Grain Todo email"
       :body-html (format "<p>Verify: <a href=\"%s/auth/verify-email?verification-token=%s\">Verify email</a></p>"
                          app-base verification-token)}))
   :result/checkpoint :after})
```

## Chaining a follow-up command

To emit a follow-up command from inside the effect, call `cp/process-command` with
the context plus a `:command` map:

```clojure
(defprocessor :todo notify-on-completed
  {:topics #{:todo/item-completed}}
  [{{:keys [todo-id]} :event :as context}]
  {:result/effect
   (fn []
     (cp/process-command
      (assoc context :command {:command/id (random-uuid)
                               :command/name :todo/record-completion-notice
                               :todo-id todo-id})))
   :result/checkpoint :after})
```

## Activation — require from the root ns

Processors only register when their namespace is loaded, so add a side-effect require
to `src/cjbarre/grain_todo_list.clj` (no alias needed — loading the ns is the point):

```clojure
;; in the (ns cjbarre.grain-todo-list ...) :require block
[cjbarre.grain-todo-list.service.<svc>-service.todo-processors]
```

The root system's `::processors` key already starts the tenant poller via
`tp/start-tenant-poller`, which dispatches polled events to every registered
`defprocessor` whose `:topics` match. After adding/changing a processor, restart the
system so the new namespace is loaded and the poller picks it up:

```clojure
(app/stop app)
(def app (app/start))
```

## Optional: per-service poller lifecycle

A service *can* expose its own poller lifecycle instead of relying on the root one —
a `todo_processors.clj` (or sibling) file that wraps the todo-processor interface's
`start-tenant-poller` / `stop-tenant-poller`. This is what the root system does, and
a service rarely needs its own. Reference:
`src/cjbarre/grain_todo_list/service/todo_list_service/todo_processors.clj`:

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.todo-processors
  (:require [ai.obney.grain.todo-processor-v2.interface :as tp]))

(defn start [{:keys [event-store cache tenant-id]}]
  (tp/start-tenant-poller
   {:event-store event-store
    :tenant-ids #{tenant-id}
    :context {:cache cache}
    :poll-interval-ms 250}))

(defn stop [poller]
  (tp/stop-tenant-poller poller))
```

If you add such a lifecycle, wire its `start`/`stop` behind a new Integrant key in
`src/cjbarre/grain_todo_list.clj`. Usually you don't — just add `defprocessor` forms
and the require above.

## Conventions

- Processors react to a **set** of `:topics`; the body destructures `:event` + context.
- Return `{:result/effect (fn [] ...) :result/checkpoint :after}` — `:after` runs the
  effect after the event is durably checkpointed (at-least-once delivery).
- Put side effects (email, external I/O) and command chaining inside the effect thunk,
  not at processor-definition time.
- Use the service domain prefix for the processor keyword (`:todo`, `:user`, ...).
- Normalize data before comparison (email lowercase, phone digits only).

## Reference Files

- `src/cjbarre/grain_todo_list/service/user_service/todo_processors.clj` — `defprocessor` email side effects
- `src/cjbarre/grain_todo_list/service/todo_list_service/todo_processors.clj` — optional poller `start`/`stop` lifecycle
- `src/cjbarre/grain_todo_list.clj` — root ns; the `::processors` key and the side-effect requires that activate processors
