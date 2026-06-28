# Agent Instructions

## Testing Expectations

Use Java 17. For ordinary code changes, run:

```powershell
.\gradlew.bat build
```

Run a targeted in-game smoke test when changing the related area:

```text
/submarine test template
/submarine test metadata
/submarine test spawn
```

Run the full in-game smoke suite after changes to submarine spawning, saved metadata, seat creation, command registration, networking, client startup automation, or test infrastructure:

```text
/submarine test run
```

For end-to-end client automation on Windows, run:

```powershell
.\run-test.ps1
```

The automated runner uses a disposable world at `run/saves/SubmarineAutomationTest` by default. It deletes only that world before each run unless `SUBMARINE_TEST_RESET_WORLD=false` is set.

## Failure Artifacts

Smoke test JSON results are written to:

```text
build/test-results/submarine-smoke/results.json
```

The automated runner writes failure artifacts to `build/test-results/submarine-smoke/`, including `runner-summary.txt`, `latest.log`, `latest-tail.log`, and `failure-screen.png` when possible.

## Practical Rules

- Do not run smoke tests in a real user world.
- Do not lower automation timeouts just to make a local run finish sooner.
- If `.\run-test.ps1` times out before Minecraft logs appear, treat it as a dev-client launch/setup problem, not a mod behavior failure.
- Prefer adding focused submarine-specific smoke tests over broad UI automation.
