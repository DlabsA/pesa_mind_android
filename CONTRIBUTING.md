# Contributing

## Onboarding (Required)

Git hooks are installed automatically when you run common Gradle tasks (for example dependency resolution/build tasks).

If you want to install them immediately after cloning, run:

```bash
./.githooks/install-hooks.sh
```

### What this enforces

- The `pre-commit` hook blocks commits on `main`.
- Create a feature branch before committing:

```bash
git switch -c feature/my-change
```

### If hooks stop working

Re-run the installer:

```bash
./.githooks/install-hooks.sh
```

