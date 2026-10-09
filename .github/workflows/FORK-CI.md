# Fork CI: Fenix debug APK

Fork-only workflow (`fork-fenix-debug-apk.yml`) that builds a Firefox for Android (Fenix)
debug APK on GitHub Actions using Mozilla's **artifact mode**: Gecko (C++/Rust) is
downloaded prebuilt from Mozilla's CI; only Fenix/Android Components Kotlin code is compiled.

**Not for upstream.** Mozilla does not accept GitHub PRs; patches go through Bugzilla +
Phabricator (`moz-phab`). Keep this workflow in its own commit and exclude it when submitting.

## Running it

- Runs on every push to `context-menu-open-in-tab-group`, or manually:
  `gh workflow run fork-fenix-debug-apk.yml -R tlrdstd/firefox --ref context-menu-open-in-tab-group`
  (manual runs only appear in the Actions UI once the file exists on the default branch).
- Download: `gh run download <run-id> -R tlrdstd/firefox`
- Install on an arm64 phone or Apple Silicon emulator:
  `adb install -r fenix-arm64-v8a-debug.apk` (package `org.mozilla.fenix.debug`;
  installs alongside regular Firefox).

## Measured cost (first run, 2026-10-09, `ubuntu-latest`)

| Step | Time |
|---|---|
| Checkout (500 commits, blobless) | ~9 min |
| `mach bootstrap` (artifact mode) | ~2 min |
| `mach build` (download prebuilt Gecko) | ~7 min |
| `mach gradle fenix:assembleDebug` | ~9 min |
| **Total** | **~30 min** |

Runner disk used ~30 GB (`~/.mozbuild` 14 GB, objdir 7.8 GB, `~/.gradle` 2.6 GB, source).
Uploaded APK ~173 MB, retained 14 days. Public repos get free standard-runner minutes;
artifact storage counts toward the account's Actions storage quota.

## Maintenance requirements

1. **Keep the branch near upstream.** Artifact mode looks for prebuilt Gecko among the
   last 500 commits of your branch. Mozilla's CI artifacts expire, so a branch that has not
   been rebased for weeks/months will fail with "Could not find any candidate pushheads".
   Fix: sync the fork's `main` with `mozilla-firefox/firefox` and rebase the branch:
   `git fetch upstream main && git rebase upstream/main`, then
   `git push --force-with-lease=<branch>:<old-remote-sha> origin <branch>` (this single-branch
   clone does not track the feature branch, so plain `--force-with-lease` is rejected as stale).
   (Local clone is shallow/blobless; `git fetch --deepen` may be needed for rebases.)
2. **Branch name is hard-coded** in `on.push.branches`. Update it for new feature branches.
3. **Action versions** (`actions/checkout@v7`, `actions/upload-artifact@v7`) need
   occasional bumps when GitHub deprecates runtimes.
4. **Mozilla tooling drift.** Bootstrap choice names, mozconfig options, or the APK output
   path (`objdir-frontend/gradle/build/mobile/android/fenix/app/outputs/apk/debug/`) can
   change upstream; on failure compare with
   https://firefox-source-docs.mozilla.org/mobile/android/fenix.html.
5. **Runner capacity.** Needs ~30 GB disk and ~7 GB Gradle heap; `ubuntu-latest` currently
   has ample headroom. If it shrinks, re-add a step deleting preinstalled toolchains.
6. **Inherited workflow.** Upstream's `pr-handler.yml` only runs on pull requests; avoid
   opening PRs on the fork, or disable that workflow in the Actions UI.
7. **Test list.** The checks step runs `ktfmtCheck`, `detekt`, and only the unit-test
   classes listed via `--tests`. Add new test classes there when the branch adds them.
   On failure, reports are uploaded as `fenix-reports-<sha>`. Local formatting without a
   build: ktfmt's standalone jar (`--kotlinlang-style`, max width 120 via `.editorconfig`).

## Known inefficiencies (deliberately not optimized)

- Bootstrap downloads an emulator + x86_64 system image (~several GB) that CI does not use.
- No caching of `~/.mozbuild` / `~/.gradle`; caching could save ~5–10 min per run but
  adds cache-invalidation upkeep.
