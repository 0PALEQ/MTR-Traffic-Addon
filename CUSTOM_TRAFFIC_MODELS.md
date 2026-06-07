# Custom Traffic Models

This document describes the older custom traffic model format. It is kept for reference, but current vehicle visuals should use MTR vehicle resources instead.

For the current recommended workflow, use:

```text
docs/RESOURCE_PACK_AUTHORING.md
```

MTR vehicle resources are preferred because they use the same renderer path as MTR vehicles, support `.bbmodel` assets directly, and can be embedded into the mod jar under `src/main/resources`.

## Legacy custom model format

Legacy custom traffic models are client resource-pack assets loaded from:

```text
assets/<namespace>/traffic_models/*.json
```

Example:

```json
{
  "id": "example:city_bus",
  "format": "obj",
  "model": "example:models/traffic/city_bus.obj",
  "texture": "example:textures/traffic/city_bus.png",
  "scale": 1.0,
  "offset": [0.0, 0.0, 0.0],
  "rotation": [0.0, 0.0, 0.0],
  "color": "FFFFFFFF"
}
```

Use the `id` as a vehicle pool entry or as `visualId` in a server-side traffic vehicle definition only if the legacy custom model renderer is enabled in the build.

Supported formats in this build:

- `obj`: triangulated at load time, supports positions, UVs, normals, n-gon faces, `mtllib`, `usemtl`, and `map_Kd` per-face texture selection.
- `json`: simple Minecraft/Blockbench-style cuboids using `elements` with `from`/`to`, or `cubes` with `origin`/`size`.
- `bbmodel`: loaded through the same cuboid reader when the file contains direct `elements` or `cubes`; face UV rectangles and per-cuboid rotations are supported.

Recognized but not decoded yet:

- `gltf`
- `glb`

Those formats need a full glTF decoder for buffers, accessors, materials and node transforms before they can render correctly.

The built-in road vehicle resource sample lives in:

```text
resourcepacks/mtr-traffic-addon-sedan
```

The same sedan and hatchback resources are now embedded in the mod jar under:

```text
src/main/resources/assets/mtr/
src/main/resources/assets/mtr_traffic_addon_sedan/
src/main/resources/data/mtr_traffic_addon_sedan/
```

The sample registers nine `mta_sedan*` variants, including `mta_sedan_taxi`, and nine `mta_hatchback*` variants for the Traffic Dashboard vehicle pool. Both body styles use OBJ traffic model definitions for the supplied mesh renderer.
