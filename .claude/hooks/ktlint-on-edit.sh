#!/usr/bin/env bash
# PostToolUse hook (Edit|Write|MultiEdit): runs ktlintCheck when a .kt/.kts file was touched.
# No standalone ktlint CLI is wired up in this repo (only the Gradle plugin), so this
# is whole-module, not scoped to the single edited file — slower than ideal, documented tradeoff.
#
# KNOWN GAP (see docs/vault/01-architecture-audit.md): the org.jlleitschuh.gradle.ktlint
# plugin is applied only to the ROOT Gradle project (build.gradle.kts:9), not to `app`,
# and there is no subprojects{}/allprojects{} block propagating it. `app` has no Kotlin
# sources of its own to lint at the root level, so today `ktlintCheck` only checks
# build/settings .kts scripts — it does NOT lint anything under app/src. This hook will
# run and pass/fail based on that same (currently non-app) scope until the plugin is
# applied to `app` as well. Left as-is deliberately: fixing the Gradle wiring is an
# application-config change, out of scope for this provisioning pass — flagged, not fixed.
set -euo pipefail

input="$(cat)"
file_path="$(jq -r '.tool_input.file_path // empty' <<<"$input")"
cwd="$(jq -r '.cwd // empty' <<<"$input")"

if [[ -z "$file_path" || ! "$file_path" =~ \.(kt|kts)$ ]]; then
  exit 0
fi

repo_root="$cwd"
if [[ -z "$repo_root" ]]; then
  exit 0
fi

cd "$repo_root" || exit 0

if [[ ! -x "./gradlew" ]]; then
  exit 0
fi

if ! output="$(./gradlew ktlintCheck --console=plain 2>&1)"; then
  echo "ktlintCheck failed after editing $file_path:" >&2
  echo "$output" >&2
  exit 2
fi

exit 0
