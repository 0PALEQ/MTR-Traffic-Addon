# Universal Road Signs

The `mtr-traffic-addon:road_sign` block renders a resource-pack-selected sign base with up to four editable text lines on top.

## In game

1. Place the Universal Road Sign. Its front faces the player who placed it.
2. Hold the MTR brush (`mtr:brush`) and right-click the block.
3. Choose a sign base with the arrow buttons.
4. On **Content**, enter the supported text lines. Disabled lines are not used by the selected base. Optionally enter a six-digit RGB color such as `#F5F5F5`; leave it empty to use the base's default.
5. On **Appearance**, optionally override the width, height, background color, and edge color.
6. Select **Import PNG...** to add artwork from your computer. **Match image ratio** adjusts the board dimensions to avoid stretching the artwork.
7. Select **Done**. The image is uploaded to the world only when the edit is saved.

The selected base ID, text, appearance overrides, and image ID are saved in the block entity and synchronized to clients. Editing is validated by the server: the player must have build permission, be near the block, and still hold the MTR brush.

## Per-sign appearance and PNG imports

Blank appearance fields inherit the selected resource-pack base. Per-sign widths accept `0.25` to `8.0` blocks and heights accept `0.25` to `4.0` blocks. The edge override colors the front border, back, and thin sides of the board. **Reset size/colors** returns all four fields to the selected base defaults without removing an imported image.

Imported artwork is drawn above the procedural colors and resource-pack texture, but below editable text. PNG alpha is preserved, so a transparent symbol can reveal the chosen background. Artwork covers the complete face; use **Match image ratio** or set matching dimensions if it should not be stretched.

For safety and predictable multiplayer packets, imported files must be valid PNG images no larger than 512 KiB, no wider or taller than 2048 pixels, and no more than 4,194,304 pixels in total. The client uploads accepted files in 24 KiB chunks to stay below Minecraft 1.20.1's packet limit, and the server validates the reassembled image again before accepting it.

Accepted images are content-addressed by SHA-256, so identical uploads are stored only once. They are kept with the world at:

```text
<world>/data/mtr-traffic-addon/road_sign_images/<sha256>.png
```

Clients receive referenced artwork from the server automatically. Players do not need the original local file or a separate resource pack after it has been saved. Unused image files are deliberately retained so removing and replacing a sign cannot accidentally delete artwork still referenced elsewhere or in an unloaded chunk.

## Adding bases with a resource pack

Definitions are client resources under:

```text
assets/<namespace>/road_signs/<name>.json
```

Without an explicit `id`, the example path `assets/my_signs/road_signs/france/blue.json` produces the selectable ID `my_signs:france/blue`.

This example uses a custom bitmap containing a border and road symbol while leaving the middle clear for player text:

```json
{
  "display_name": "French blue motorway sign",
  "texture": "my_signs:road_signs/france_blue",
  "width": 3.0,
  "height": 1.5,
  "thickness": 0.0625,
  "y_offset": 0.0,
  "back_color": "#70777C",
  "text": {
    "color": "#FFFFFF",
    "x": 0.12,
    "y": 0.14,
    "width": 0.76,
    "height": 0.72,
    "max_lines": 4,
    "alignment": "left",
    "shadow": false,
    "full_bright": false
  }
}
```

Its texture goes at:

```text
assets/my_signs/textures/road_signs/france_blue.png
```

The `textures/` prefix and `.png` suffix are added automatically when omitted. A texture covers the full front face; bake permanent arrows, shields, borders, and symbols into it, and keep the configured text region clear. Transparent pixels reveal the procedural background and border colors below it.

If `texture` is omitted, the renderer creates a clean colored board. The built-in blue, yellow, white, and red bases use this procedural mode.

## Definition fields

| Field | Default | Meaning |
| --- | ---: | --- |
| `id` | File-derived | Persistent ID stored in the block entity. |
| `display_name` | Base ID | Literal name shown in the brush screen. |
| `translation_key` | Empty | Optional language key used instead of `display_name`. |
| `texture` | None | Optional full-front PNG resource location. |
| `width` | `2.0` | Board width in blocks, clamped to `0.25`–`8.0`. |
| `height` | `1.0` | Board height in blocks, clamped to `0.25`–`4.0`. |
| `thickness` | `0.0625` | Board depth in blocks, clamped to `0.01`–`0.25`. |
| `y_offset` | `0.0` | Vertical offset from the owning block's bottom. |
| `background_color` | `#24529A` | Procedural front color. |
| `border_color` | `#FFFFFF` | Procedural border and texture underlay color. |
| `back_color` | `#70777C` | Back and edge color. |
| `border` | `0.04` | Procedural border thickness as a fraction of the shorter edge. |

The nested `text` object accepts:

| Field | Default | Meaning |
| --- | ---: | --- |
| `color` | `#FFFFFF` | Text color unless the player overrides it. |
| `x`, `y` | `0.08`, `0.10` | Text-region top-left in normalized front-face coordinates. |
| `width`, `height` | `0.84`, `0.80` | Text-region size in normalized coordinates. |
| `max_lines` | `4` | Editable/rendered lines, clamped to `1`–`4`. |
| `alignment` | `center` | `left`, `center`, or `right`. |
| `shadow` | `false` | Enables the Minecraft font shadow. |
| `full_bright` | `false` | Renders text at full light, useful for illuminated signs. |

The text is automatically scaled down to fit both the configured region and its line slots. Each line supports up to 64 Unicode code points.

## Resource-pack reloads and multiplayer

Road-sign definitions reload with client resources (`F3+T`). The server stores only the resource location, so packs can add bases without server-side registration code. For multiplayer, distribute the same pack as the server resource pack. A client missing a selected base sees the built-in blue fallback with the saved text; opening and saving the editor preserves the missing base ID unless that player selects another base.
