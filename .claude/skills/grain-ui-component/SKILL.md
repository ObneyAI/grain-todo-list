---
name: grain-ui-component
description: Build the app's reusable UI component library (its visual language) on the foundation.ui primitives + DaisyUI, and compose screens from it
---

# Grain UI: build a reusable component library, compose screens from it

Server-rendered Hiccup over Datastar (SSE) — no SPA. **UI is developed library-first:** you grow the
app's own reusable component library (its visual language) and every screen is a *thin composition* of
those components. Never hand-roll one-off layout in a page (`ui.clj`) — if you're writing raw flex/grid/spacing
in a page function, that's the signal to factor a component.

In this compact (non-Polylith) app the library is split in two layers, and you grow **both**:
- **App-wide primitives** live in `cjbarre.grain-todo-list.foundation.ui.components` — the closest thing
  to a "ui-kit". These are domain-agnostic visual pieces reused everywhere.
- **Domain widgets** live in each service's own `cjbarre.grain-todo-list.service.<svc>.ui.components`
  (e.g. `service.todo-list-service.ui.components`). These compose the foundation primitives into
  domain-specific pieces (task cards, project rows, review controls, …).

Why: consistency becomes structural (the look is defined once), and refining a component propagates
everywhere. This is what keeps screens from drifting into cramped, misaligned, inconsistent layouts.

## What actually exists (use the REAL API)

**Foundation shell — `cjbarre.grain-todo-list.foundation.ui` (`:as app-ui`):**
`app-shell` (the `#app` root + page chrome) and `action-error` (the adapter-owned error banner).

**Foundation primitives — `cjbarre.grain-todo-list.foundation.ui.components` (`:as c`):**
`surface` (+ `surface-class`), `action-button`, `text-field`, `select-field`, `chip`, `chip-row`,
`badge`, `badge-row`, `status-badges`, `status-action`, `empty-state`, `page-section`, `panel`,
`panel-stack`, `section-heading`, `section-title`, `page-title`, `product-label`, `metadata-text`,
`form-class`. These are the reusable building blocks — grow this namespace when a piece is app-wide.

**Datastar — the checked `ds-ui` DSL (`ai.obney.grain.datastar.ui :as ds-ui`):**
- Signals: `(ds-ui/with-signals [title {:init ""}] …)` — options `:init`, `:name`, `:stable? true`.
- Bindings (in the attr map): `:bind/value`, `:bind/text` (`(ds-ui/trimmed title)`), `:bind/show`
  (`(ds-ui/js "$error")`), `:bind/class`, `:bind/attr {:disabled saving?}`, `:bind/prop {:checked sel?}`,
  `:morph/ignore true`.
- Events: `:on/submit`/`:on/click`/`:on/change`/`:on/blur`/`:on/keydown`/`:on/signal-patch` →
  `{:effect … :modifiers {:prevent true}}`.
- Effects: `(ds-ui/dispatch :todo/command {:k sig})` (command), `(ds-ui/refresh :todo/page)` (query),
  `(ds-ui/effects …)`, `(ds-ui/when-effect …)`, `(ds-ui/choose-effect …)`, `(ds-ui/on-keys {…})`,
  `(ds-ui/set-signal s v)`, `(ds-ui/reset-signal s)`, `(ds-ui/clear-errors)`, `(ds-ui/blur)`,
  `(ds-ui/href :todo/page)` (resolve a query → URL; NEVER write literal app hrefs), `(ds-ui/js …)`
  (escape hatch only).
- Expressions: `ds-ui/trimmed`, `ds-ui/num`, `ds-ui/num-cents`, `ds-ui/present?`, `ds-ui/changed?`,
  `ds-ui/evt`, `ds-ui/indexed`.
- A page function returns Hiccup wrapped by `app-ui/app-shell`; the query renders it with `(ds-ui/hiccup …)`.

> Do NOT use `platform-shell`, `data-table`, `action-form`, `form-field`, `auth-card`, `modal`, raw
> `data-on-click`/`data-bind` strings, or a `pattern-compendium.md` — **none of these exist in this
> project.** The canonical, working references are
> `src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj` (domain widgets, forms,
> inline edits) and `src/cjbarre/grain_todo_list/foundation/ui/components.clj` (the primitives).

> **Beyond a simple form/list — modals/dialogs, contenteditable, live-updating UI, multi-signal coordination?**
> Read grain's authoritative **Datastar UI DSL reference** FIRST — it ships as **`docs/datastar-ui.md`** in the
> **grain-datastar** dependency (it has a *Code Agent Checklist* plus the signal→SSE→patch model, lowering, and
> the gotchas). **Locate it dynamically** in the Clojure git-deps cache (honor `$GITLIBS`, else `$HOME/.gitlibs`):
> `find "${GITLIBS:-$HOME/.gitlibs}" -path '*grain-datastar*/docs/datastar-ui.md' | head -1`. Don't reverse-engineer the DSL from
> library source.

## Grow the component library

There is no separate component to load — the namespaces already exist. Just add functions:
- **App-wide** → add to `cjbarre.grain-todo-list.foundation.ui.components`.
- **Domain-specific** → add to the service's `cjbarre.grain-todo-list.service.<svc>.ui.components`
  (it `(:require [...foundation.ui.components :refer [...]])` and composes those primitives).

