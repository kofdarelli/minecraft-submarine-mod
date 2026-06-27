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

The smoke test clears a small area ahead of the player and leaves a spawned test submarine in the world.

On Windows, the automated client smoke test builds the mod, launches `runClient`, opens the first singleplayer world, runs `/submarine test run`, watches `run/logs/latest.log`, and exits nonzero on failure:

```powershell
.\run-test.ps1
```

Set `SUBMARINE_TEST_SKIP_MENU=true` if the client will already be loaded into a world before the test command is sent.
