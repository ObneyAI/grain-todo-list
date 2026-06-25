---
name: grain-schema
description: Define Malli schemas for commands, events, and queries using defschemas
---

# Grain Schema Pattern

## Required Imports

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))
```

## Schema Registration

Use `defschemas` to register named Malli schemas:

```clojure
(defschemas schema-group-name
  {:schema-key schema-definition
   :another-key another-definition})
```

This repo groups schemas into named `defschemas` groups: `common-schemas`,
`event-schemas`, `command-schemas`, `query-schemas`, and `read-model-schemas`.

## Command Schemas

Naming: `:namespace/verb` (verb form)

```clojure
(defschemas command-schemas
  {;; Create command
   :todo/capture-task
   [:map
    [:title ::non-blank-string]
    [:task-id {:optional true} :uuid]
    [:project-id {:optional true} :uuid]
    [:order {:optional true} ::order]]

   ;; Update command
   :todo/rename-task
   [:map
    [:task-id :uuid]
    [:title ::non-blank-string]]

   ;; Action command
   :todo/start-weekly-review
   [:map
    [:review-id {:optional true} :uuid]]})
```

## Event Schemas

Naming: `:namespace/entity-past-tense` (what happened)

```clojure
(defschemas event-schemas
  {;; Created event
   :todo/task-captured
   [:map
    [:user-id :uuid]
    [:task-id :uuid]
    [:title ::non-blank-string]
    [:status ::task-status]
    [:order ::order]
    [:project-id {:optional true} :uuid]]

   ;; Renamed event
   :todo/task-renamed
   [:map
    [:user-id :uuid]
    [:task-id :uuid]
    [:title ::non-blank-string]]

   ;; Action completed event
   :todo/weekly-review-started
   [:map
    [:user-id :uuid]
    [:review-id :uuid]
    [:started-at :time/offset-date-time]]})
```

## Query Schemas

Naming: `:namespace/query-name` (the page or data being requested)

```clojure
(defschemas query-schemas
  {:todo/home-page [:map]
   :todo/tasks-page [:map]
   :todo/task-page [:map [:task-id :uuid]]
   :todo/projects-page [:map]
   :todo/project-page [:map [:project-id :uuid]]})
```

## Read Model State Schemas

Read-model **state schemas** are declared in their own `read-model-schemas`
group. The schema describes the shape of the projected state (typically a map
keyed by entity id):

```clojure
(defschemas read-model-schemas
  {:todo/tasks    [:map-of :uuid ::task]
   :todo/projects [:map-of :uuid ::project]})
```

## Common Malli Types

```clojure
:string                              ;; String
:int                                 ;; Integer
:double                              ;; Float
:boolean                             ;; Boolean
:uuid                                ;; UUID
:keyword                             ;; Keyword
:any                                 ;; Any value

[:enum :a :b :c]                     ;; Enum
[:set :string]                       ;; Set of strings
[:vector :uuid]                      ;; Vector of UUIDs
[:map-of :keyword :any]              ;; Map with keyword keys

[:map                                ;; Map with specific keys
 [:required-key :string]
 [:optional-key {:optional true} :int]]

[:re #"^[a-z]+$"]                    ;; Regex pattern
```

## Custom Type Registration

```clojure
(defschemas custom-types
  {:email [:re #"^[^@\s]+@[^@\s]+\.[^@\s]+$"]
   :phone [:re #"^\d{10,15}$"]
   :encrypted [:map
               [:ciphertext :string]
               [:encrypted-key :string]
               [:iv :string]]})
```

## Reusable Sub-Schemas

Common schemas can be referenced by qualified keyword (`::name`) from other
groups. In this repo the `common-schemas` group holds these reusable pieces:

```clojure
(defschemas common-schemas
  {::non-blank-string [:fn {:error/message "Must not be blank"} non-blank-string?]
   ::task-status [:enum :active :completed :canceled :archived]
   ::order number?
   ::task [:map
           [:user-id :uuid]
           [:task-id :uuid]
           [:title ::non-blank-string]
           [:status ::task-status]
           [:order ::order]
           [:project-id {:optional true} :uuid]]})

(defschemas event-schemas
  {:todo/task-captured ::task})   ;; reuse the ::task shape directly
```

## Conventions

- Group schemas by category: `common-schemas`, `event-schemas`, `command-schemas`, `query-schemas`, `read-model-schemas`
- Commands: `:namespace/verb` (capture, rename, complete, start, etc.)
- Events: `:namespace/entity-past-tense` (captured, renamed, started)
- Queries: `:namespace/...-page` (one per page/screen)
- Use `{:optional true}` for optional fields
- Define reusable sub-schemas in `common-schemas` and reference via `::name`
- Match event schemas to what `->event` produces
- Declare read-model state shapes in `read-model-schemas`

## Reference Files

- `src/cjbarre/grain_todo_list/service/todo_list_service/schemas.clj` - Todo schemas (tasks, projects, weekly review)
- `src/cjbarre/grain_todo_list/service/user_service/schemas.clj` - User/auth schemas
