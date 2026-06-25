---
name: design-review
description: Visual quality gate — screenshot each primary screen, critique it against the baseline rubric, and refine the components until it passes. Use before declaring a build done.
---

# Design Review — the visual quality gate

Functional-but-ugly is **not done**. After a screen works, you must *look* at it and refine it to a
baseline standard. This is a loop, not a one-time screenshot: screenshot → critique → fix the component →
re-screenshot → repeat until it passes. You fix the **component** in the UI foundation
(`cjbarre.grain-todo-list.foundation.ui` / `foundation.ui.components` for app-wide widgets, or
`service.<svc>-service.ui.components` for domain widgets), not the one screen — so the fix propagates everywhere.

## The loop (run on EACH primary screen)
1. Navigate to the screen (Playwright `browser_navigate`).
2. `browser_take_screenshot` (full page) and **actually look at the image**.
3. Go through the rubric below and write an explicit **pass/fail for each item** with what's wrong.
4. Fix the offending UI foundation component (or the composition) to resolve every fail.
5. Reload and re-screenshot. Repeat until **all rubric items pass**.

A build is done only when every primary screen passes this rubric AND is composed from the UI
foundation components (no ad-hoc one-off layout).

## The baseline rubric (pass/fail, not vibes)
1. **Header** — page title (+ optional subtitle) on the left; primary actions on the right
   (`flex items-center justify-between`). FAIL if a button is glued to / overlapping the heading.
2. **Spacing rhythm** — one consistent gap scale between sections; cards have padding (`p-4`–`p-6`);
   content sits in a max-width container, not edge-to-edge. FAIL if gaps look random or content is cramped.
3. **Row alignment** — list/table rows use `items-center justify-between`: primary text left,
   status/value/actions right-aligned. FAIL if a value floats mid-row or columns don't line up.
4. **Hierarchy** — clear size/weight/color steps (one page title → section titles → body → muted
   `text-base-content/60` metadata). FAIL if everything is the same size/weight or labels outweigh values.
5. **Consistency** — cards, buttons, radius, and gaps look uniform across the screen (you get this for
   free by composing the same components). FAIL if styles are visibly mixed.
6. **Empty states** — intentional (title + hint + optional CTA). FAIL if a list/section is just blank.
7. **Density & breathing room** — nothing touching edges; comfortable line-height; tap targets aren't
   tiny. FAIL if it feels claustrophobic.

## Calibration — bad → good
**Bad (what we're fixing):** a "Today's Todos" heading with an "Add todo" button jammed right against
it, and a todo row showing the title with "Done" floating in the middle of the row.
```clojure
;; BAD: hand-rolled, button glued to heading, status floats
[:div (ui/section-title "Today's Todos")
      [:a {:class "btn btn-sm btn-primary"} "Add todo"]]
[:div (str (:title todo)) [:span "Done"]]
```
**Good (composed + aligned):**
```clojure
(kit/section-header "Today's Todos"
  (ui/action-button {:label "Add todo" :class "btn-primary btn-sm"
                     :attrs {:on/click {:effect (ds-ui/dispatch :todo/new-todo {})}}}))
(kit/row {}
  [:div [:p {:class "font-medium"} (:title todo)] (ui/metadata-text (:due-date todo))]
  (kit/badge "Done" "badge-success"))         ; status right-aligned by the row component
```
The fix lives in `section-header`/`row`/`badge` — so every screen using them is correct.

## Output before declaring done
For each primary screen, state the screenshot path and the rubric verdict, e.g.:
```
/ (dashboard): header ✓ spacing ✓ rows ✓ hierarchy ✓ consistency ✓ empty ✓ density ✓ — PASS
```
If any item is ✗, fix and re-run — do not declare done.
