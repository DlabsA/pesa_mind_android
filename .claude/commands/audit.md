---
description: Run a full read-only architecture + standards audit and (re)write docs/vault/01-architecture-audit.md
argument-hint: Optional focus area (e.g. "auth" or "SMS ingestion")
---

Launch the `android-auditor` subagent (via the Task tool) to produce or refresh `docs/vault/01-architecture-audit.md`.

If arguments were given, pass them as the focus scope (e.g. audit just the auth flow or the SMS ingestion pipeline) instead of the whole codebase. If `graphify-out/graph.json` doesn't exist or looks stale, tell the agent to flag that rather than silently grep-sweeping instead.

This command makes no code changes — `android-auditor` is read-only.
