# MTR Traffic Addon Documentation

Version line: `26.5.B01`

MTR Traffic Addon adds lightweight road-traffic simulation on top of Minecraft Transit Railway rails. It uses MTR rail geometry as route geometry, then renders configured traffic vehicles along spawn-to-despawn routes. The addon also provides a traffic dashboard, road traffic connector tools, traffic light blocks, intersection areas, manual/auto signal phases, and built-in sedan resources.

This document is the main user and maintainer documentation for the addon. For custom vehicle model authoring, also see `docs/RESOURCE_PACK_AUTHORING.md` and `CUSTOM_TRAFFIC_MODELS.md`.

## Requirements

- Minecraft `1.20.1`
- Fabric Loader `0.19.2` or newer
- Fabric API
- Minecraft Transit Railway `4.0.4` or newer
- Java `17` or newer at runtime
- Java `21` or newer to run the current Gradle/Loom build

The published mod targets Java 17 bytecode, but Fabric Loom `1.16.1` requires Gradle itself to run on Java 21 or newer.

## Installed Items and Blocks

The addon registers one creative tab: `MTR Traffic Addon`.

Items:

- `Traffic Spawn Connector`
- `Traffic Despawn Connector`
- `Traffic Dashboard`
- `Traffic Lights Pole Bottom`
- `Traffic Lights Pole`
- `Traffic Lights Vertical Pole`
- `Traffic Lights Primary`

The two connector items inherit MTR rail modifier behavior. They create styled MTR rail sections and register those sections as traffic spawn or despawn points.

## Core Concepts

Traffic routing uses these concepts:

- Spawn connector: an MTR rail segment where addon-controlled road vehicles enter the simulated route.
- Despawn connector: an MTR rail segment where addon-controlled road vehicles leave the simulated route.
- Route: the shortest MTR graph path from a spawn connector to a despawn connector, including the connector sections.
- Vehicle pool: the list of loaded MTR/custom vehicle visual IDs allowed to spawn from a spawn connector.
- Intersection: a dashboard-defined rectangular area used to detect entry/exit nodes and apply traffic signal logic.
- IN node: an intersection boundary node where a vehicle enters the intersection.
- OUT node: an intersection boundary node where a vehicle leaves the intersection.
- Group: one or more IN node numbers that receive green together.

The addon uses MTR rails as the path graph. In practice, road lanes should be built as MTR rail paths, then hidden or styled as needed by the resource pack/world setup.

## Quick Start

1. Build or place MTR rail paths that represent the road lanes.
2. Use `Traffic Spawn Connector` to connect two MTR nodes where vehicles should enter traffic.
3. Use `Traffic Despawn Connector` to connect two MTR nodes where vehicles should exit traffic.
4. Open the `Traffic Dashboard`.
5. Select the spawn connector.
6. Open `Vehicle Pool`.
7. Add at least one loaded vehicle visual ID, such as `mta_sedan` or `mta_sedan_blue`.
8. Return to the overview and press `Refresh Routes`.
9. Make sure the spawn and despawn connectors are enabled.
10. Wait for active vehicles to appear on the route.

Current beta behavior requires a spawn connector to have a non-empty Vehicle Pool. If the Vehicle Pool is empty, the spawn point is skipped.

## Traffic Dashboard

Right-click with `Traffic Dashboard` to open the dashboard.

The dashboard has two main sections:

- `Connectors`
- `Intersections`

The map can focus on selected connectors or intersections. It can also switch between top-view and current-Y overlays.

### Connector Controls

Connector entries show saved spawn and despawn points in the current dimension.

Important controls:

- `Enable` / `Disable`: toggles the selected connector.
- `Focus Map`: centers the map on the selected connector.
- `Refresh Routes`: asks the server to refresh connector route metadata using the latest MTR graph near the player.
- `Clear Active`: removes currently active addon traffic vehicles.
- `Interval -1s` / `Interval +1s`: changes spawn interval for spawn connectors.
- `Vehicle Pool`: opens the vehicle visual selection panel for spawn connectors.

Notes:

