# MTR Traffic Addon Sedan Resource Pack

This pack adds the supplied sedan model and texture as a traffic visual for MTR Traffic Addon.
It uses MTR's vehicle resource renderer only.

## Current status

The sedan resources are now embedded in the mod jar under `src/main/resources`. This standalone pack is kept as a source/export copy and for testing resource-pack changes before they are embedded.

## Use as a resource pack

1. Put `mtr-traffic-addon-sedan.zip` or this folder in `.minecraft/resourcepacks`.
2. Enable it in Minecraft's resource pack screen.
3. Open the Traffic Dashboard and add any sedan variant to a spawn connector vehicle pool.

Do not enable this standalone pack together with a mod jar that already embeds the same sedan resources unless you intentionally want the external pack to override the built-in copy.

## Optional data pack use

The pack also includes `data/mtr_traffic_addon_sedan/traffic_vehicles/*.json`, which defines standalone traffic vehicles for the sedan variants. To use those definitions, install the same zip or folder into the world's `datapacks` folder as well, then reload the world or run `/reload`.

## Included resource IDs

- MTR vehicle resource: `mta_sedan`
- MTR vehicle resource: `mta_sedan_white`
- MTR vehicle resource: `mta_sedan_green`
- MTR vehicle resource: `mta_sedan_red`
- Traffic vehicle definition: `sedan_01`
- Traffic vehicle definition: `sedan_white`
- Traffic vehicle definition: `sedan_green`
- Traffic vehicle definition: `sedan_red`

## Authoring notes

The working sedan pack uses:

- one body model per variant,
- no `bogie1Models` or `bogie2Models`,
- `positions: [{}]`,
- `positionsFlipped: []`,
- `condition: "NORMAL"`,
- `renderStage: "EXTERIOR"`,
- `type: "NORMAL"`,
- `modelYOffset: -0.75`.

More detailed guidance is in `docs/RESOURCE_PACK_AUTHORING.md`.
