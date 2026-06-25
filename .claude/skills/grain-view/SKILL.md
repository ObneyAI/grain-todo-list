---
name: grain-view
description: Build Hiccup view functions for Datastar pages as thin compositions of the app's component library
---

# Grain View Functions

Page functions are **pure functions**: view-model in, Hiccup out. They live in
`cjbarre.grain-todo-list.service.<svc>.ui` (one fn per page) and are **thin compositions of the app's
component library** — the foundation primitives in `cjbarre.grain-todo-list.foundation.ui.components`
plus the service's own domain widgets in `cjbarre.grain-todo-list.service.<svc>.ui.components` (see the
`grain-ui-component` skill). A page should read like a layout outline of named components, not a pile of
raw `flex`/`grid`/`gap` divs. Raw layout in a page = factor a component into the right `ui.components`
namespace instead.

## Core rule
A page is wrapped by `app-ui/app-shell` (which provides the `#app` root + chrome); the query renders it
with `ds-ui/hiccup`. The page fn never reads the event store/context — the query builds the view-model.
Pure UI fns NEVER run commands, mutate state, or touch Integrant.

```clojure
(ns cjbarre.grain-todo-list.service.todo-list-service.queries
  (:require [ai.obney.grain.datastar.ui :as ds-ui]
            [ai.obney.grain.query-processor.interface :refer [defquery]]
            [cjbarre.grain-todo-list.service.todo-list-service.read-models :as rm]
            [cjbarre.grain-todo-list.service.todo-list-service.ui :as ui]))

(defn render [page] (ds-ui/hiccup page))   ; the production lowering boundary

(defquery :todo tasks-page
  {:authorized? authenticated?            ; gate per-user/account-scoped pages with auth
   :datastar/path "/tasks"
   :datastar/title "Tasks"
   :grain/read-models {:todo/tasks 1 :todo/projects 1}}
  [ctx]
  (let [tasks (vec (rm/active-tasks ctx))
        projects (vec (rm/active-project-summaries ctx))]
    {:query/result {:tasks tasks :projects projects}
     :datastar/hiccup (render (ui/tasks-page {:tasks tasks :projects projects}))}))
```

## A page is a composition

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

## Forms & signals (the REAL `ds-ui` DSL)
Pattern from the canonical todo-service widgets — use this, not `action-form`/`data-bind`:

```clojure
(ds-ui/with-signals [title {:init ""}]
  [:form {:on/submit {:effect (ds-ui/effects
                                (ds-ui/clear-errors)
                                (ds-ui/dispatch :todo/capture-task {:title (ds-ui/trimmed title)})
                                (ds-ui/reset-signal title))
                      :modifiers {:prevent true}}}
   [:input {:class "input input-bordered w-full" :aria-label "Task title"
            :required true :bind/value title}]
   (c/action-button {:label "Add" :class "btn-primary" :type "submit"})])
```

Inline edits coordinate keys + blur (see `task-title-edit`/`project-editor`):

```clojure
{:on/blur {:effect (ds-ui/choose-effect
                     (ds-ui/present? (ds-ui/trimmed draft))
                     (ds-ui/when-effect
                       (ds-ui/changed? (ds-ui/trimmed draft) title)
                       (ds-ui/dispatch :todo/rename-task {:task-id id :title (ds-ui/trimmed draft)}))
                     (ds-ui/set-signal draft title))}
 :on/keydown {:effect (ds-ui/on-keys {"Enter"  (ds-ui/blur)
                                      "Escape" (ds-ui/effects (ds-ui/set-signal draft title)
                                                              (ds-ui/blur))})}}
```

Key `ds-ui`: `with-signals`; `:bind/value`/`:bind/show`/`:bind/text`/`:bind/class`/`:bind/attr`/`:bind/prop`;
`dispatch` (command), `refresh` (query), `effects`, `when-effect`, `choose-effect`, `on-keys`,
`set-signal`, `reset-signal`, `clear-errors`, `blur`; `(ds-ui/href :todo/page)` (resolve query → URL —
NEVER literal app hrefs); expressions `trimmed`/`num`/`present?`/`changed?`/`evt`/`indexed`; `(ds-ui/js …)`
as an escape hatch only.

Errors are adapter-owned via the `error` signal — use `app-ui/action-error` (the banner), or inline:
`[:div {:class "alert alert-error" :bind/show (ds-ui/js "$error")} [:span {:bind/text (ds-ui/js "$error")}]]`.

> **Beyond a simple form/list — modals/dialogs, contenteditable, live-updating UI, multi-signal coordination?**
> Read grain's authoritative **Datastar UI DSL reference** FIRST — it ships as **`docs/datastar-ui.md`** in the
> **grain-datastar** dependency (it has a *Code Agent Checklist* plus the signal→SSE→patch model, lowering, and
> the gotchas). **Locate it dynamically** in the Clojure git-deps cache (honor `$GITLIBS`, else `$HOME/.gitlibs`):
> `find "${GITLIBS:-$HOME/.gitlibs}" -path '*grain-datastar*/docs/datastar-ui.md' | head -1`. Don't reverse-engineer the DSL from
> library source.

## Layout & spacing standards (the bar every screen must clear)
- Section headings (`c/section-heading`, `c/page-section`, `c/panel`): title/status left, count/action
  right — never jam a button into a heading.
- One gap scale between sections (`gap-6`/`space-y-6`); padded surfaces (the `surface` variants encode it).
- Rows: `items-center justify-between` — label left, status/value/actions right; no floating values.
- Hierarchy via size + weight + color (page title → section titles → body → muted metadata).
- Intentional empty states via `c/empty-state`.

After building, run the **`design-review`** skill on each primary screen and refine the component-library
fns until the screen passes the rubric.

## Test
Run `clojure -T:build test` (there is no `-M:poly`).

## Reference files
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui.clj` — canonical page composition (app-shell,
  action-error, thin pages calling components).
- `src/cjbarre/grain_todo_list/service/todo_list_service/ui/components.clj` — domain widgets, forms,
  inline edits, and `ds-ui` signal/effect patterns.
- `grain-ui-component` skill — how to build/compose the foundation + service component libraries.
- `src/cjbarre/grain_todo_list/foundation/ui/components.clj` — foundation primitives.
- `src/cjbarre/grain_todo_list/foundation/ui.clj` — `app-shell` + `action-error`.
