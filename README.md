# MTR Traffic Addon

Addon for minecraft transit railway featuring car traffic generation.

## Beta Status

Current beta line: `26.7.B09a`.

This build includes MTR route traffic, traffic dashboard controls, configurable spawn/despawn connectors, vehicle and pedestrian traffic light blocks, manual/auto intersection signals, bundled sedan/taxi/hatchback/Nissan Sentra vehicle resources, and fail-open handling so stale paused traffic/intersection state does not keep MTR vehicles blocked indefinitely.

**26.7.B09a highlights:**
- Pedestrian signals blink their green indication during the yellow/clearance phase: 0.5 seconds on and 0.5 seconds off, synchronized to client world time.
- Five Nissan Sentra color variants are embedded in the built-in vehicle resources: white, red, blue, black, and brown.
- Dashboard workflow additions include inline connector-track renaming, copy/paste for spawn connector vehicle pools, and Crossing/Train intersection levels.
- Mid-route virtual vehicles can materialize anywhere inside player simulation distance. The simulator scans every departure that could still be on the route, while `maxVehicles` limits only the number materialized from each spawn. Materialization no longer depends on an outer band derived from server view distance, fixing fresh Forge/Sinytra worlds that otherwise considered only the newest, spawn-side departure.

**Earlier 26.6.B04a changes:**
- Sinytra Connector compatibility: `fabricloader` version constraint relaxed to `>=0.15.0` so the mod runs on Forge via Sinytra Connector without a crash.
- Routing over long distances: MTR graph fetch radius increased to `8192` blocks and connector route refresh radius increased to `30,000` blocks. Spawn and despawn connectors that are more than 512 blocks apart now build routes correctly.
- Dashboard responsiveness: the panel resizes with the window and GUI scale. On narrow screens the map collapses and a toggle button shows it. Spawn interval and phase green duration use inline `[−]` / `[+]` controls with the current value displayed between them.
- Dashboard intersection layout: action buttons are anchored below the signal groups list instead of overlapping it. All buttons in the map area are now clickable.

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

## Dashboard Quick Tutorial

- Rename a connector track: open `Connectors`, double-click its row, type the new name, and press Enter. Submitting an empty name restores the generated default name.
- Copy a vehicle pool: select a spawn connector, open `Vehicle Pool`, and press `Copy Vehicles`. Open another spawn connector's pool and press `Paste Vehicles`. Paste replaces the destination pool; it does not merge the two lists. This uses the dashboard's internal copy buffer, not the operating-system clipboard.
- Change an intersection level: open `Intersections` and right-click an intersection row to toggle `Crossing` / `Train`. In Train mode, press `Find Nodes`, select a node on the map, and use `Train Node: On/Off` to choose train approaches. If no train nodes are explicitly selected, all detected IN nodes are used.

See [docs/ADDON_DOCUMENTATION.md](docs/ADDON_DOCUMENTATION.md) for the full Train-level and tollgate setup tutorial.

For full addon usage, setup, world data, beta limitations, and release notes, see [docs/ADDON_DOCUMENTATION.md](docs/ADDON_DOCUMENTATION.md).

Author: opaleq
Website: https://cookiecraftmods.com
Github: https://github.com/0PALEQ/MTR-Traffic-Addon

Credits: opaleq, cookiecraftmods

## License

All rights reserved. Modpacks, showcases, videos, streams, articles, and similar media are allowed with proper credit. Commercial redistribution of the mod is not allowed. See [LICENSE](LICENSE).
