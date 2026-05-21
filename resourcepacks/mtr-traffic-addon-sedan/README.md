# MTR Traffic Addon Sedan Resource Pack

This pack adds the supplied mesh sedan model and textures as traffic visuals for MTR Traffic Addon.
It registers MTR vehicle resource IDs for dashboard selection and OBJ traffic model definitions for rendering the supplied mesh.

## Current status

The sedan resources are now embedded in the mod jar under `src/main/resources`. This standalone pack is kept as a source/export copy and for testing resource-pack changes before they are embedded.

## Use as a resource pack

1. Put `mtr-traffic-addon-sedan.zip` or this folder in `.minecraft/resourcepacks`.
2. Enable it in Minecraft's resource pack screen.
3. Open the Traffic Dashboard and add any `mta_sedan*` variant to a spawn connector vehicle pool.

Do not enable this standalone pack together with a mod jar that already embeds the same sedan resources unless you intentionally want the external pack to override the built-in copy.

## Optional data pack use

The pack also includes `data/mtr_traffic_addon_sedan/traffic_vehicles/*.json`, which defines standalone traffic vehicles for the sedan variants. To use those definitions, install the same zip or folder into the world's `datapacks` folder as well, then reload the world or run `/reload`.

## Included resource IDs

- MTR vehicle resource: `mta_sedan`
- MTR vehicle resource: `mta_sedan_white`
- MTR vehicle resource: `mta_sedan_black`
- MTR vehicle resource: `mta_sedan_green`
- MTR vehicle resource: `mta_sedan_red`
- MTR vehicle resource: `mta_sedan_blue`
- MTR vehicle resource: `mta_sedan_brown`
- MTR vehicle resource: `mta_sedan_orange`
- Traffic vehicle definition: `sedan_01`
- Traffic vehicle definition: `sedan_white`
- Traffic vehicle definition: `sedan_black`
- Traffic vehicle definition: `sedan_green`
- Traffic vehicle definition: `sedan_red`
- Traffic vehicle definition: `sedan_blue`
- Traffic vehicle definition: `sedan_brown`
- Traffic vehicle definition: `sedan_orange`

## Authoring notes

The working sedan pack uses:

- one shared OBJ mesh for traffic rendering,
- one `.bbmodel` copy for resource metadata and future MTR renderer compatibility,
- eight 256x256 texture variants,
- no `bogie1Models` or `bogie2Models`,
- `positions: [{}]`,
- `positionsFlipped: []`,
- `condition: "NORMAL"`,
- `renderStage: "EXTERIOR"`,
- `type: "NORMAL"`,
- `modelYOffset: 0.0`.

More detailed guidance is in `docs/RESOURCE_PACK_AUTHORING.md`.
