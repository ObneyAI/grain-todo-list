# Agent Instructions

This is a teaching project for the Grain Sessions video series. Keep the code
small, direct, and useful for demonstrating Grain patterns.

## Project Shape

- Use the existing compact Clojure layout. Do not introduce Polylith `bases/`,
  `components/`, `interface.clj`, or service bricks.
- Keep application composition in `src/cjbarre/grain_todo_list.clj`: Integrant
  system, routes, lifecycle, and service registration.
- Keep app-wide shell and reusable UI primitives in
  `src/cjbarre/grain_todo_list/foundation/ui.clj` and
  `src/cjbarre/grain_todo_list/foundation/ui/components.clj`.
- Keep todo domain behavior under
  `src/cjbarre/grain_todo_list/service/todo_list_service/`: schemas, read models,
  commands, queries, processors, periodic tasks, and page UI.
- Use [doc/pattern-compendium.md](doc/pattern-compendium.md) as the detailed
  architecture reference before adding new patterns.

## Grain And Datastar

- Prefer Grain interfaces already used in the repo over introducing new local
  wrappers.
- Define command, event, query, and read-model schemas near the todo service
  schema definitions.
- Keep command handlers focused on validation and event emission.
- Keep query handlers focused on assembling read-model data and returning
  Datastar Hiccup.
- UI functions should be pure: receive data and return Hiccup. Do not read the
  event store, mutate state, run commands, or access Integrant components from
  page rendering functions.
- Use Datastar adapter helpers such as `ds/routes`, `ds/action-form`,
  `ds/on-click-command`, and related DSL helpers instead of building Datastar
  attributes by hand.
- Use Grain Code Agent Tools via nrepl to inspect the grain application efficiently.
- Eval relevant files via nrepl to allow hot reloading code without restarting the app.

## UI And CSS

- Match the existing Tailwind and DaisyUI style.
- Put app-level reusable Hiccup primitives in the app UI component namespace.
- Put todo-specific controls, lists, cards, review widgets, and gallery
  specimens in the todo service UI component namespace.
- If UI class names change, rebuild CSS with `npm run css:build`.

## Verification

- For behavior changes, run focused Clojure tests with `clojure -T:build test`.
- For Clojure lint checks, use `clj-kondo --lint src test` when available.
- For UI or CSS changes, run `npm run css:build`.
- Do not commit generated or unrelated changes unless the user explicitly asks.
