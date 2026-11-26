---
name: accessibility
description: Accessibility rules for every screen of this Android app (TalkBack, touch targets, contrast, font scaling, reduced motion, automated checks). Use whenever creating or changing a composable, a screen, an icon, an image or a colour, and before a pull request that touches the UI. Trigger whenever accessibility, a11y, TalkBack, contentDescription, semantics or screen reader is mentioned.
---

# Accessibility

Accessibility is mandatory: principle VII of `.specify/memory/constitution.md` makes it a
delivery condition, not a final pass.

1. Read `docs/accessibility.md` before writing or changing a composable. Apply every rule of its
   table to what you write.
2. Every screen's UI test includes a test that calls `enableAccessibilityChecks()` and
   `tryPerformAccessibilityChecks()`, in light and dark theme.
3. Every `contentDescription`, `onClickLabel` and state description is a string resource,
   translated in every supported language like any other text.
4. Before a pull request that changes the UI, launch the `accessibility-reviewer` agent on the
   change and fix what it confirms.
5. Do the manual checks of `docs/accessibility.md` on an emulator (TalkBack, largest font, dark
   theme). When you cannot run them, say so explicitly; never claim a check you did not do.
