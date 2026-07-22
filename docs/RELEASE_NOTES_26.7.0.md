# MTR Traffic Addon 26.7.0 Release Notes

- Released: 22 July 2026
- Compared with: `26.6.B04a`

Version 26.7.0 is a feature and performance release for Minecraft 1.20.1. It adds configurable universal road signs, an MTR route-path blocker, train-controlled level crossings and tollgates, recipes for every registered addon item, seven new translations, five Nissan Sentra variants, and a substantially revised traffic simulation and dashboard.

## Compatibility

- Minecraft: `1.20.1, 1.20.4, 1.21.1`
- Primary loader: Fabric
- Forge: supported through Sinytra Connector and its Fabric API compatibility layer
- MTR: built against `FABRIC-4.0.5+1.20.1`; the mod metadata accepts MTR `4.0.4` or newer
- Runtime Java: `17` or newer
- Build Java: `21` or newer for Fabric Loom `1.16.1`
- Gradle Fabric artifact: `build/libs/mtr-traffic-addon-26.7.0.jar`
- Prepared Fabric distribution filename: `build/libs/mta-26.7.0-fabric-1.20.1.jar`
- Sources artifact: `build/libs/mtr-traffic-addon-26.7.0-sources.jar`

## Highlights

- Create country-specific road signs in game with editable text, dimensions, colors, PNG artwork, and resource-pack-defined sign bases.
- Exclude individual rails from newly generated MTR depot paths without replacing the rail or its custom appearance.
- Build train-controlled level crossings with selectable train approaches, road signals, pedestrian signals, tollgate poles, and animated bars.
- Run larger traffic networks with less server-tick pressure, less rendering stutter, and more reliable mid-route vehicle materialization.
- Manage connectors and intersections through a localized, responsive dashboard with search, inline renaming, map tools, and vehicle-pool copy/paste.

## Universal Road Signs

The new `mtr-traffic-addon:road_sign` block is edited by right-clicking it with the MTR brush.

- Select from four built-in bases: blue direction, yellow temporary, white direction, and red route panel.
- Enter up to four text lines. Each line accepts up to 64 Unicode code points, allowing signs in many writing systems.
- Override the text color separately for each placed sign.
- Set the board width from `0.25` to `8.0` blocks and height from `0.25` to `4.0` blocks.
- Override the background and edge colors, or leave fields blank to inherit the selected base.
- Import a PNG from the local computer and use **Match image ratio** to avoid stretching it. PNG transparency is retained, and text is rendered above the artwork.
- Editing is server-authoritative: the server checks build permission, distance from the block, and that the player is still holding the MTR brush.

Imported PNGs are limited to 512 KiB, 2048 pixels on either axis, and 4,194,304 pixels in total. Uploads use 24 KiB chunks and are validated again by the server. Images are identified by their SHA-256 digest, so identical uploads are stored once, and are saved with the world at:

```text
<world>/data/mtr-traffic-addon/road_sign_images/<sha256>.png
```

The server automatically sends referenced images to clients. Once a sign has been saved, players do not need the original local image.

Resource packs can add sign bases under `assets/<namespace>/road_signs/*.json`. A base can define its size, thickness, vertical offset, colors, optional face texture, text region, alignment, shadow, brightness, and number of lines. Definitions reload with `F3+T`. If a client is missing the selected base, the sign uses the built-in blue fallback while preserving the saved base ID and text.

See [Universal Road Signs](../ROAD_SIGNS.md) for the complete player and resource-pack authoring guide.

## MTR Path Blocker Connector

The new `MTR Path Blocker Connector` marks an existing MTR rail as unavailable to MTR siding/depot path searches.

- Select the two endpoint nodes using the same two-click workflow as an MTR signal connector.
- Select the same rail again to remove the block.
- The rail geometry, speed, direction, resource-pack style, and normal MTR signal colors are preserved.
- A red connector overlay and action-bar feedback show the blocker's state.
- The recipe is shapeless: one red MTR signal connector plus one obsidian block.

The blocker affects new path searches only. It does not rewrite a depot path that MTR already generated and does not forcibly stop a train already using that path. Regenerate the affected MTR depot routes after adding or removing blockers.

## Train Intersections and Tollgates

Intersections now have two levels:

- `Crossing` retains the existing manual/automatic road signal groups and phase timing.
- `Train` detects approaching MTR trains and closes the road signals and tollgates as a railway level crossing.

Right-click an intersection row in the dashboard to switch its level. In Train mode, detected IN nodes can be selected as train approaches. If no approach is selected explicitly, all detected IN nodes are used. Train demand follows the train's direction and remaining route, and the crossing stays closed until the train has cleared the controlled area.