After editing namespaces, reload via your nREPL (`require ... :reload`). If you changed the running
system, restart it with `(app/stop app)` then `(def app (app/start))` — there is no `load-component!`/`restart!`.

Pure UI fns only: receive data, return Hiccup / checked DSL Hiccup. **Never** read the event store,
mutate state, run commands, or touch Integrant from a UI fn. The query layer is the only place that
builds the view-model and lowers it with `(ds-ui/hiccup view)`.

## Reference blueprints (real API)

App-wide primitive added to `foundation.ui.components`:

```clojure
(ns cjbarre.grain-todo-list.foundation.ui.components)

;; Stat tile built from surface.
(defn stat-card [label value sub]
  (surface {:variant :card}
    [:p {:class "text-xs font-medium uppercase tracking-wide text-base-content/60"} label]
    [:p {:class "mt-1 text-2xl font-semibold tracking-tight"} value]
    (when sub [:p {:class "mt-0.5 text-sm text-base-content/60"} sub])))
```

Domain widgets in `service.todo-list-service.ui.components` (compose the primitives + `ds-ui`):

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.ui.components
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.foundation.ui.components
             :refer [action-button empty-state form-class surface surface-class]]))

;; A form wired to a signal — note form-class wraps the form in the :form surface styling.
(defn quick-add [{:keys [project-id]}]
  (form-class
   (ds-ui/with-signals [title {:init ""}]
     [:form {:on/submit {:effect
                         (ds-ui/effects
                          (ds-ui/dispatch :todo/capture-task
                                          (cond-> {:title (ds-ui/trimmed title)}
                                            project-id (assoc :project-id project-id)))
                          (ds-ui/reset-signal title))
                         :modifiers {:prevent true}}}
      [:input {:class "input input-bordered min-w-0 flex-1"
               :aria-label "Task title" :placeholder "Add a task"
               :required true :bind/value title}]
      (action-button {:label "Add" :class "btn-primary" :type "submit"})])
   (surface-class :form nil)))

;; A list = a :list surface of rows; an empty list = an intentional empty-state.
(defn task-list [tasks empty-message projects]
  (if (seq tasks)
    [:div {:class "space-y-3"}
     (for [task tasks]
       (with-meta (task-card task projects) {:key (:task-id task)}))]
    (empty-state empty-message)))
```

## Composing a screen (thin — just calls the library)

A page function lives in `service.<svc>.ui` and only arranges named components:

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.ui
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [cjbarre.grain-todo-list.service.todo-list-service.ui.components :as tc]
            [cjbarre.grain-todo-list.foundation.ui :as app-ui]
            [cjbarre.grain-todo-list.foundation.ui.components :as c]))

(defn tasks-page [{:keys [tasks projects]}]
  (app-ui/app-shell {:title "Tasks"}
    (app-ui/action-error)
    [:div {:class "mb-6"} (tc/quick-add {})]
    (tc/task-list tasks "No active tasks." projects)))
```

## Layout & spacing standards (every screen must hold these)
- **Header:** the `app-shell` provides the product label + page title; section headings (`section-heading`,
  `page-section`, `panel`) put a title/status LEFT and a count/action RIGHT — never jam a button into a heading.
- **Rhythm:** ONE gap scale between sections (e.g. `gap-6`/`space-y-6`); padded surfaces (the `surface`
  variants already encode `p-4`–`p-5`).
- **Rows:** `items-center justify-between` — label left, status/value/actions right; no floating values.
- **Hierarchy:** size + weight + color (page title → section titles → body → muted `text-base-content/60`).
- **Consistency:** uniform surface/button/radius/gaps — automatic when you compose the same components.
- **Empty states:** intentional via `empty-state` (title + hint), never blank.

## Dev gallery (visual specimens)
Render static specimens of components in the dev gallery so the visual language is reviewable. Strip
interactivity from specimens with `(ds-ui/static (ds-ui/ir node) {:strip-href? true :strip-raw-events? true})`
(see the `static-specimen`/`inert` helpers and `dev-gallery-page` in the todo service's `ui/components.clj`).

After building, run the **`design-review`** skill on each primary screen and refine the components until
they pass. A screen isn't done until it composes from the foundation + service component libraries and
passes the visual rubric.

Prefer ordinary pure fns for components — `ds-ui/defcomponent` is rarely needed in this teaching app and
plain fns read more clearly.

## CSS build
Tailwind scans `src/**/*.clj`. After adding utility classes, rebuild with `npm run css:build`, which
writes `resources/public/css/main.css`.

## Reference files
- `src/cjbarre/grain_todo_list/foundation/ui/components.clj` — the app-wide primitives (read for exact
  signatures: `surface`, `action-button`, `chip`, `badge`, `panel`, `page-section`, …).
- `src/cjbarre/grain_todo_list/foundation/ui.clj` — `app-shell` + `action-error`.
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj` — the canonical real example
  (domain widgets, forms, inline edits via `text-field`, `ds-ui` signal/effect patterns, gallery specimens).
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj` — thin page composition.
