"""Draws the application icon and exports it in every format the build needs.

Run from the repository root:  python packaging/make_icons.py   (requires Pillow)
"""
from pathlib import Path

from PIL import Image, ImageDraw

SIZE = 1024
SUPER = 4  # draw at 4x and downsample for smooth edges
S = SIZE * SUPER


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def draw_icon():
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Rounded square with a diagonal navy-to-blue gradient.
    gradient = Image.new("RGBA", (S, S))
    top, bottom = (13, 27, 62), (31, 111, 235)
    pixels = gradient.load()
    for y in range(S):
        for x in range(0, S, 8):
            t = min(1.0, (0.35 * x + 0.65 * y) / S)
            colour = lerp(top, bottom, t) + (255,)
            for dx in range(8):
                pixels[x + dx, y] = colour
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=255)
    image.paste(gradient, (0, 0), mask)

    draw = ImageDraw.Draw(image)
    white = (255, 255, 255, 255)
    orange = (255, 143, 0, 255)
    stroke = int(S * 0.055)

    # House outline: roof and body.
    left, right = int(S * 0.22), int(S * 0.78)
    roof_top = int(S * 0.20)
    eaves = int(S * 0.46)
    floor = int(S * 0.80)
    # Walls first, starting just under the roof line; the roof drawn on top hides the joint.
    wall_top = int(roof_top + (eaves - roof_top) * (S // 2 - left) / (S // 2 - int(S * 0.14))) + stroke
    draw.line([(left, wall_top), (left, floor), (right, floor), (right, wall_top)], fill=white, width=stroke,
              joint="curve")
    draw.line([(int(S * 0.14), eaves + stroke // 2), (S // 2, roof_top), (int(S * 0.86), eaves + stroke // 2)],
              fill=white, width=stroke, joint="curve")

    # Wi-Fi waves radiating from a point inside the house.
    cx, cy = S // 2, int(S * 0.70)
    dot = int(S * 0.045)
    draw.ellipse((cx - dot, cy - dot, cx + dot, cy + dot), fill=orange)
    for i, radius in enumerate((0.12, 0.20, 0.28)):
        r = int(S * radius)
        alpha = 255 - i * 45
        draw.arc((cx - r, cy - r, cx + r, cy + r), start=225, end=315, fill=(255, 143, 0, alpha),
                 width=int(S * 0.04))

    return image.resize((SIZE, SIZE), Image.LANCZOS)


def main():
    root = Path(__file__).resolve().parent
    icon = draw_icon()

    resources = root.parent / "src/main/resources/io/github/phlekies/smarthome/ui"
    for size in (16, 32, 48, 64, 128, 256):
        icon.resize((size, size), Image.LANCZOS).save(resources / f"icon-{size}.png", optimize=True)

    icons = root / "icons"
    icons.mkdir(exist_ok=True)
    icon.resize((512, 512), Image.LANCZOS).save(icons / "app.png", optimize=True)
    icon.save(icons / "app.ico", sizes=[(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)])
    icon.save(icons / "app.icns")
    print("icons written to", icons, "and", resources)


if __name__ == "__main__":
    main()
