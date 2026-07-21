# ADR-0001: Claude Code toolkit for Pesa Mind Android

**Status:** Accepted
**Date:** 2026-07-19

## Context

The project needed a `.claude/` toolkit tailored to a Kotlin/Android Compose codebase
undergoing an architecture audit and standards refactor, per the target contract in
`.claude/CLAUDE.md`. Before building anything custom, the existing Claude Code
capabilities and the official plugin marketplace were audited for a fit.

## What was discovered (corrections to the original request)

The request that kicked this off assumed several things that turned out not to match
reality. Recorded here so a future session doesn't re-assume them:

1. **`mobile-development` skill does not exist** — not installed globally or in the
   project, and not present in the one configured marketplace
   (`claude-plugins-official`, 257 plugins, github.com/anthropics/claude-plugins-official).
   Dropped from scope per explicit user decision; `pesa-mind-conventions` covers the
   same ground for this repo.
2. **Agent frontmatter has no `effort` or `maxTurns` field, and no allow/deny tool
   pair** — verified against Anthropic's own `plugin-dev` agent-development skill and
   its `validate-agent.sh`, plus every real agent file shipped in the marketplace.
   Required fields: `name`, `description`, `model` (`inherit`/`sonnet`/`opus`/`haiku`),
   `color`. Optional: `tools` (allow-list array; omit for full access). "Read-only" is
   enforced by omitting `Write`/`Edit`/`MultiEdit`/`NotebookEdit` from `tools` — `Bash`
   itself cannot be sub-restricted at the frontmatter level, so agents that need `Bash`
   (for `graphify` queries, `git diff`, `ktlintCheck`) are read-only by tool-omission
   plus a hard system-prompt instruction, not a sandboxed guarantee.
3. **No Android/Kotlin/Compose-specific audit, perf, or Espresso-testing plugin exists**
   in the marketplace. Closest generic candidates (`pr-review-toolkit`, the official
   `code-review` plugin, `code-modernization`, `kotlin-lsp`) don't cover this repo's
   domain (Compose perf, Material 3, `UnifiedViewModel`, the SMS-ingestion pipeline).
   Custom agents were built instead of adopting an existing plugin, per direct
   comparison, not by default.
