---
description: Run a read-only reuse-cluster scan and (re)write docs/vault/02-reuse-clusters.md, or check a specific concept for existing equivalents
argument-hint: Optional concept/name to check instead of a full scan (e.g. "empty state composable")
---

If arguments were given, launch the `reuse-scout` subagent (via the Task tool) for a targeted pre-write check: does an equivalent to `$ARGUMENTS` already exist in the graph, where, and is it reusable as-is.

If no arguments were given, launch `reuse-scout` for the full scan and have it produce or refresh `docs/vault/02-reuse-clusters.md` — duplicated/near-duplicate composables and logic grouped into clusters, each with call sites.

This command makes no code changes — `reuse-scout` is read-only.