- Spawn interval is clamped between `20` and `1200` ticks.
- The current beta mainly gates spawning by spawn interval and whether the spawn connector is occupied.
- `maxVehicles` exists in saved data/snapshots, but the dashboard buttons are currently disabled and it is not the main spawn limiter.
- Despawn connectors do not have vehicle pools.

### Vehicle Pool

Vehicle Pool controls which visual IDs may spawn from a spawn connector.

The pool lists loaded MTR vehicle resources and addon custom traffic model resources. Built-in sedan visual IDs include:

- `mta_sedan`
- `mta_sedan_white`
- `mta_sedan_black`
- `mta_sedan_green`
- `mta_sedan_red`
- `mta_sedan_blue`
- `mta_sedan_brown`
- `mta_sedan_orange`

Use `[+]` entries to add vehicles and `[-]` entries to remove vehicles. A spawn connector with no entries in its pool will not spawn traffic.

In the current implementation, the pool selects the rendered visual ID. Vehicle physics are still based on the selected runtime traffic definition used by the spawner.

## Creating Connectors

Use the connector item like an MTR rail modifier:

1. Select the first MTR node.
2. Select the second MTR node.
3. The item creates a styled MTR rail segment.
4. The addon saves a connector point at the segment midpoint.

Saved connector data includes:

- dimension ID
- midpoint position
- connector type: `SPAWN` or `DESPAWN`
- enabled state
- spawn interval
- connector start and end node coordinates
- vehicle pool

Saved connector points are stored in the world folder:

```text
data/mtr-traffic-addon/traffic_connector_points.json
```

If a connector's underlying rail no longer exists, route refresh may remove the stale connector point.

## Intersections

Intersections are rectangular areas created from the dashboard map.

To create one:

1. Open `Traffic Dashboard`.
2. Switch to `Intersections`.
3. Press `Draw Area`.
4. Click the first corner on the map.
5. Click the opposite corner on the map.
6. Select the created intersection.
7. Press `Find Nodes` to detect IN/OUT boundary nodes from the MTR graph.

Intersection data is stored in the world folder:

```text
data/mtr-traffic-addon/traffic_intersections.json
```

Each intersection stores:

- `id`
- `name`
- `dimensionId`
- bounds: `minX`, `minY`, `minZ`, `maxX`, `maxY`, `maxZ`
- `enabled`
- `autoDetectNodes`
- `signalMode`
- `phaseDurationTicks`
- `phaseOrder`
- `groups`
- `nodes`

Y bounds are saved, but current area containment is based on X/Z.

### Nodes

Nodes are detected where MTR graph edges cross the intersection boundary:

- outside to inside becomes `IN`
- inside to outside becomes `OUT`

Dashboard node controls:

- `Find Nodes`: refreshes detected nodes.
- `Node Type`: toggles selected node between `IN` and `OUT`.
- `Node # -` / `Node # +`: changes the selected node number.
- `Delete Node`: removes the selected node.

Signal logic only binds traffic lights and groups to `IN` nodes.

### Manual Signal Mode

Manual mode is phase-cycle based.

The active green set is computed from configured groups or phase order. If multiple groups exist, the cycle includes an all-red clearance interval between green phases.

Useful controls:

- `Mode: Manual` / `Mode: Auto`: toggles signal mode.
- `Add Group`: creates a group.
- `Assign Node`: adds the selected IN node to the current group.
- `Remove Node`: removes the selected IN node from the current group.
- `Green -1s` / `Green +1s`: changes the group's green duration.
- `Move Up` / `Move Down`: changes group order.

Group green durations have a minimum of `300` ticks in saved/runtime logic.

### Auto Signal Mode

Auto mode is demand based.

The auto controller:

- detects addon traffic vehicles approaching IN nodes
- detects recently simulated MTR vehicles approaching IN nodes
- queues demanded groups
- keeps the current group green while no other group has demand
- switches after a delay when another group is waiting
- uses yellow/all-red clearance before the next green
- waits for the intersection to clear before giving green

Important timing constants in the current implementation:

