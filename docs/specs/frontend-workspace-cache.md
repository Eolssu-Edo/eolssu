# Frontend CI workspace cache

- Task: S15P31E102-292. User requested cache/output preservation, same-SHA rebuild measurement, and unchanged stage visibility.
- Preserve all 20 production stage names, Gradle commands, --no-daemon, tests, production optimization, revision validation, deployment policy and 20-minute timeout.
- Existing managed Git workspace: restore tracked source and remove untracked, non-ignored files; retain ignored Gradle/Kotlin/node/build caches and outputs. Source checkout still selects exactly REVISION. Refuse cleanup outside a repository root.
- Empty/uninitialized workspace, including a .git directory with no valid HEAD commit after interrupted clone: use deleteDir bootstrap and normal checkout. The helper signals this specific state with exit 2; other preparation failures stop the build without deleting the workspace.
- Cache keys and invalidation remain Gradle input/output tracking; no forced cacheability or skipped verification.
- Measure two CI-only runs in the same isolated Jenkins job/workspace on the same immutable SHA and host: initial empty workspace, then retained workspace. Keep --no-daemon and all CI tasks identical. Production deploy stage is excluded equally from both measurements; image uses an isolated tag. Record queue separately, phase times, result, test counts, and artifact hashes. A timeout is reported as a timeout, not a completed baseline.
- No running job cancellation or scheduler/executor changes. No app deployment in the benchmark. Apply production workspace preparation only after correctness and useful speedup are established.
