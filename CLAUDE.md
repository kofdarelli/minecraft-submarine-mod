# Submarine Mod — CLAUDE.md

Fabric mod for Minecraft 1.20.1 using Valkyrien Skies 2 (VS2) for physics.
Java 17, Fabric Loom, official Mojang mappings.

## Build

```
.\gradlew build
```

## Run (dev client)

```
.\gradlew runClient
```

Launches a Minecraft 1.20.1 dev instance with the mod loaded. Use a Creative world; cheats are on by default in single-player Creative.

## Tests

Follow `AGENTS.md` for the concise testing contract.

### In-game integration tests

After launching, run in chat (requires op):
```
/submarine test run
```
Tests template integrity, metadata round-trip, and spawn + seat creation. Results appear in chat and in `run/logs/latest.log`.

Targeted variants are available for faster debugging:

```
/submarine test template
/submarine test metadata
/submarine test spawn
```

JSON results are written to `build/test-results/submarine-smoke/results.json`.

### Automated client runner

Launches Minecraft via Gradle, creates or opens the disposable `run/saves/SubmarineAutomationTest` world, fires the test command, reads the log, and exits with code 0 (pass) or 1 (fail):

```
.\run-test.ps1
```

The runner class is `src/test/java/com/submarine/automated/SubmarineTestRunner.java`.
Environment variables: `SUBMARINE_TEST_WORLD_NAME`, `SUBMARINE_TEST_RESET_WORLD`, `SUBMARINE_TEST_LAUNCH_WAIT_MS`, `SUBMARINE_TEST_WORLD_READY_TIMEOUT_MS`, `SUBMARINE_TEST_TIMEOUT_MS`.
Failure artifacts are written to `build/test-results/submarine-smoke/`.

## In-game usage

| Action | How |
|--------|-----|
| Spawn submarine | `/submarine spawn starter_sub` (op) or right-click any block with the Blueprint item |
| Get blueprint | `/give @s submarine:submarine_blueprint` |
| Pilot | Right-click the front dark-oak stair (pilot seat). W/S = forward/back, A/D = turn, Space = rise, Shift = dive, Sneak = exit |
| List submarines | `/submarine list` |
| Delete submarine | `/submarine delete <id>` |
| Transfer ownership | `/submarine transfer <id> <player>` |
| Run tests | `/submarine test run` |

## Project structure

```
src/main/java/com/submarine/
  SubmarineMod.java              — mod entry point, registers everything
  control/SubmarineController.java — per-tick VS physics (forces, buoyancy, depth limit)
  data/SubmarineMetadata.java    — per-ship data (owner, template, shipyard origin)
  data/SubmarineSavedData.java   — SavedData persistence (NBT, per ServerLevel)
  seat/SubmarineSeatManager.java — seat entity lifecycle, owner enforcement, stale cleanup
  entity/SubmarineSeatEntity.java — invisible riding entity for each seat
  template/StarterSubmarineTemplate.java — all block layout + physics constants
  template/SubmarineSpawner.java — assembles blocks → VS ship
  protection/SubmarineProtection.java — block break/place event guards
  net/SubmarineNetworking.java   — server-side packet receivers
  command/SubmarineCommands.java — command registration hub
  command/SubmarineManageCommands.java — list / delete / transfer
  command/SubmarineTestCommands.java   — in-game test runner

src/client/java/com/submarine/
  client/SubmarineClient.java    — client entry point
  client/SubmarineHud.java       — depth gauge + role indicator + underwater tint
  net/SubmarineClientInput.java  — sends WASD/space/shift to server each tick

src/main/resources/
  fabric.mod.json
  assets/submarine/lang/en_us.json
  assets/submarine/models/item/submarine_blueprint.json
  data/submarine/recipes/submarine_blueprint.json
```

## Key constants (StarterSubmarineTemplate)

| Constant | Value | Purpose |
|----------|-------|---------|
| `FORWARD_FORCE` | 180 000 | Thrust along ship axis |
| `VERTICAL_FORCE` | 140 000 | Rise/dive thrust |
| `YAW_TORQUE` | 950 000 | Turn torque |
| `LINEAR_DRAG` | 12 000 | Velocity damping |
| `ANGULAR_DRAG` | 80 000 | Rotation damping |
| `BUOYANCY_GRAVITY` | 10.0 | Matches VS default gravity; gives neutral buoyancy |
| `MIN_DEPTH` | -60.0 | World Y floor; 4× buoyancy applied below this |
| `IDLE_STATIC_AFTER_TICKS` | 8 | Ticks of no input before ship freezes (above water) |

## VS2 API notes

- Ship physics forces: `ValkyrienSkiesMod.getOrCreateGTPA(dimId).applyWorldForce(shipId, vec, null)`
- Ship lookup: `VSGameUtilsKt.getShipObjectWorld(level).getAllShips().getById(shipId)`
- Ship mass: `ship.getInertiaData().getMass()` (`getInertia()` does not exist; `getShipMass()` is deprecated)
- Angular velocity: `ship.getAngularVelocity()` (`getOmega()` is deprecated)
- Ship position: `ship.getTransform().getPositionInWorld()`
- Dimension ID: `VSGameUtilsKt.getDimensionId(level)`
