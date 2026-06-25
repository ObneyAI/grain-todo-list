---
name: verify-ui
description: Verify a build's acceptance checks against the LIVE app — the correct Playwright MCP snapshot→ref→act contract, signing in to an auth app, and when to assert via nREPL instead of the browser. Use whenever you verify acceptance as a real user (Definition of done #6).
---

# Verify the app as a user

Acceptance is the contract. Verify each acceptance check against the **live** app — but spend browser
turns wisely: the browser is slow and the Playwright MCP tools are easy to mis-call.

## 1. Assert DATA over nREPL, not the browser
Anything checkable as data is faster and deterministic over `code-agent-tools` — no clicking:
```clojure
(tools/projection :your/read-model)        ; did it save? pinned? newest-first? folder moved?
(tools/events {:types #{:your/event}})     ; did the command land?
```
Use **Playwright only for genuinely visual / interaction** behavior: does clicking *compose* open a
blank editor, does the list re-render live without a refresh, empty states, layout/design.

## 2. Playwright MCP contract — snapshot first, act by ref **and** target
NEVER guess element targets. Each interaction is **snapshot → read the exact `ref` → act**:

1. `browser_snapshot {}` → the accessibility tree; each element has a `ref` (e.g. `"e15"`).
2. **Type** — `browser_type` needs **all four**: `element`, `ref`, **`target`**, `text`.
   - `element` = human description (`"Email textbox"`), `ref` = the snapshot ref (`"e15"`),
     `target` = the **same ref id** (`"e15"`), `text` = what to type.
   - ✅ `{"element":"Email textbox","ref":"e15","target":"e15","text":"a@b.dev"}`
   - ❌ `{"element":"Email textbox","ref":"e15","text":"a@b.dev"}` — **missing `target`** → fails with
     *"expected string, received undefined → at target"*. This is the #1 wasted-turn error. Always pass `target`.
3. **Click** — `browser_click {element, ref, target}`. `target` may be the ref (`"e37"`) **or** a CSS
     selector (`"button:has-text(\"Save\")"`). Always include it.
4. **Re-snapshot after any DOM change** before the next ref-based action — refs go stale.

## 3. Prefer `browser_type` over `browser_fill_form`
`browser_fill_form` is finicky — every field needs a matching `name` + `target`, and a mismatch fails
with *"X does not match any elements."* Type fields one at a time with `browser_type` instead.

## 4. Sign in first (auth apps)
Most apps gate per-user data, so you must be signed in to verify them. The dev mailer
(`cjbarre.grain-todo-list.foundation.email` `logger-email`) only **logs** the verification token — it
doesn't send mail — so create + verify a test
account deterministically over nREPL, *then* sign in through the form (so the browser holds the cookie):
```clojure
;; 1. create the account
(tools/invoke-command! {:command/name :user/sign-up
                        :email-address "builder@test.dev"
                        :password "Test-1234!" :confirm-password "Test-1234!"})
;; 2. read the verification token straight from the event log (the dev mailer only logged it)
(->> (tools/events {:types #{:user/email-verification-requested}}) last)
;; → inspect the event; grab its :verification-token
;; 3. verify the email
(tools/invoke-command! {:command/name :user/verify-email :verification-token "<token-from-step-2>"})
```
Then sign in via the UI so the browser session is authed: `browser_navigate` to `/auth/sign-in`,
`browser_snapshot`, `browser_type` the email + password (each with element+ref+**target**+text), and
`browser_click` the sign-in button. You're now signed in for the rest of the verification.

(If the app has no accounts, skip this — public-first pages are reachable directly.)

## 5. The loop
`browser_snapshot` → act (by ref+target) → **re-snapshot to confirm the result** → assert the underlying
data via nREPL where possible → `browser_take_screenshot` at key states. Record pass/fail per acceptance
check. A check isn't verified until you've seen the actual result, as a user.
