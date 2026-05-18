# Custom Traffic Models

Custom traffic models are client resource-pack assets loaded from:

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

Use the `id` as a vehicle pool entry or as `visualId` in a server-side traffic vehicle definition.

Supported formats in this build:

- `obj`: triangulated at load time, supports positions, UVs, normals and n-gon faces.
- `json`: simple Minecraft/Blockbench-style cuboids using `elements` with `from`/`to`, or `cubes` with `origin`/`size`.
- `bbmodel`: loaded through the same cuboid reader when the file contains direct `elements` or `cubes`.

Recognized but not decoded yet:

- `gltf`
- `glb`

Those formats need a full glTF decoder for buffers, accessors, materials and node transforms before they can render correctly.
