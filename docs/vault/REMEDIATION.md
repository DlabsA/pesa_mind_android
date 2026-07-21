# Remediation — leaked release-signing credential

Follow-up to `docs/vault/01-architecture-audit.md` finding 1 and
`docs/decisions/ADR-0002-remove-hardcoded-signing-credential.md`. The commands below are
**not run by Claude** — they're destructive/irreversible or affect shared history, so
they're your call. Run them yourself, in this order.

## 1. Rotate the compromised store/key password

The password `K@sh404730` was committed in cleartext in `app/build.gradle.kts` across 6
commits and is considered compromised regardless of whether this repo was ever pushed
anywhere non-private — treat "was it ever pushed" as unknowable, not as a mitigating
factor.

Android keystores support changing the *store* password and a given key's password
independently, without regenerating the key itself:

```bash
# Change the keystore (store) password
keytool -storepasswd \
  -keystore ~/.android/my-release-key.keystore

# Change the key password for the pesa_mind alias
keytool -keypasswd \
  -alias pesa_mind \
  -keystore ~/.android/my-release-key.keystore
```

Both commands prompt interactively for the current password, then the new one. After
rotating, update your local `keystore.properties` (already created, gitignored, at repo
root) with the new values — `storePassword` / `keyPassword`.

**Caveat — Play App Signing:** if this key is enrolled in Play App Signing (Google holds
the *app signing key* and you hold only an *upload key*), what you can rotate depends on
which key `my-release-key.keystore` actually is:
- If it's the **upload key**: you can rotate its password freely (above), and if you
  suspect the key material itself (not just the password) is compromised, Google Play
  Console has an upload-key-reset flow (Play Console → App integrity → App signing →
  request upload key reset) — this does NOT require touching the app signing key.
- If it's the **app signing key** itself (legacy apps enrolled before upload keys existed,
  or apps that opted out of Play App Signing): the app signing key generally **cannot be
  rotated** without Google's manual key-upgrade process (requires proof of ownership,
  case-by-case, can take weeks) — check Play Console → App integrity first to see which
  case you're in before assuming a password rotation alone is sufficient.

## 2. Purge the secret from git history

Rotating the password (step 1) makes the leaked value useless going forward, but it will
still be readable in every historical commit that touched `app/build.gradle.kts` until
history is rewritten. Two options — pick one:

### Option A: `git filter-repo` (recommended, faster, actively maintained)

```bash
# Install if needed: brew install git-filter-repo
cd /Users/conradkash/Github/dlabs/pesa_mind_android

# Back up first — filter-repo rewrites all history in place
git clone --mirror . ../pesa_mind_android-backup.git

git filter-repo --replace-text <(cat <<'EOF'
K@sh404730==>REDACTED
EOF
)
```

### Option B: BFG Repo-Cleaner

```bash
# Install if needed: brew install bfg
cd /Users/conradkash/Github/dlabs/pesa_mind_android
git clone --mirror . ../pesa_mind_android-backup.git   # backup first

echo 'K@sh404730' > /tmp/secrets.txt
bfg --replace-text /tmp/secrets.txt .
rm /tmp/secrets.txt

git reflog expire --expire=now --all
git gc --prune=now --aggressive
```

### Caveats (read before running either)

- **Requires a force-push.** `git push --force-with-lease origin main` (or whichever
  branches contain the old history) — this rewrites commit SHAs for every commit after
  the point where the secret was introduced. Do this deliberately, not as a reflex.
- **Breaks every existing clone/fork.** Anyone else with a local clone — collaborators,
  CI checkouts, forks — will have diverged history and need to re-clone (or hard-reset
  to the new history); their existing local branches/stashes on top of old commits will
  need manual reconciliation.
- **Any open PRs against the rewritten commits will likely need to be recreated** or
  rebased by hand.
- **Any external system that references old commit SHAs** (deploy logs, issue-tracker
  commit links, CI build records) will point to now-nonexistent commits.
- The `git clone --mirror` backup step above is not optional — if the filter step is
  misconfigured, the mirror clone is your recovery path.
- If the repository was ever pushed to a non-private remote (public GitHub, a CI cache,
  anyone's fork), treat the secret as permanently exposed regardless of history rewrite —
  rewriting history here does not retroactively purge copies elsewhere. Rotation (step 1)
  is the only thing that actually neutralizes the leak; the history purge is cleanup, not
  a substitute for it.
