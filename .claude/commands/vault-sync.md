---
description: Rebuild the graphify graph and refresh both docs/vault reports (architecture audit + reuse clusters)
argument-hint: (none)
---

Run, in order:

1. `graphify update .` via Bash — real CLI subcommand, code-only re-extraction, no LLM needed. If `graphify-out/graph.json` doesn't exist yet (first run), this has nothing to update — instead invoke the `/graphify` skill (Skill tool, no path argument defaults to `.`) to do the full LLM-orchestrated build. There is no bare `graphify .` shell command — it errors.
2. Launch `android-auditor` (via the Task tool) to refresh `docs/vault/01-architecture-audit.md`.
3. Launch `reuse-scout` (via the Task tool) to refresh `docs/vault/02-reuse-clusters.md`.

Steps 2 and 3 have no dependency on each other and can be launched in parallel once step 1 finishes.

This command makes no application-code changes.
