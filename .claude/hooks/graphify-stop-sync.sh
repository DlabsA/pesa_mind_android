#!/usr/bin/env bash
# Stop hook: heuristically detects structural changes (added/deleted/renamed files,
# i.e. not plain content edits) since the last graphify sync and runs `graphify update .`.
# This is best-effort — it can't distinguish "moved a file" from "wrote a new unrelated file";
# it just means graphify stays roughly in sync without a required manual /vault-sync.
# Never blocks the stop (always exits 0) — a stale graph is a soft failure, not worth
# forcing another turn over.
set -uo pipefail

input="$(cat)"
cwd="$(jq -r '.cwd // empty' <<<"$input" 2>/dev/null)"
[[ -z "$cwd" ]] && exit 0

cd "$cwd" || exit 0
git rev-parse --is-inside-work-tree >/dev/null 2>&1 || exit 0
[[ -f "graphify-out/graph.json" ]] || exit 0
command -v graphify >/dev/null 2>&1 || exit 0

structural="$(git status --porcelain=v1 2>/dev/null | grep -E '^(A|D|R|\?\?)' | sort)"
[[ -z "$structural" ]] && exit 0

marker=".claude/hooks/.last-graphify-sync"
structural_hash="$(shasum -a 256 <<<"$structural" | cut -d' ' -f1)"

if [[ -f "$marker" && "$(cat "$marker")" == "$structural_hash" ]]; then
  exit 0
fi

if graphify update . >/tmp/graphify-stop-sync.log 2>&1; then
  echo "$structural_hash" > "$marker"
  echo "graphify graph refreshed (structural changes detected)."
fi

exit 0