- demand lookahead: about `53` meters
- signal stop lookahead: about `48` meters
- stop buffer: about `5` meters
- all-red/yellow clearance: `200` ticks for manual phase clearance and `60` ticks for auto yellow
- minimum green in auto: `300` ticks
- auto switch delay: `60` ticks

Auto signal state fails open when it is stale. This prevents paused or unloaded intersection state from keeping MTR vehicles blocked indefinitely.

## Traffic Lights

Traffic light blocks can be bound to intersection IN nodes.

Supported blocks:

- `Traffic Lights Primary`
- `Traffic Lights Pole` with attached lights
- `Traffic Lights Vertical Pole` with attached lights

Binding workflow:

1. Place a traffic light block inside an intersection area.
2. Hold the MTR brush.
3. Right-click the traffic light.
4. Select an intersection IN node from the binding screen.

The traffic light then reads the node's signal state:

- red
- yellow
- green
- off/fallback

Saved bindings are stored in:

```text
data/mtr-traffic-addon/traffic_light_bindings.json
```

Bound lights update their block state server-side. Lit states emit block light and have client-side emissive/glow overlays.

## Vehicle Definitions

Traffic vehicle definitions are loaded from server data packs under:

```text
data/<namespace>/traffic_vehicles/*.json
```

Example:

```json
{
  "id": "sedan_blue",
  "type": "car",
  "lengthMeters": 4.2,
  "maxSpeedKph": 70.0,
  "accelerationMetersPerSecondSquared": 1.8,
  "brakingMetersPerSecondSquared": 3.0,
  "spawnWeight": 8,
  "visualId": "mta_sedan_blue"
}
```

Fields:

- `id`: unique traffic definition ID.
- `type`: descriptive type, such as `car` or `bus`.
- `lengthMeters`: physical length used for spacing and stopping.
- `maxSpeedKph`: maximum runtime speed.
- `spawnWeight`: positive integer required by validation.
- `visualId`: rendered MTR/custom vehicle visual ID. Defaults to `id` if omitted.
- `accelerationMetersPerSecondSquared`: optional; defaults to `1.2`.
- `brakingMetersPerSecondSquared`: optional; defaults to `2.4`.

Invalid definitions are skipped and logged.

## Built-In Vehicle Resources

The mod embeds sedan resources directly in the jar. Players do not need to install the old standalone sedan resource pack for the built-in sedans.

Embedded resources include:

- MTR custom resource metadata at `assets/mtr/mtr_custom_resources.json`
- OBJ mesh at `assets/mtr_traffic_addon_sedan/models/vehicle/sedan.obj`
- BBModel source/metadata copy at `assets/mtr_traffic_addon_sedan/models/vehicle/sedan.bbmodel`
- texture variants under `assets/mtr_traffic_addon_sedan/textures/vehicle/`
- traffic model definitions under `assets/mtr_traffic_addon_sedan/traffic_models/`

The standalone copy under `resourcepacks/mtr-traffic-addon-sedan` is kept as an export/source copy and for testing overrides. It should not be enabled alongside the mod jar unless intentionally overriding embedded resources.

## Rendering Pipeline

Client rendering tries custom traffic models first, then falls back to MTR vehicle resources.

Custom traffic model definitions live under:

```text
assets/<namespace>/traffic_models/*.json
```

Example:

```json
{
  "id": "mta_sedan_blue",
  "format": "obj",
  "model": "mtr_traffic_addon_sedan:models/vehicle/sedan.obj",
  "texture": "mtr_traffic_addon_sedan:textures/vehicle/sedan_blue.png",
  "scale": 1.0,
  "offset": [0.0, 0.0, 0.0],
  "rotation": [0.0, -90.0, 0.0],
  "color": "FFFFFFFF"
}
```

Supported custom format in the current path:

- `obj`

The renderer samples world lighting at the vehicle position. If custom rendering fails, the client falls back to the MTR resource renderer where possible.

## Runtime Behavior

The traffic manager runs on server ticks.

Main runtime steps:

