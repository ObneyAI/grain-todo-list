---
name: test
description: Run the project test suite with the build tool's test task
---

# Run Tests

Run the test suite from the project root.

## Command

```
clojure -T:build test
```

This is defined in `build.clj` and runs the Cognitect test-runner over the `test/`
directory.

## How to run it

Run it from the **shell**, NOT by loading test namespaces over nREPL — requiring a test
ns into the live image hits stale `__init.class` errors. The test runner spins up a fresh
process, so it always sees your current source.

A fresh seed has no tests yet, so "No tests to run" / a zero-test summary is expected
until you've written some. Test files live under `test/`, e.g.
`test/cjbarre/grain_todo_list/service/user_service_test.clj`.

## Lint

```
clj-kondo --lint src test
```

## After running

Report the test summary (passes, failures, errors). If there are failures, show the
failing test names and assertion details.
