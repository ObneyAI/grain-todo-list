# grain-todo-list

A small Grain + Datastar todo app experiment.

The project intentionally avoids a Polylith layout. Application code should stay small and local:

- `src/cjbarre/grain_todo_list.clj` - backend, Integrant system, Grain primitives
- `src/cjbarre/grain_todo_list/ui.clj` - pure Hiccup UI
- `css/main.css` - Tailwind/DaisyUI input
- `resources/public/css/main.css` - generated CSS served by the app

## Requirements

- Clojure CLI
- Node.js and npm

The `:dev` alias is required when starting the app because LMDB needs Java module opens.

## Install Frontend Dependencies

```sh
npm install
```

## Build CSS

One-shot build:

```sh
npm run css:build
```

Watch mode while developing UI:

```sh
npm run css:dev
```

The CSS build writes to `resources/public/css/main.css`.

## Run The App

Start an nREPL:

```sh
./scripts/nrepl.sh
```

Then evaluate:

```clojure
(require '[cjbarre.grain-todo-list :as app])
(def system (app/start))
```

Open:

```text
http://localhost:8080/
```

Stop the app:

```clojure
(app/stop system)
```

## Development Loop

Typical local loop:

```sh
npm run css:dev
```

In another terminal:

```sh
./scripts/nrepl.sh
```

Then work from the REPL. Reload namespaces after edits and restart the Integrant system as needed:

```clojure
(require 'cjbarre.grain-todo-list.ui :reload)
(require 'cjbarre.grain-todo-list :reload)

(app/stop system)
(def system (app/start))
```

## Tests And Checks

Run tests:

```sh
clojure -T:build test
```

Run clj-kondo:

```sh
clj-kondo --lint src test
```

## Notes

- Datastar pages are generated from Grain query metadata via `ds/routes`.
- The home page is currently a minimal smoke-test page at `/`.
- For Datastar or frontend issues, check the Datastar version in use and read the Grain Datastar adapter source before guessing.
