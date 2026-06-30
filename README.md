# Walkable Submarine

Fabric 1.20.1 MVP for a walkable, drivable Minecraft submarine built on Valkyrien Skies.

## Features

- Blueprint item places a starter submarine made from vanilla blocks.
- Valkyrien Skies assembles the template into a walkable moving ship.
- Invisible seat entities support one pilot seat and passenger seats.
- Pilot input is sent client-to-server and applied as server-side VS physics forces.
- Hull, windows, seats, and template-critical blocks are protected.
- Interior volumes allow normal furnishing placement and breaking.

## Development

Use Java 17.

```powershell
.\gradlew build
```

In a dev world, use the blueprint item or:

```text
/submarine spawn starter_sub
```

## Testing

Fast compile check:

```powershell
.\gradlew build
```

In a dev world with commands enabled, run the submarine smoke tests:

```text
/submarine test run
```

Target a smaller area when debugging:

```text
/submarine test template
/submarine test metadata
/submarine test spawn
```

The smoke test clears a small area ahead of the player and leaves a spawned test submarine in the world.
Results are written to `build/test-results/submarine-smoke/results.json`.

On Windows, the automated client smoke test builds the mod, launches `runClient`, creates or opens the disposable `run/saves/SubmarineAutomationTest` world, runs `/submarine test run`, watches `run/logs/latest.log`, and exits nonzero on failure:

```powershell
.\run-test.ps1
```

Set `SUBMARINE_TEST_WORLD_NAME` to use a different disposable world name. Set `SUBMARINE_TEST_RESET_WORLD=false` to reuse the disposable world instead of deleting it before the run.
If the first launch is still downloading assets or preparing the dev client, increase `SUBMARINE_TEST_WORLD_READY_TIMEOUT_MS`.
The runner also copies failure artifacts to `build/test-results/submarine-smoke/`, including the latest log, log tail, runner summary, and a screenshot when available.
