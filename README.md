# MTR Traffic Addon

Addon for minecraft transit railway featuring car traffic generation.

## Beta Status

Current beta line: `26.5.B01`.

This build includes MTR route traffic, traffic dashboard controls, configurable spawn/despawn connectors, traffic light blocks, manual/auto intersection signals, bundled sedan vehicle resources, and fail-open handling so stale paused traffic/intersection state does not keep MTR vehicles blocked indefinitely.

## Build

Fabric Loom `1.16.1` requires Gradle to run on JDK 21 or newer. The mod still targets Java 17 bytecode for Minecraft 1.20.1 runtime compatibility.

Example local build:

```powershell
$env:JAVA_HOME='C:\Users\opale\.jdks\ms-21.0.8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

## Built-In Vehicle Resources

The sedan vehicle resources are bundled inside the mod jar. Players do not need to install the old standalone sedan resource pack when using a current build.

Current built-in sedan visuals:

- `mta_sedan`
- `mta_sedan_white`
- `mta_sedan_black`
- `mta_sedan_green`
- `mta_sedan_red`
- `mta_sedan_blue`
- `mta_sedan_brown`
- `mta_sedan_orange`

Current built-in traffic vehicle definitions:

- `sedan_01`
- `sedan_white`
- `sedan_black`
- `sedan_green`
- `sedan_red`
- `sedan_blue`
- `sedan_brown`
- `sedan_orange`

For adding or updating MTR vehicle visuals, see [docs/RESOURCE_PACK_AUTHORING.md](docs/RESOURCE_PACK_AUTHORING.md).

Author: opaleq
Website: https://cookiecraftmods.com
Github: https://github.com/0PALEQ/MTR-Traffic-Addon

Credits: opaleq, cookiecraftmods

## License

All rights reserved. Modpacks, showcases, videos, streams, articles, and similar media are allowed with proper credit. Commercial redistribution of the mod is not allowed. See [LICENSE](LICENSE).
