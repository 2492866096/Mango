#!/usr/bin/env python3
"""Regenerate the six supplied launcher artworks without drawing new content.

Requires Pillow. Sources in docs/app-icons/source are unchanged user JPG files.
No Gradle, Android build, network service, or AI image generation is involved.
"""

from collections import deque
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "TMessagesProj/src/main/res"
SOURCES = ROOT / "docs/app-icons/source"
PREVIEW = ROOT / "build/previews/launcher-icons-contact-sheet.png"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
# slug, label, launcher resource, layer prefix, original filename
ICONS = (
    ("galaxy", "星空 · 默认", "ic_launcher", "icon", "2026-10-03 08.35.52.jpg"),
    ("pink", "粉色", "icon_6_launcher", "icon_6", "2026-10-03 08.35.43.jpg"),
    ("gold", "黑金", "icon_4_launcher", "icon_4", "2026-10-03 08.36.00.jpg"),
    ("moon", "月夜", "icon_3_launcher", "icon_3", "2026-10-03 08.35.55.jpg"),
    ("speed", "极速", "icon_5_launcher", "icon_5", "2026-10-03 08.35.57.jpg"),
    ("neon", "霓虹", "icon_2_launcher", "icon_2", "2026-10-03 08.36.05.jpg"),
)
LANCZOS = Image.Resampling.LANCZOS
# Android's adaptive layers are 108 dp; the visible mask spans 72 dp. A 64 dp
# artwork keeps the character (including its tail) in the central 66 dp safe
# circle. Decorative corners may be masked, but the character is not enlarged
# to fill the entire 108 dp layer (which would crop the head/body on launchers).
ARTWORK_DP = 64


def without_exterior_white(source):
    """Remove only the edge-connected white photographic matte, not white eyes.

    Retain the supplied pixels and colors; unmatte the pale exterior shadow
    onto transparency so it does not leave a white box on circular launchers.
    Gold has an intentional dark background and passes through unchanged.
    """
    image = source.convert("RGBA")
    minimum = ImageChops.darker(ImageChops.darker(source.getchannel("R"), source.getchannel("G")), source.getchannel("B"))
    candidates = minimum.point(lambda value: 255 if value >= 160 else 0)
    width, height = source.size
    flood = candidates.load()
    queue = deque()
    for x in range(width):
        for y in (0, height - 1):
            if flood[x, y] == 255:
                flood[x, y] = 128
                queue.append((x, y))
    for y in range(height):
        for x in (0, width - 1):
            if flood[x, y] == 255:
                flood[x, y] = 128
                queue.append((x, y))
    pixels = image.load()
    while queue:
        x, y = queue.popleft()
        red, green, blue, _ = pixels[x, y]
        alpha = round((255 - min(red, green, blue)) * 255 / 95)
        if alpha == 0:
            pixels[x, y] = (0, 0, 0, 0)
        else:
            corrected = tuple(max(0, min(255, round(255 + (value - 255) * 255 / alpha))) for value in (red, green, blue))
            pixels[x, y] = (*corrected, alpha)
        for nx, ny in ((x - 1, y), (x + 1, y), (x, y - 1), (x, y + 1)):
            if 0 <= nx < width and 0 <= ny < height and flood[nx, ny] == 255:
                flood[nx, ny] = 128
                queue.append((nx, ny))
    return image


def foreground(artwork, scale):
    size = round(108 * scale)
    art_size = round(ARTWORK_DP * scale)
    result = Image.new("RGBA", (size, size))
    offset = (size - art_size) // 2
    result.alpha_composite(artwork.resize((art_size, art_size), LANCZOS), (offset, offset))
    return result


def softened_artwork(source):
    """Remove the outside photo frame, preserving the original character.

    This broad protection polygon encloses the supplied character's eyes,
    teeth, black outlines and tail; it is not a newly drawn character mask.
    Smoothly fade only the surrounding artwork into its extended background.
    """
    image = without_exterior_white(source)
    mask = Image.new("L", source.size)
    pixels = mask.load()
    width, height = source.size
    for y in range(height):
        for x in range(width):
            distance = min(x, y, width - 1 - x, height - 1 - y)
            value = max(0, min(1, (distance - 80) / 220))
            pixels[x, y] = round(value * value * (3 - 2 * value) * 255)
    protection = Image.new("L", source.size)
    ImageDraw.Draw(protection).polygon(
        [(140, 420), (365, 175), (905, 165), (1015, 450), (1125, 1020),
         (1125, 1115), (340, 1120), (270, 770), (135, 620)], fill=255)
    protection = protection.filter(ImageFilter.GaussianBlur(14))
    mask = ImageChops.lighter(mask, protection)
    image.putalpha(ImageChops.multiply(image.getchannel("A"), mask))
    return image


