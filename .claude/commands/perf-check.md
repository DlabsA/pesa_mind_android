---
description: Metric-first performance review of a screen or diff against this repo's budgets (launch <2s, 16.67ms frame budget, <100MB/screen)
argument-hint: Screen, file, or diff to check (defaults to current unstaged diff)
---

Launch the `compose-perf` subagent (via the Task tool) to review `$ARGUMENTS` (or the current unstaged `git diff` if no arguments were given) against this repo's performance budgets.

Remind the agent up front whether a profiler/device/Macrobenchmark output is available in this session — if not, it must report structural risk only and say so explicitly, never round that up to a measured budget claim.

This command makes no code changes — `compose-perf` is read-only.
