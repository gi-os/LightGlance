#!/usr/bin/env python3
"""
Regenerate the Glance launcher icon.

The mark is the app itself: three glyphs in a row - filled dot, ring, square - which
is exactly what the ambient surface draws. White on black, matching the icon language
of the sibling Light Phone III tools.

Geometry is defined once in the 108x108 adaptive-icon canvas and emitted twice, as
Android vector paths and as raster fallbacks. The row spans 62 units so it clears the
66-unit circular mask that adaptive icons are cropped to.

    python3 scripts/generate_icon.py

Needs Pillow. Rewrites app/src/main/res/{drawable,mipmap-*}.
"""

from __future__ import annotations

import os

from PIL import Image, ImageDraw

RES = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "res")

CANVAS = 108
CENTER = CANVAS / 2.0
GLYPH = 14.0          # bounding box of one glyph
GAP = 10.0
STROKE = 2.2
ROW = 3 * GLYPH + 2 * GAP   # 62, inside the 66 mask circle

# Left edge of each glyph box.
XS = [CENTER - ROW / 2.0 + i * (GLYPH + GAP) for i in range(3)]
TOP = CENTER - GLYPH / 2.0

DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}


def circle_path(cx: float, cy: float, r: float) -> str:
    """Two half-arcs; vector drawables have no circle primitive."""
    return (
        f"M{cx - r:.2f},{cy:.2f} "
        f"a{r:.2f},{r:.2f} 0 1,0 {2 * r:.2f},0 "
        f"a{r:.2f},{r:.2f} 0 1,0 {-2 * r:.2f},0 Z"
    )


def rect_path(x: float, y: float, w: float, h: float) -> str:
    return f"M{x:.2f},{y:.2f} h{w:.2f} v{h:.2f} h{-w:.2f} Z"


def foreground_vector() -> str:
    r = GLYPH / 2.0
    dot = circle_path(XS[0] + r, CENTER, r)
    ring = circle_path(XS[1] + r, CENTER, r - STROKE / 2.0)
    side = GLYPH * 0.86
    off = (GLYPH - side) / 2.0
    square = rect_path(XS[2] + off, TOP + off, side, side)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="{dot}" />
    <path
        android:strokeColor="#FFFFFFFF"
        android:strokeWidth="{STROKE}"
        android:fillColor="#00000000"
        android:pathData="{ring}" />
    <path android:fillColor="#FFFFFFFF" android:pathData="{square}" />
</vector>
"""


BACKGROUND_VECTOR = """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:fillColor="#FF000000" android:pathData="M0,0h108v108h-108z" />
</vector>
"""

ADAPTIVE = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
"""


# The legacy raster icon is not cropped to the adaptive-icon mask circle, so the same
# geometry drawn at 1:1 looks lost in the corners. Fill more of the tile.
RASTER_SCALE = 1.3


def raster(px: int) -> Image.Image:
    """Draw at 8x and downsample; Pillow has no antialiased primitives."""
    ss = 8
    size = px * ss
    k = size / CANVAS * RASTER_SCALE
    pad = (size - CANVAS * k) / 2.0

    def sx(v: float) -> float:
        return pad + v * k

    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    d = ImageDraw.Draw(img)
    r = GLYPH / 2.0
    cy = sx(CENTER)

    cx = sx(XS[0] + r)
    rr = r * k
    d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 255, 255, 255))

    # Pillow strokes an ellipse entirely inside the bounding box rather than centred on
    # it, so the box is the OUTER edge here, not the centreline the vector path uses.
    cx = sx(XS[1] + r)
    rr = r * k
    d.ellipse(
        [cx - rr, cy - rr, cx + rr, cy + rr],
        outline=(255, 255, 255, 255),
        width=max(1, round(STROKE * k)),
    )

    side = GLYPH * 0.86
    off = (GLYPH - side) / 2.0
    x0, y0 = sx(XS[2] + off), sx(TOP + off)
    d.rectangle([x0, y0, x0 + side * k, y0 + side * k], fill=(255, 255, 255, 255))

    return img.resize((px, px), Image.LANCZOS)


def write(path: str, text: str) -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(text)
    print("wrote", os.path.relpath(path, RES))


def main() -> None:
    write(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), foreground_vector())
    write(os.path.join(RES, "drawable", "ic_launcher_background.xml"), BACKGROUND_VECTOR)
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher.xml"), ADAPTIVE)
    write(os.path.join(RES, "mipmap-anydpi-v26", "ic_launcher_round.xml"), ADAPTIVE)

    for name, px in DENSITIES.items():
        out = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out, exist_ok=True)
        img = raster(px)
        img.save(os.path.join(out, "ic_launcher.png"))
        img.save(os.path.join(out, "ic_launcher_round.png"))
        print(f"wrote mipmap-{name}/ic_launcher.png ({px}px)")


if __name__ == "__main__":
    main()