Two new blocks provide visible crossing barriers:

- `Tollgate Pole`
- `Tollgate Bar`

Place a pole inside the Train intersection or within its 24-block control margin, then attach one to seven contiguous bar blocks. The pole and connected bars animate between open and closed states with the intersection. Road and pedestrian lights bound to the same intersection cooperate with the crossing state.

Pedestrian signals now blink green during the yellow/clearance phase for 10 ticks on and 10 ticks off before turning red.

## Traffic Dashboard and User Interface

- Added translations throughout the dashboard instead of relying on English-only labels.
- Added connector search alongside the existing vehicle and intersection searches.
- Added inline connector-track renaming: double-click a connector row, type a name, and press Enter. Saving a blank name restores the generated default.
- Added `Locate` and `Pool` actions to spawn-connector rows.
- Added copy/paste for complete spawn-connector vehicle pools. Paste replaces the destination pool, removes duplicate IDs when saved, and uses an internal session buffer rather than the operating-system clipboard.
- Added Crossing/Train level labels and Train Node controls.
- Improved responsive layout across window sizes and GUI scales. Narrow screens can switch between the panel and map; wider screens keep both visible.
- Added a `Fit` map action, reusable local map-tile caching, and clearer crossing/train map markers.
- Fixed map-area click handling and intersection controls that could overlap other controls.
- Kept spawn interval and phase green duration as compact inline minus/value/plus controls.
- Added dashboard shortcuts to the project Discord and Ko-fi pages.

## Traffic Simulation, Routing, and Networking

The existing wall-clock traffic runtime received a major performance and reliability revision.

- Vehicle movement, spacing, signal decisions, materialization, and despawn checks remain on the dedicated 50 ms wall-clock simulation loop, while more shared-state and networking work has been removed from hot server-tick paths.
- Cross-thread readers use immutable snapshots.
- Per-player network filtering is asynchronous and uses a spatial index with 512-block grid cells, reducing repeated whole-world vehicle scans.
- Removed an expensive client chunk-presence check from vehicle rendering that caused CPU spikes, `Can't keep up!` warnings, freezes, and stutter in busy worlds.
- Fixed partial vehicle freezes during filtered snapshot updates.
- High-traffic networks, including networks with hundreds of materialized vehicles, place substantially less work on the server tick thread.

The deterministic virtual departure system was revised:

- The simulator scans departures that could still be travelling on a route, up to 2,048 candidates per spawn pass.
- `maxVehicles` limits the number materialized for a spawn; it no longer truncates the virtual departure history.
- Vehicles can materialize at their calculated position anywhere within player simulation distance, including midway along a route and inside visible range.
- This fixes long routes and fresh Forge/Sinytra worlds where view-distance reporting could leave only the newest spawn-side departure eligible.
- Vehicles outside every player's simulation radius or not sent to a player for the configured lifetime are removed and can later be recreated from the same virtual stream.
- New materializations check clearance from active addon vehicles and recently observed MTR vehicles. Unsafe departures are skipped instead of being retried every simulation pass.
- Despawn-connector sections are excluded from materialization.

Traffic flow and routing changes include:

- Speed-dependent following gaps and a stopping threshold for standing traffic jams.
- Indexed directed-segment lookups for route-ahead spacing across segment boundaries, avoiding repeated scans of every vehicle.
- Signal occupancy checks at signal entry boundaries rather than on every segment sharing a signal color.
- Spawn routes prefer the saved connector node direction while retaining reverse traversal as a compatibility fallback for older saves.
- Full MTR rail snapshots are captured from the simulator and refreshed periodically for more reliable routing and train detection.
- Existing long-route support continues to use an 8,192-block fallback graph request radius and a 30,000-block connector repair/pruning radius.

## MTR Interaction and Safety

- MTR vehicles can treat addon road vehicles and red addon intersection entries as blockers.
- MTR demand uses route lookahead, allowing a train or other MTR vehicle stopped before the exact entry rail to request the correct intersection group.
- Stale addon simulation or signal data fails open after a short timeout instead of holding MTR vehicles indefinitely.
- Intersections outside every player's active simulation range also fail open. This intentionally favors a usable railway over preserving remote, frozen signal state.
- Train crossings release correctly after the train leaves the controlled area.

## Vehicles

Five Nissan Sentra variants are now bundled in the mod and available in connector vehicle pools:

| Visual ID | Traffic definition |
| --- | --- |
| `mta_nissan_sentra_white` | `nissan_sentra_white` |
| `mta_nissan_sentra_red` | `nissan_sentra_red` |
| `mta_nissan_sentra_blue` | `nissan_sentra_blue` |
| `mta_nissan_sentra_black` | `nissan_sentra_black` |
| `mta_nissan_sentra_brown` | `nissan_sentra_brown` |

The shared normalized OBJ model and five textures are embedded in the mod. Existing sedan, taxi, and hatchback resources remain available.

## Crafting and Recipe Book Support

All 13 registered addon items now have survival crafting recipes and recipe-unlock advancements:

- Traffic Dashboard
- Traffic Spawn Connector
- Traffic Despawn Connector
- MTR Path Blocker Connector
- Traffic Lights Pole Bottom
- Traffic Lights Pole
- Traffic Lights Vertical Pole
- Traffic Lights Primary
- Pedestrian Lights
- Pedestrian Lights Pole
- Tollgate Pole
- Tollgate Bar
- Universal Road Sign

Recipes appear in the recipe book after their relevant ingredient criteria are met.

## Localization

Version 26.7.0 includes the following language files:

- English (`en_us`)
- Polish (`pl_pl`)
- German (`de_de`)
- Japanese (`ja_jp`)
- French (`fr_fr`)
- Spanish (`es_es`)
- Czech (`cs_cz`)
- Simplified Chinese (`zh_cn`)

The new translations cover the traffic dashboard, road signs, tollgates, connector tools, state messages, and tooltips.

## Fixes

- Fixed saved spawn/despawn connectors disappearing after a server restart when a partial graph snapshot did not contain both connector endpoints.
- Fixed CPU spikes, stutter, and integrated-server slowdowns caused by render-time chunk checks.
- Fixed several vehicle freeze, spacing, traffic-jam, long-distance spawning, early despawn, and route materialization problems.
- Fixed dashboard scaling, overlap, and click-target problems.
- Improved connector route recovery and direction handling for existing world data.
- Improved MTR vehicle direction and intersection-entry detection.
- Fixed a Minecraft 1.20.1 Forge/Sinytra Connector startup/runtime crash.

## Internal and Compatibility Changes

- Updated the compile dependency from MTR `4.0.4` to `4.0.5`.
- Kept the runtime MTR metadata requirement at `4.0.4` or newer.
- Kept Fabric Loader metadata compatible with Sinytra Connector by accepting loader `0.15.0` or newer.
- Removed the abandoned MTA-exclusive rail system and its 20-120 speed-specific connector items, renderers, networking, and assets. Spawn/despawn traffic connectors and the new MTR Path Blocker Connector are the supported connector tools.

## Upgrade Notes

1. Back up the world, especially `<world>/data/mtr-traffic-addon/`, before upgrading.
2. Remove or replace any obsolete MTA-exclusive 20-120 speed connector items before opening an important world. Those item IDs are no longer registered.
3. Existing spawn/despawn connector, intersection, and traffic-light binding data remains supported. Older node-only traffic-light bindings continue to load.
4. After placing or removing an MTR Path Blocker Connector, regenerate the affected MTR depot route so MTR performs a new path search.
5. Distribute resource packs containing custom road-sign bases to every multiplayer client, preferably as the server resource pack. Clients missing a base see the built-in blue fallback.
6. Imported road-sign PNGs are stored in the world and synchronized automatically; they do not need to be copied into client resource packs.
7. Refresh connector routes and intersection nodes after changing the underlying MTR rail network.

## Known Limitations

- Spawn density is controlled by spawn interval and `maxVehicles`; dashboard controls for changing `maxVehicles` are not yet exposed.
- Addon road quality and route availability depend on the underlying MTR rail graph.
- Fallback graph requests cover 8,192 blocks around a player. Extremely large networks can still fail to resolve when an endpoint falls outside the fetched graph.
- Automatic and Train intersections depend on recent graph and MTR vehicle observations near players and intentionally fail open when that state becomes stale or remote.
- Custom addon traffic-model loading currently focuses on OBJ models.
- A path blocker cannot rewrite an already generated MTR path or stop a train already following that path.
- Unused uploaded road-sign images are retained deliberately; they are not garbage-collected automatically.

## Related Documentation

- [Main addon documentation](ADDON_DOCUMENTATION.md)
- [Universal Road Signs](../ROAD_SIGNS.md)
- [Resource Pack Authoring](RESOURCE_PACK_AUTHORING.md)
- [Custom Traffic Models](../CUSTOM_TRAFFIC_MODELS.md)