1. Refresh an MTR graph snapshot near a player at intervals.
2. Refresh connector route metadata near the graph snapshot.
3. Spawn traffic vehicles from eligible spawn connectors.
4. Record recently simulated MTR vehicles for spacing/signal demand.
5. Tick auto intersections.
6. Resolve traffic vehicle spacing and signal speed limits.
7. Move traffic vehicles along their route.
8. Despawn vehicles at despawn connectors.

Graph request radius is currently `512` blocks. Connector route pruning/repair near the player uses a radius of `448` blocks.

## MTR Interaction and Fail-Open Behavior

The addon injects into MTR vehicle blocking checks. MTR vehicles may be stopped by:

- addon traffic vehicles occupying the same route section
- red intersection entries

To avoid indefinite blocking during pause menus, stalled ticks, or unloaded areas, blocker checks fail open when addon traffic ticks are stale. In that state, the addon reports no MTA blocker to MTR instead of preserving an old red or old vehicle position.

This is intentional beta behavior. It prioritizes keeping MTR usable over perfectly preserving frozen traffic-light state.

## Saved World Data

The addon writes world data under:

```text
<world>/data/mtr-traffic-addon/
```

Files:

- `traffic_connector_points.json`
- `traffic_intersections.json`
- `traffic_light_bindings.json`

These files are world-specific. Back them up before large manual edits.

## Build and Release

Local build with Java 21:

```powershell
$env:JAVA_HOME='C:\Users\opale\.jdks\ms-21.0.8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat build
```

Expected beta jar:

```text
build/libs/mtr-traffic-addon-26.5.B01.jar
```

The sources jar is also generated:

```text
build/libs/mtr-traffic-addon-26.5.B01-sources.jar
```

Before publishing a beta:

1. Build with Java 21+.
2. Confirm the jar version matches `gradle.properties`.
3. Start a local game with MTR and Fabric API installed.
4. Open an existing test world.
5. Confirm the dashboard opens.
6. Confirm at least one spawn/despawn route can spawn vehicles.
7. Confirm traffic lights bind to an intersection IN node.
8. Pause/open menus near a red intersection and confirm MTR vehicles are not permanently stuck after stale MTA ticks.

## Troubleshooting

No traffic vehicles spawn:

- Make sure at least one spawn connector and one despawn connector are enabled.
- Open the spawn connector's `Vehicle Pool` and add at least one loaded visual ID.
- Press `Refresh Routes`.
- Check that MTR rails exist between spawn and despawn connectors.
- Check server logs for graph refresh or definition loading warnings.

Vehicles spawn but disappear too soon:

- Verify the route does not immediately enter a despawn connector.
- Check connector direction and placement.

Traffic lights always red:

- Make sure the traffic light is bound to an `IN` node.
- Make sure the intersection has valid nodes.
- In auto mode, make sure at least one group contains the target IN node.
- Press `Find Nodes` after changing rail geometry.

Intersection has no nodes:

- Confirm the MTR graph snapshot includes rails crossing the intersection boundary.
- Move near the intersection and refresh routes/nodes.
- Make sure the area surrounds the crossing boundary, not only the inside road.

MTR vehicles remain blocked:

- Build/run the current beta with stale-tick fail-open changes.
- Check whether another MTR or addon vehicle is actually occupying the rail section.
- Clear addon vehicles from the dashboard.
- If the issue only happens after a long pause or far away from the intersection, capture logs and the relevant world data JSON.

Custom vehicle does not appear in Vehicle Pool:

- Confirm its MTR resource ID is loaded.
- Confirm custom traffic model JSON is under `assets/<namespace>/traffic_models/`.
- Confirm resource paths use valid namespace IDs.
- Check client logs for model loading errors.

Build fails with a Java version error:

- Set `JAVA_HOME` to JDK 21 or newer before running Gradle.
- The default runtime Java 17 is not enough for the current Loom plugin.

## Known Beta Limitations

- Spawn density control is still basic.
- `maxVehicles` is present in data but not currently exposed as an active dashboard control.
- Traffic uses MTR rail geometry, so road layout quality depends on the underlying rail graph.
- Auto intersections depend on recent vehicle observations and graph snapshots near players.
- Custom model support currently focuses on OBJ traffic models.
- Some dashboard labels and workflows are still beta-level and may change before stable release.

