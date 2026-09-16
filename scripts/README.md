# Scripts

Utility scripts for this project.

> Template maintainers: init/verification tooling (`init_project.main.kts`,
> `verify_template.sh`) is documented in `docs/template-maintenance.md`.
> Those scripts are removed from generated projects.

## install_hooks.sh

Installs the repo's git hooks (`.githooks/`) into your local clone. Run once
after cloning, before your first commit:

```bash
./scripts/install_hooks.sh
```

## enable_ci.sh

Present only if CI was declined at project init. Installs the staged CI /
release automation (`.github/workflows/`, fastlane files, `pages/`,
release-please config) and then removes itself:

```bash
./scripts/enable_ci.sh
```

See SETUP.md for the GitHub secrets the pipeline needs.

## create_module.main.kts

Creates new KMP modules with proper structure and configuration.

- KMP source sets (`commonMain`, `androidMain`, `iosMain`) and the right
  convention plugin per module type
- Feature modules get a Screen + ViewModel starter; libraries get a basic
  class; the public/impl split is supported for libraries
- Updates `settings.gradle.kts` and `apps/compose/build.gradle.kts`

```bash
./scripts/create_module.main.kts                      # interactive
./scripts/create_module.main.kts feature messaging    # feature module
./scripts/create_module.main.kts library analytics    # library module
./scripts/create_module.main.kts library user:preferences  # sub-module
```

## setup_sentry.main.kts

Turns crash reporting on. Run once, right after init:

```bash
./scripts/setup_sentry.main.kts
```

Asks for a Sentry **user** auth token (`project:read`, `project:write`,
`org:read` — an organization `sntrys_…` token has only `org:ci` and 403s every
read endpoint), creates or adopts the project, writes the DSN into the
committed `telemetry.properties`, sets the CI variables and secret, then sends
a test event and waits for it to arrive before declaring success. Idempotent.

With no GitHub remote configured it prints "No GitHub repo reachable via gh"
and skips the CI step. That is expected here, not an error.

Commit `telemetry.properties` afterwards. A DSN is a write-only ingest
endpoint shipped inside every store binary, not a secret — keeping it per
developer is what leaves fresh clones silently reporting nothing.

## cleanup.sh

Cleans build artifacts and caches:

```bash
./scripts/cleanup.sh
```
