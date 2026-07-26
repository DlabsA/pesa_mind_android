---
description: Adversarial read-only review of the current diff against .claude/CLAUDE.md target standards and root CLAUDE.md
argument-hint: Optional diff scope (defaults to unstaged git diff)
---

Launch the `android-reviewer` subagent (via the Task tool) to review `$ARGUMENTS` (or the current unstaged `git diff` if no arguments were given).

This command makes no code changes — `android-reviewer` is read-only and only reports findings, ranked by severity, for the caller to act on.
