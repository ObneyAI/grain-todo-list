---
name: grain-query-handler
description: Implement query handlers that read from event-sourced read models and render hiccup
---

# Grain Query Handler Pattern

## Required Imports

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.queries
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.service.todo-list-service.ui :as ui]))
```

Every page query lowers its view to HTML through a small `render` helper backed
by `ds-ui/hiccup`:

```clojure
(defn render [page]
  (ds-ui/hiccup page))
```

## Query Template

A page query declares its route, title, and the read models it depends on, then
returns both a `:query/result` and rendered `:datastar/hiccup`:

```clojure
(defquery :todo tasks-page
  {:authorized? authenticated?
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [tasks (vec (rm/active-tasks ctx))]
    {:query/result {:tasks tasks}
     :datastar/hiccup (render (ui/tasks-page {:tasks tasks}))}))
```

The home page is just the query mounted at `/`:

```clojure
(defquery :todo home-page
  {:authorized? authenticated?
   :datastar/path "/"
   :datastar/title "Grain Todo"
   :grain/read-models {:todo/tasks 1 :todo/projects 1 :todo/weekly-review 1}}
  "Main todo workspace."
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (render (ui/home-page data))}))
```

## Return Patterns

### Success
```clojure
{:query/result {:tasks tasks :projects projects}
 :datastar/hiccup (render (ui/tasks-page {:tasks tasks :projects projects}))}
```

### Error
```clojure
{::anom/category ::anom/not-found
 ::anom/message "Human readable message"}
```

## Reading Query Parameters

**Routes use query params, NEVER path params.** Declare a static
`:datastar/path` and read params from `(:query ctx)`:

```clojure
(defquery :todo task-page
  {:authorized? owns-task-query?
   :datastar/path "/task"          ;; NOT "/task/:task-id"
   :datastar/title "Task"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [task-id]} :query :as ctx}]   ;; from /task?task-id=<uuid>
  (let [task (get (rm/all-tasks ctx) task-id)]
    {:query/result {:task task}
     :datastar/hiccup (render (ui/task-page {:task task}))}))
```

## Fat Query Pattern (Screen Queries)

Load all data a screen needs in one query, usually via a shared data-builder fn:

```clojure
(defn workspace-data [ctx]
  {:tasks    (vec (rm/active-tasks ctx))
   :due-soon (vec (rm/due-soon-tasks ctx))
   :projects (vec (rm/active-project-summaries ctx))
   :review   (rm/current-weekly-review ctx)})

(defquery :todo review-page
  {:authorized? authenticated?
   :datastar/path "/review"
   :datastar/title "Weekly Review"
   :grain/read-models {:todo/tasks 1 :todo/projects 1 :todo/weekly-review 1}}
  [ctx]
  (let [data (workspace-data ctx)]
    {:query/result data
     :datastar/hiccup (render (ui/review-page data))}))
```

## Composing Read Models

Enrich one read model with data from another inside the query:

```clojure
(defquery :todo project-page
  {:authorized? owns-project-query?
   :datastar/path "/project"
   :datastar/title "Project"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [{{:keys [project-id]} :query :as ctx}]
  (let [project (get (rm/all-projects ctx) project-id)
        tasks   (vec (rm/tasks-for-project ctx project-id))
        project (when project
                  (assoc project :task-counts (rm/project-task-counts ctx project-id)))]
    {:query/result {:project project :tasks tasks}
     :datastar/hiccup (render (ui/project-page {:project project :tasks tasks}))}))
```

## Event-Driven Re-rendering

Page queries are **event-driven**. Declare `:grain/read-models {<rm> <version>}`
so the page re-renders only when those read models change — no polling.

- **Do not use `:datastar/fps` for normal state.** Omitting both
  `:grain/read-models` and `:datastar/fps` silently defaults to 30fps polling.
- The only sanctioned use of `:datastar/fps` is `:datastar/fps 0` for a
  genuinely static page (e.g. the dev gallery), which disables re-rendering
  entirely:

```clojure
(defquery :todo dev-gallery-page
  {:authorized? (constantly true)
   :datastar/path "/dev/gallery"
   :datastar/title "Dev UI Gallery"
   :datastar/fps 0}                ;; static page — no re-render
  [_ctx]
  {:query/result {}
   :datastar/hiccup (render (ui/dev-gallery-page))})
```

## Authorization

Public/landing pages may use `:authorized? (constantly true)`. Any query reading
**per-user / account-scoped** data uses a real predicate. This repo uses:

- `authenticated?` — requires a current user
- `owns-task-query?` / `owns-project-query?` — verify the queried entity belongs
  to the caller (they read the param from `(:query ctx)` and check the
  projection)

```clojure
(defn owns-task-query? [ctx]
  (and (authenticated? ctx)
       (if-let [task-id (get-in ctx [:query :task-id])]
         (contains? (rm/all-tasks ctx) task-id)
         true)))
```

## Conventions

- **Routes use query params, NEVER path params** — `:datastar/path "/task"` + read `(:task-id (:query ctx))` from `/task?task-id=<uuid>`
- **Pages are EVENT-DRIVEN** — declare `:grain/read-models {<rm> <version>}`; never `:datastar/fps` for normal state (only `:datastar/fps 0` for static pages)
- **Hybrid auth** — `(constantly true)` only for public pages; per-user data uses `authenticated?` / ownership predicates
- Every query lowers its view through `render`/`ds-ui/hiccup`
- Queries are read-only (never emit events)
- Use read models (helpers taking `ctx`) to get current state
- Return `{:query/result {...} :datastar/hiccup (render ...)}` for pages
- Use fat queries (a shared data-builder fn) for screen data to reduce round trips

## Reference Files

- `src/cjbarre/grain_todo_list/service/todo_list_service/queries.clj` - Todo page queries (home, tasks, task, projects, project, review, dev gallery)
- `src/cjbarre/grain_todo_list/service/user_service/queries.clj` - Auth pages (good example of `:authorized?` predicates and `:datastar/fps 0`)
