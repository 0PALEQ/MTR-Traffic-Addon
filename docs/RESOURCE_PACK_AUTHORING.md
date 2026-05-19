# MTR Traffic Addon Resource Pack Authoring

This project can load MTR vehicle visuals from normal Minecraft resource packs, but the preferred release setup is to embed stable addon assets directly in the mod jar under `src/main/resources`.

## Recommended Layout

For an external resource pack:

```text
pack.mcmeta
assets/
  mtr/
    mtr_custom_resources.json
  your_namespace/
    models/vehicle/example.bbmodel
    properties/definition/origin.json
    properties/vehicle/example.json
    textures/vehicle/example.png
data/
  your_namespace/
    traffic_vehicles/example.json
```

For built-in mod resources, copy the same `assets/` and `data/` contents into:

```text
src/main/resources/assets/...
src/main/resources/data/...
```

Do not copy `pack.mcmeta` into the mod resources. The mod jar is already a resource container.

## MTR Vehicle Resource

`assets/mtr/mtr_custom_resources.json` registers the visual models that MTR can load. A simple road vehicle should usually have exactly one body model and no bogie models:

```json
{
  "vehicles": [
    {
      "id": "mta_sedan",
      "name": "MTA Sedan",
      "color": "F2F2F2",
      "transportMode": "TRAIN",
      "length": 5.625,
      "width": 2.0,
      "models": [
        {
          "modelResource": "mtr_traffic_addon_sedan:models/vehicle/sedan.bbmodel",
          "textureResource": "mtr_traffic_addon_sedan:textures/vehicle/sedan.png",
          "modelPropertiesResource": "mtr_traffic_addon_sedan:properties/vehicle/sedan.json",
          "positionDefinitionsResource": "mtr_traffic_addon_sedan:properties/definition/origin.json",
          "flipTextureV": false
        }
      ],
      "bogie1Position": -1.8,
      "bogie2Position": 1.8,
      "couplingPadding1": 0.0,
      "couplingPadding2": 0.0,
      "hasGangway1": false,
      "hasGangway2": false,
      "hasBarrier1": false,
      "hasBarrier2": false
    }
  ],
  "signs": [],
  "rails": [],
  "objects": [],
  "lifts": []
}
```

Road vehicles should not define `bogie1Models` or `bogie2Models` unless those are real separate visible wheel/bogie models. Do not point bogie models at the full car body, and do not rely on an empty bogie properties file to hide them; MTR can still cache/render unexpected model data.

## Model Properties

For a single Blockbench group named `main`:

```json
{
  "modelYOffset": -0.75,
  "parts": [
    {
      "names": ["main"],
      "positionDefinitions": ["origin"],
      "condition": "NORMAL",
      "renderStage": "EXTERIOR",
      "type": "NORMAL"
    }
  ]
}
```

Rules:

- `names` must match exported Blockbench group names from the `.bbmodel`.
- Use `condition: "NORMAL"` for always-visible exterior road vehicles.
- Use `renderStage: "EXTERIOR"` for the body.
- Include `type: "NORMAL"` for normal static geometry.
- Tune vertical placement with `modelYOffset`; for the current sedan, `-0.75` places it correctly on the road.

## Position Definitions

A normal non-flipped body should use one normal position and no flipped positions:

```json
{
  "positionDefinitions": [
    {
      "name": "origin",
      "positions": [{}],
      "positionsFlipped": []
    }
  ]
}
```

Do not put `{}` in both `positions` and `positionsFlipped` for the same part. That can render the same model twice, with one copy facing the opposite direction.

Use `positionsFlipped` only for parts that are intentionally mirrored/flipped, and leave `positions` empty for those parts.

## Texture Variants

For multiple colors using the same model:

1. Add one MTR vehicle resource per texture, for example `mta_sedan`, `mta_sedan_white`, `mta_sedan_green`, and `mta_sedan_red`.
2. Reuse the same `.bbmodel`, model properties, and position definitions.
3. Change only `textureResource`, `id`, `name`, `color`, and optional tags.
4. Add matching traffic vehicle definitions under `data/<namespace>/traffic_vehicles/*.json`.

Example traffic definition:

```json
{
  "id": "sedan_white",
  "type": "car",
  "lengthMeters": 4.8,
  "maxSpeedKph": 65.0,
  "accelerationMetersPerSecondSquared": 1.8,
  "brakingMetersPerSecondSquared": 3.0,
  "spawnWeight": 20,
  "visualId": "mta_sedan_white"
}
```

## Embedding In The Mod

Once a pack is stable, embed it:

1. Copy `assets/mtr/mtr_custom_resources.json` into `src/main/resources/assets/mtr/mtr_custom_resources.json`.
2. Copy the custom asset namespace into `src/main/resources/assets/<namespace>/`.
3. Copy traffic definitions into `src/main/resources/data/<namespace>/traffic_vehicles/`.
4. Build the mod jar.
5. Verify the jar contains the embedded files:

```powershell
jar tf build\libs\mtr-traffic-addon-26.5.ALPHA.jar | Select-String -Pattern "mtr_custom_resources|mtr_traffic_addon_sedan|sedan"
```

After embedding, remove old external copies of the same pack from `.minecraft/resourcepacks`; stale external packs can override the embedded resources.

## Troubleshooting

Vehicle renders twice, one forward and one backward:

- Check `positionsFlipped`; for a normal body it must be `[]`, not `[{}]`.
- Check that the vehicle has no `bogie1Models` or `bogie2Models` pointing at the full body model.
- Check that only one copy of the resource pack is active.

Vehicle levitates:

- Adjust `modelYOffset` in the vehicle properties.
- Do not compensate in renderer code unless every MTR vehicle resource has the same systematic offset.

Blue boxes or placeholder rendering:

- The visual ID in `data/.../traffic_vehicles/*.json` must match an MTR vehicle `id` from `mtr_custom_resources.json`.
- Reload resources after changing IDs.
- Clear existing traffic vehicles if they were spawned with old visual IDs.

Parts missing from normal MTR vehicles:

- Do not special-case the global renderer for one custom car if that changes how all MTR vehicle resources queue their render stages.
- Fix per-pack structure first: parts, position definitions, and bogie definitions.

## Current Built-In Sedan IDs

- `mta_sedan`
- `mta_sedan_white`
- `mta_sedan_green`
- `mta_sedan_red`
