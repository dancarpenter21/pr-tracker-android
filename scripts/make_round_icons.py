# /// script
# dependencies = ["pillow==10.4.0"]
# ///

from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "PR-round.png"
DENSITIES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}
ADAPTIVE_FOREGROUND_SIZE = 432


def square_crop(source: Image.Image) -> Image.Image:
    crop_size = min(source.size)
    crop_x = (source.width - crop_size) // 2
    crop_y = 0
    return source.crop((crop_x, crop_y, crop_x + crop_size, crop_y + crop_size))


def make_legacy_icon(source: Image.Image, size: int) -> Image.Image:
    return square_crop(source).resize((size, size), Image.Resampling.LANCZOS).convert("RGBA")


def make_adaptive_foreground(source: Image.Image) -> Image.Image:
    return square_crop(source).resize(
        (ADAPTIVE_FOREGROUND_SIZE, ADAPTIVE_FOREGROUND_SIZE),
        Image.Resampling.LANCZOS,
    ).convert("RGBA")


def main() -> None:
    source = Image.open(SOURCE).convert("RGBA")
    foreground = make_adaptive_foreground(source)
    foreground.save(ROOT / "app" / "src" / "main" / "res" / "drawable-nodpi" / "ic_launcher_foreground.png")

    for density, size in DENSITIES.items():
        icon = make_legacy_icon(source, size)
        output_dir = ROOT / "app" / "src" / "main" / "res" / f"mipmap-{density}"
        for filename in ("ic_launcher.png", "ic_launcher_round.png"):
            icon.save(output_dir / filename)


if __name__ == "__main__":
    main()
