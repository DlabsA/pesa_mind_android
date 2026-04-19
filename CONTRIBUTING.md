# Contributing

## Onboarding (Required)

Install Git hooks once after cloning:

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

