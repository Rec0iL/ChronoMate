# ChronoMate logo (vector)

A hand-built vector redraw of `app/src/main/res/drawable/logo.png`. No tracing and no embedded bitmaps: every part is a circle, an arc, a tapered line or a letter outline, drawn in a `0 0 2048 2048` viewBox so the coordinates line up with the original PNG.

| File | What it is |
|---|---|
| `logo.svg` | Full colour logo on a black square |
| `logo-transparent.svg` | The same logo without the black square: the orange glow fades out to see-through |
| `logo-mono.svg` | One-colour mark for icons (ring, BB, CM). It uses `currentColor`, so setting the CSS `color` recolours it. Cropped to `300 310 1440 1440` |
| `logo-512.png`, `logo-2048.png` etc. | PNG exports rendered from the SVGs with Inkscape |
| `comparison.png` | The original next to the new render |

Re-export a PNG after editing an SVG:

```bash
inkscape logo.svg --export-type=png --export-filename=logo-2048.png -w 2048
```

## Layers (for animation)

Back to front. Every id can be selected on its own, for example `#bb` or `#ring-main`.

| id | Contents | Animation ideas |
|---|---|---|
| `background` | Black square (not in `logo-transparent.svg`) | — |
| `glow-disc` | Orange radial glow behind everything | Fade or scale up from 0 |
| `rings` → `ring-main`, `ring-outer-1…4`, `ring-mid-1…5`, `ring-inner-1…2`, `ring-core` | Each ring has a blurred `.ring-glow` path and a sharp `.ring-line` path | `.ring-line` has `pathLength="100"`: animate `stroke-dasharray: 100` / `stroke-dashoffset: 100 → 0` to draw it on. Rotate each ring group around the centre (1020, 1030) |
| `glints` → `glint-top`, `glint-bottom` | Two bright flashes on the main ring | Flash / twinkle |
| `rays` → `rays-long` (`ray-34` …) and `sparks` (`spark-30` …) | Tapered light rays and short sparks, named by angle | Scale out from the centre, stagger |
| `flare` → `flare-bloom`, `flare-streak`, `flare-cross`, `flare-core` | Lens flare at (935, 990) plus the horizontal streak | Pop-in / flash |
| `bb` → `bb-body`, `bb-shade`, `bb-reflect`, `bb-rim`, `bb-highlight`, `bb-specular` | The BB, centre (1320, 950), radius 300 | Fly in, then come to rest |
| `lettering` → `lettering-slant` → `letter-c`, `letter-m` | Italic CM. The slant is one transform on `lettering-slant` | Slide or wipe in letter by letter |

For CSS transforms on a group, set `transform-box: fill-box; transform-origin: center;` (or use the centre points above) so it scales and rotates around itself.

## Differences from the original PNG

- The original's painted look (soft blurs and grainy texture inside the rings, uneven line thickness, the many faint criss-cross lines) is simplified into clean arcs with an even glow and a smooth gradient.
- Ring ends are rounded and even in thickness. In the original they taper and fade.
- The lens flare is built from a bloom, a streak and ten spikes. It is a little softer and less spiky than the painted one.
- The BB is a gradient sphere with one highlight, a specular dot, a left rim light and a warm reflection at the bottom. The original's glossy inner reflection band is left out.
- The CM outlines follow the measured letter shapes to within a few pixels. The letters have a lighter, more even glow.
- The small four-point sparkle in the bottom right corner of the original (an image generator's watermark) was not redrawn.
- The mono version is a new simplified mark (main ring, two inner arcs, BB and CM, with gaps cut between them). The original has no single-colour version. It reads well from 32 px up. At 16 px only the ring and the BB still read.