4. **`graphify . --update` and bare `graphify .` are not valid CLI invocations.** The
   `graphify` binary (`~/.local/bin/graphify`) has no bare `<path>` command — running it
   errors `unknown command '.'`. A full first-time build requires the LLM-orchestrated
   `/graphify` skill (semantic extraction via subagents — can't run headless). The
   correct **headless, no-LLM** CLI form for an existing graph is `graphify update
   <path>`. This was caught by actually running the Stop hook during provisioning, not
   assumed — the hook initially shipped with the wrong syntax and was fixed in the same
   pass. `.claude/CLAUDE.md`'s own reuse-first section repeats the incorrect `graphify .`
   form; flagged as a correction to make there too (see `docs/vault/00-standards.md`).
5. **`ktlintCheck` currently lints zero application Kotlin files.**
   `org.jlleitschuh.gradle.ktlint` (`build.gradle.kts:9`) is applied only to the root
   Gradle project. There is no `subprojects{}`/`allprojects{}` propagation and
   `app/build.gradle.kts` doesn't apply the plugin itself, so `./gradlew ktlintCheck` —
   the command root `CLAUDE.md` documents as the lint gate — only checks root `.kts`
   build/settings scripts, never anything under `app/src`. Discovered by running the
   ktlint PostToolUse hook against a real file during provisioning testing (it failed on
   a pre-existing, unrelated `build.gradle.kts` violation — missing trailing newline,
   unused import — confirming the task only ever touches that file). This is a real,
   high-impact gap, not a hook-design nuance — carried into
   `docs/vault/01-architecture-audit.md` as a finding, not fixed here (fixing Gradle
   wiring is an application-config change, out of scope for a provisioning pass).
6. **detekt is not configured** — `.claude/CLAUDE.md` references "ktlint/detekt config"
   as a target; only ktlint exists today. Per user decision, documented as a follow-up
   item in the audit rather than pretended to be current state.

## Decision

Provision a fully custom `.claude/` toolkit (no marketplace plugin installed) tailored
to this repo:

### Subagents (`.claude/agents/`)

| Agent | Tools | Model | Read-only |
|---|---|---|---|
| `android-auditor` | Read, Grep, Glob, Bash | opus | Yes |
| `reuse-scout` | Read, Grep, Glob, Bash | sonnet | Yes |
| `compose-perf` | Read, Grep, Glob, Bash | sonnet | Yes (measurement-gated: never claims a budget is met without real profiler/benchmark data) |
| `test-author` | Read, Write, Edit, Grep, Glob, Bash | sonnet | No — writes only under `app/src/test` and `app/src/androidTest`, never `app/src/main` |
| `android-reviewer` | Read, Grep, Glob, Bash | opus | Yes |

### Skill

`.claude/skills/pesa-mind-conventions/SKILL.md` — ktlint commands (with the coverage-gap
warning above), folder/naming as currently laid out, the reuse-first workflow with
correct `graphify` command syntax.

### Commands (`.claude/commands/`)

`/audit`, `/reuse-scan`, `/perf-check`, `/review`, `/vault-sync` — thin wrappers that
launch the matching agent via the Task tool. None make code changes.

### Hooks (`.claude/settings.json`)

- `PostToolUse` on `Edit|Write|MultiEdit`, filtered to `.kt`/`.kts` in
  `.claude/hooks/ktlint-on-edit.sh` → runs `ktlintCheck` (whole-module; no standalone
  ktlint CLI wired up, can't scope to a single file; also inherits the coverage gap in
  finding 5 above until the Gradle wiring is fixed).
- `Stop` hook in `.claude/hooks/graphify-stop-sync.sh` → heuristically detects
  add/delete/rename entries in `git status --porcelain` (not plain content edits) since
  the last sync (tracked via `.claude/hooks/.last-graphify-sync`, gitignored) and runs
  `graphify update .` — the correct headless, no-LLM CLI form. Never blocks the stop
  (always exits 0) — a stale graph is a soft failure. Both hooks were run against real
  synthetic and live input during this session, not just written and assumed correct.

### `.gitignore`

Added `/graphify-out/` (working/cache dir — committed copies live in
`docs/vault/graphify/` instead per Step C) and the Stop-hook's local marker file.

## Validation performed

- `jq . .claude/settings.json` — valid JSON.
- Anthropic's own `validate-agent.sh` (from the `plugin-dev` marketplace plugin) run
  against all 5 agent files — all pass (frontmatter-format warnings only, e.g. missing
  `<example>` blocks; no errors).
- Both hook scripts executed with real and synthetic stdin payloads against this actual
  repo (not just reviewed as text): the ktlint hook correctly no-ops on non-Kotlin files,
  correctly runs and correctly surfaces the pre-existing `build.gradle.kts` violation
  with exit 2; the graphify Stop hook correctly ran a real `graphify update .` (837
  nodes, 751 edges rebuilt), wrote its marker, and correctly no-op'd on a second run with
  unchanged structural state.
- There is no `claude plugin validate` applicable here — these are loose project
  `.claude/` files, not a packaged plugin bundle, so that command doesn't apply. `/agents`
  (to visually confirm registration) is an interactive built-in this session cannot
  invoke itself — left as a user-side check.

## Consequences

- No net-new external dependency (no plugin installed) — smaller surface, but the
  agents/hooks are unique to this repo and won't benefit from marketplace updates.
- Two real, previously-undocumented gaps were surfaced as a side effect of provisioning
  (ktlint not covering `app/`, and the `graphify . --update` syntax being wrong wherever
  it appeared) — both are now the first entries a fresh `/audit` run should re-confirm
  and rank.
- The ktlint PostToolUse hook is honest about, but does not fix, its own reduced
  usefulness until the Gradle ktlint-scope gap is closed.
