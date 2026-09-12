# Community Scripts

Community-contributed automation scripts for the Project X engine.

These ship as the **Community** channel in the launcher's Plugins tab. Installing
that channel downloads the jar built from this repository.

Scripts here are written by the community. They are not reviewed line by line by
the engine maintainers, so read anything you intend to run.

## Building

Open the folder in IntelliJ IDEA and let the Gradle sync finish, or run:

```bash
./gradlew installScripts
```

The build downloads the [script API](https://github.com/iEasyScript/script-api)
itself (the version is in `gradle.properties`) and, if needed, a JDK 25. It
compiles the jar and copies it to `~/.projectx/scripts/`, where the engine loads
it on startup. To build without installing, run `./gradlew jar` and find the jar
in `build/libs/`.

## Writing a script

Scripts can be written in Kotlin or Java. Read the
[full guide](https://github.com/iEasyScript/script-api/blob/main/WRITING-SCRIPTS.md)
in the script-api repository, and start from the
[starter template](https://github.com/iEasyScript/script-template). Put scripts
under `src/main/kotlin/com/projectx/script/impl/<your-name>/` (or
`src/main/java/...` for Java).

Two rules matter more than the rest:

- React to outcomes, never to a fixed sleep. Wait until the thing you expected
  actually happened, with a timeout.
- Never interact in a loop without a minimum interval. A state loop that clicks
  and returns is re-entered every tick.

## Contributing

Open a pull request. Keep one script per file, match the surrounding style, and
say in the description what the script does and where you tested it.

## Releases

A `v*` tag builds the jar and publishes it as a release asset with its sha256.
The launcher installs from the newest release.
