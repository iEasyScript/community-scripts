# Community Scripts

Community-contributed automation scripts for the Project X engine.

Scripts here are written by the community. They are not reviewed line by line by
the engine maintainers, so read anything you intend to run.

## Building

You need JDK 25. The engine API is not on Maven Central, so download the three
jars from the [script-api releases](https://github.com/iEasyScript/script-api/releases)
and drop them in `libs/`:

```
libs/projectx-engine-api-<version>.jar
libs/projectx-core-<version>.jar
libs/projectx-official-scripts-<version>.jar
```

Then build and install:

```bash
./gradlew installScripts
```

That compiles the jar and copies it to `~/.projectx/scripts/`, where the engine
loads it on startup. Restart the engine, or hot-reload it, to pick up changes.

To build without installing, run `./gradlew jar` and find the jar in
`build/libs/`.

## Writing a script

Read the [full guide](https://github.com/iEasyScript/script-api/blob/main/WRITING-SCRIPTS.md) in the script-api repository, and start from the [starter template](https://github.com/iEasyScript/script-api). Existing scripts in `src/main/kotlin/com/projectx/script/impl/` are also good reference.
from an existing script in `src/main/kotlin/com/projectx/script/impl/` — the
shapes there are the fastest way in. The API surface is documented in the
[script-api](https://github.com/iEasyScript/script-api) repository.

Two rules matter more than the rest:

- React to outcomes, never to a fixed sleep. Wait until the thing you expected
  actually happened, with a timeout.
- Never interact in a loop without a minimum interval. A state loop that clicks
  and returns is re-entered every tick.

## Contributing

Open a pull request. Keep one script per file, match the surrounding style, and
say in the description what the script does and where you tested it.
