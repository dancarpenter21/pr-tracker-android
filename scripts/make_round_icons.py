# /// script
# dependencies = ["pillow==10.4.0"]
# ///

from pathlib import Path

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "PR-icon-1.png"
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
ADAPTIVE_FOREGROUND_SIZE = 432


def make_icon(source: Image.Image, size: int) -> Image.Image:
    crop_size = min(source.size)
    crop_x = (source.width - crop_size) // 2
    crop_y = 0
    cropped = source.crop((crop_x, crop_y, crop_x + crop_size, crop_y + crop_size))
    resized = cropped.resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")

    mask = Image.new("L", (size, size), 0)
    draw = ImageDraw.Draw(mask)
    draw.ellipse((0, 0, size - 1, size - 1), fill=255)
    resized.putalpha(mask)
    return resized


def make_adaptive_foreground(source: Image.Image) -> Image.Image:
    crop_size = min(source.size)
    crop_x = (source.width - crop_size) // 2
    crop_y = 0
    cropped = source.crop((crop_x, crop_y, crop_x + crop_size, crop_y + crop_size))
    return cropped.resize(
        (ADAPTIVE_FOREGROUND_SIZE, ADAPTIVE_FOREGROUND_SIZE),
        Image.Resampling.LANCZOS,
    ).convert("RGBA")


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    foreground = make_adaptive_foreground(source)
    foreground.save(ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground.png")

    for density, size in DENSITIES.items():
        icon = make_icon(source, size)
        output_dir = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        for filename in ("ic_launcher.png", "ic_launcher_round.png"):
            icon.save(output_dir / filename)


if __name__ == "__main__":
    main()
