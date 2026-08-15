# MTR Traffic Addon

Addon for minecraft transit railway featuring car traffic generation.

## Current Release

Current release: `26.7.0` for Minecraft `1.20.1, 1.20.4, 1.21.1`.

This release adds universal editable road signs, the MTR Path Blocker Connector, train-controlled intersections and tollgates, survival recipes for every registered addon item, eight included languages, five Nissan Sentra variants, a localized responsive dashboard, and major traffic simulation and networking improvements.

**26.7.0 highlights:**

- Customize road signs with up to four Unicode text lines, dimensions, text/background/edge colors, imported PNG artwork, and resource-pack-defined bases.
- Block an existing rail from newly generated MTR siding/depot paths without replacing its geometry, style, speed, direction, or normal signal colors.
- Configure `Crossing` or `Train` intersections with selectable train approaches, animated tollgate poles/bars, and blinking pedestrian clearance signals.
- Run larger traffic networks through a dedicated 20 Hz simulation loop, asynchronous per-player snapshots, spatial filtering, deterministic virtual departures, and reliable mid-route materialization.
- Search and rename connectors, copy/paste vehicle pools, use row-level Locate/Pool actions, and fit the dashboard map to configured content.
- Craft all 13 addon items and use the interface in English, Polish, German, Japanese, French, Spanish, Czech, or Simplified Chinese.

Read the [full 26.7.0 release notes](docs/RELEASE_NOTES_26.7.0.md) for all features, fixes, compatibility changes, upgrade instructions, and known limitations.

Traffic vehicle visibility and simulation distances are `auto` by default in `config/mtr-traffic-addon.properties`. Auto visibility distance follows render distance minus 2 chunks, and auto simulation/materialization distance follows visibility distance plus `trafficVehicleMaterializationMarginChunks` chunks. The default margin is 2 chunks. Either distance value can be set to a fixed block distance if a server owner wants an explicit cap.

Addon traffic simulation runs on a dedicated wall-clock simulation thread instead of doing vehicle movement and spacing inside the Minecraft server tick path. Spawn connectors produce deterministic virtual route streams globally; only vehicles whose calculated route position is near a player and has enough route clearance are materialized and sent to clients. Materialized vehicles that are not sent to any player for `trafficVehicleUnrenderedLifetimeSeconds` are removed and can be recreated later from the virtual stream.

## Build

Fabric Loom `1.16.1` requires Gradle to run on JDK 21 or newer. The mod still targets Java 17 bytecode for Minecraft 1.20.1 runtime compatibility.

Example local build:

```powershell
$env:JAVA_HOME='C:\Users\opale\.jdks\ms-21.0.8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

Standard Gradle artifacts:

- `build/libs/mtr-traffic-addon-26.7.0.jar`
- `build/libs/mtr-traffic-addon-26.7.0-sources.jar`

The prepared Fabric distribution copy in this workspace is named `build/libs/mta-26.7.0-fabric-1.20.1.jar`.

## Built-In Vehicle Resources

The road vehicle resources are bundled inside the mod jar. Players do not need to install the old standalone vehicle resource pack when using a current build.

Current built-in vehicle visuals:

- `mta_sedan`
- `mta_sedan_white`
- `mta_sedan_black`
- `mta_sedan_green`
- `mta_sedan_red`
- `mta_sedan_blue`
- `mta_sedan_brown`
- `mta_sedan_orange`
- `mta_sedan_taxi`
- `mta_hatchback`
- `mta_hatchback_white`
- `mta_hatchback_gray`
- `mta_hatchback_blue`
- `mta_hatchback_brown`
- `mta_hatchback_green`
- `mta_hatchback_orange`
- `mta_hatchback_pink`
- `mta_hatchback_red`
- `mta_nissan_sentra_white`
- `mta_nissan_sentra_red`
- `mta_nissan_sentra_blue`
- `mta_nissan_sentra_black`
- `mta_nissan_sentra_brown`

Current built-in traffic vehicle definitions:

- `sedan_01`
- `sedan_white`
- `sedan_black`
- `sedan_green`
- `sedan_red`
- `sedan_blue`
- `sedan_brown`
- `sedan_orange`
- `sedan_taxi`
- `hatchback`
- `hatchback_white`
- `hatchback_gray`
- `hatchback_blue`
- `hatchback_brown`
- `hatchback_green`
- `hatchback_orange`
- `hatchback_pink`
- `hatchback_red`
- `nissan_sentra_white`
- `nissan_sentra_red`
- `nissan_sentra_blue`
- `nissan_sentra_black`
- `nissan_sentra_brown`

For adding or updating MTR vehicle visuals, see [docs/RESOURCE_PACK_AUTHORING.md](docs/RESOURCE_PACK_AUTHORING.md).

Traffic lights can be bound with the MTR brush from inside an intersection area. Lights can target IN nodes or intersection signal groups, so pedestrian lights can follow a whole crossing phase instead of a single node. During a pedestrian yellow phase, the green indication blinks at a one-second cycle before the signal turns red.

The `MTR Path Blocker Connector` closes an existing rail to MTR route searches without replacing its geometry, custom resource-pack style, or ordinary signal colors. Select the rail's two endpoint nodes as with an MTR signal connector; select the same rail again to reopen it. Regenerate the affected MTR depot route after changing a blocker, because paths that MTR already generated are not rewritten in place.

The `Universal Road Sign` is edited with the MTR brush and supports selectable bases, up to four Unicode text lines, custom dimensions and colors, and imported PNG artwork. Resource packs can add country-specific bases without addon code. See [ROAD_SIGNS.md](ROAD_SIGNS.md) for the player and pack-author guide.

## Dashboard Quick Tutorial

- Rename a connector track: open `Connectors`, double-click its row, type the new name, and press Enter. Submitting an empty name restores the generated default name.
- Copy a vehicle pool: select a spawn connector, open `Vehicle Pool`, and press `Copy Vehicles`. Open another spawn connector's pool and press `Paste Vehicles`. Paste replaces the destination pool; it does not merge the two lists. This uses the dashboard's internal copy buffer, not the operating-system clipboard.
- Change an intersection level: open `Intersections` and right-click an intersection row to toggle `Crossing` / `Train`. In Train mode, press `Find Nodes`, select a node on the map, and use `Train Node: On/Off` to choose train approaches. If no train nodes are explicitly selected, all detected IN nodes are used.

See [docs/ADDON_DOCUMENTATION.md](docs/ADDON_DOCUMENTATION.md) for the full Train-level and tollgate setup tutorial.

For full addon usage, setup, world data, and known limitations, see [docs/ADDON_DOCUMENTATION.md](docs/ADDON_DOCUMENTATION.md). For the complete change list, see the [26.7.0 release notes](docs/RELEASE_NOTES_26.7.0.md).

Author: opaleq
Website: https://cookiecraftmods.com
Github: https://github.com/0PALEQ/MTR-Traffic-Addon

Credits: opaleq, cookiecraftmods

## License

All rights reserved. Modpacks, showcases, videos, streams, articles, and similar media are allowed with proper credit. Commercial redistribution of the mod is not allowed. See [LICENSE](LICENSE).