def background_texture(source, size):
    """Extend original edge textures instead of introducing a thick solid rim."""
    crop = source.crop((180, 180, 1074, 1074))
    core_size = round(size * 60 / 108 * 894 / 1254)
    core = crop.resize((core_size, core_size), LANCZOS)
    image = Image.new("RGB", (size, size))
    offset = (size - core_size) // 2
    end = offset + core_size
    image.paste(core, (offset, offset))
    image.paste(core.crop((0, 0, core_size, 1)).resize((core_size, offset)), (offset, 0))
    image.paste(core.crop((0, core_size - 1, core_size, core_size)).resize((core_size, size - end)), (offset, end))
    image.paste(image.crop((offset, 0, offset + 1, size)).resize((offset, size)), (0, 0))
    image.paste(image.crop((end - 1, 0, end, size)).resize((size - end, size)), (end, 0))
    return image.filter(ImageFilter.GaussianBlur(size * 8 / 108))


def masked_icon(artwork, source, size, shape="circle"):
    """Simulate the central adaptive 72 dp mask at high sampling resolution."""
    work_size = size * 4
    layer_size = round(work_size * 108 / 72)
    layer = background_texture(source, layer_size).convert("RGBA")
    art_size = round(layer_size * ARTWORK_DP / 108)
    offset = (layer_size - art_size) // 2
    layer.alpha_composite(artwork.resize((art_size, art_size), LANCZOS), (offset, offset))
    crop = (layer_size - work_size) // 2
    result = layer.crop((crop, crop, crop + work_size, crop + work_size))
    mask = Image.new("L", (work_size, work_size))
    draw = ImageDraw.Draw(mask)
    if shape == "circle":
        draw.ellipse((0, 0, work_size - 1, work_size - 1), fill=255)
    else:
        draw.rounded_rectangle((0, 0, work_size - 1, work_size - 1), radius=work_size * 0.23, fill=255)
    result.putalpha(mask)
    return result.resize((size, size), LANCZOS)


def save_png(image, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, optimize=True)


def make_contact_sheet(images):
    sheet = Image.new("RGB", (1440, 640), "#F4F5F8")
    draw = ImageDraw.Draw(sheet)
    font_path = Path("/System/Library/Fonts/STHeiti Medium.ttc")
    font = ImageFont.truetype(str(font_path), 23) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 19) if font_path.exists() else font
    draw.text((30, 22), "新应用图标 · 6 款去边精修", font=font, fill="#192033")
    draw.text((30, 64), "去掉额外黑框和白边 · 保留人物原始描边（素材预览，非实机截图）", font=small, fill="#576172")
    for index, (label, source, artwork) in enumerate(images):
        x = index * 240 + 20
        draw.text((x, 118), label, font=font, fill="#192033")
        preview_icon = masked_icon(artwork, source, 200, "squircle")
        sheet.paste(preview_icon, (x, 158), preview_icon)
        draw.text((x, 369), "设置页 / 圆角桌面", font=small, fill="#576172")
        round_icon = masked_icon(artwork, source, 164)
        sheet.paste(round_icon, (x + 18, 413), round_icon)
        draw.text((x, 589), "圆形桌面", font=small, fill="#576172")
    PREVIEW.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREVIEW, optimize=True)


def main():
    images = []
    for slug, label, launcher, prefix, filename in ICONS:
        source = Image.open(SOURCES / filename).convert("RGB")
        if source.size != (1254, 1254):
            raise ValueError(f"Unexpected source dimensions: {filename}: {source.size}")
        artwork = softened_artwork(source)
        save_png(masked_icon(artwork, source, 384, "squircle"), RES / "drawable-nodpi" / f"mango_icon_{slug}.png")
        for density, scale in DENSITIES.items():
            folder = RES / f"mipmap-{density}"
            size = round(48 * scale)
            legacy = masked_icon(artwork, source, size, "squircle")
            save_png(legacy, folder / f"{launcher}.png")
            save_png(masked_icon(artwork, source, size), folder / f"{launcher}_round.png")
            save_png(foreground(artwork, scale), folder / f"{prefix}_foreground.png")
            save_png(background_texture(source, round(108 * scale)), folder / f"{prefix}_background_texture.png")
            if slug == "galaxy":
                save_png(legacy, RES / f"drawable-{density}" / "ic_launcher_dr.png")
        drawable = RES / "drawable" / f"{prefix}_background.xml"
        drawable.write_text(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Extend the supplied image texture; do not add a solid black outer frame. -->
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@mipmap/{prefix}_background_texture"
    android:gravity="fill"
    android:filter="true" />
''')
        adaptive = f'''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/{prefix}_background" />
    <foreground android:drawable="@mipmap/{prefix}_foreground" />
</adaptive-icon>
'''
        for suffix in ("", "_round"):
            (RES / "mipmap-anydpi-v26" / f"{launcher}{suffix}.xml").write_text(adaptive)
        images.append((label, source, artwork))
    make_contact_sheet(images)
    print(f"Generated 6 icon sets in {RES}")
    print(f"Preview: {PREVIEW}")


if __name__ == "__main__":
    main()
