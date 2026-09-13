#!/usr/bin/env python3
"""Generate the AE2LF pattern-panel masks and native AE2 model resources.

The factory panel deliberately keeps AE2's complete native terminal rendering:
display_base owns the housing, the medium layer preserves AE2's native mask
union as the colored face, and the dark layer overlays the Recipe Ring Solver
core silhouette. The inventory model is the native pattern-terminal item model
with those three mask textures substituted. No custom item base or GUI lighting
override is emitted.
"""

from __future__ import annotations

import base64
import hashlib
import json
import uuid
from collections import Counter
from pathlib import Path

from PIL import Image


ROOT = Path(__file__).resolve().parents[1]
GENERATIONS = ("1.21.1", "26.1.2")
LAYERS = ("bright", "medium", "dark")
TINT_RGB = (242, 242, 242)
BACKGROUND_ALPHA = 255
CORE_ALPHA = {
    (186, 255, 244, 255): 96,
    (98, 245, 223, 255): 160,
    (22, 143, 138, 255): 255,
}


def core_pixels(image: Image.Image) -> list[tuple[int, int, tuple[int, int, int, int]]]:
    pixels = []
    for y in range(image.height):
        for x in range(image.width):
            pixel = image.getpixel((x, y))
            if pixel[3] and pixel[2] > pixel[0] + 20 and pixel[1] > pixel[0] + 20:
                pixels.append((x, y, pixel))
    return pixels


def empty_layer(size: tuple[int, int]) -> Image.Image:
    return Image.new("RGBA", size, (0, 0, 0, 0))


def make_background_layer(native_layers: list[Image.Image]) -> Image.Image:
    result = empty_layer(native_layers[0].size)
    for y in range(result.height):
        for x in range(result.width):
            if any(layer.getpixel((x, y))[3] > 0 for layer in native_layers):
                result.putpixel((x, y), (*TINT_RGB, BACKGROUND_ALPHA))
    return result


def make_core_layer(core_source: Image.Image) -> Image.Image:
    result = empty_layer(core_source.size)
    for x, y, pixel in core_pixels(core_source):
        if pixel not in CORE_ALPHA:
            raise SystemExit(f"unexpected Recipe Ring Solver core color: {pixel}")
        result.putpixel((x, y), (*TINT_RGB, CORE_ALPHA[pixel]))
    return result


def panel_elements() -> list[dict[str, object]]:
    return [
        {
            "from": [2, 2, 0],
            "to": [14, 14, 2],
            "faces": {
                "north": {
                    "texture": "#lightsBright",
                    "tintindex": 3,
                    "neoforge_data": {"block_light": 15, "sky_light": 15},
                }
            },
        },
        {
            "from": [2, 2, 0],
            "to": [14, 14, 2],
            "faces": {
                "north": {
                    "texture": "#lightsMedium",
                    "tintindex": 2,
                    "neoforge_data": {"block_light": 15, "sky_light": 15},
                }
            },
        },
        {
            "from": [2, 2, 0],
            "to": [14, 14, 2],
            "faces": {
                "north": {
                    "texture": "#lightsDark",
                    "tintindex": 1,
                    "neoforge_data": {"block_light": 15, "sky_light": 15},
                }
            },
        },
    ]


def panel_models(generation: str) -> tuple[dict[str, object], dict[str, object]]:
    textures = {
        "lightsBright": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_bright",
        "lightsMedium": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_medium",
        "lightsDark": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_dark",
    }
    off = {
        "parent": "ae2:part/display_off",
        "textures": textures,
    }
    on: dict[str, object] = {"textures": dict(textures)}
    if generation == "26.1.2":
        on["textures"]["particle"] = "#lightsMedium"
        on["render_type"] = "minecraft:cutout"
    on["elements"] = panel_elements()
    return off, on


def panel_item_model() -> dict[str, object]:
    return {
        "parent": "ae2:item/display_base",
        "textures": {
            "front": "ae2:part/pattern_encoding_terminal",
            "front_bright": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_bright",
            "front_medium": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_medium",
            "front_dark": "ae2lightoptimizer:part/loop_factory_pattern_encoding_panel_dark",
        },
    }


def panel_item_descriptor() -> dict[str, object]:
    return {
        "model": {
            "type": "minecraft:model",
            "model": "ae2lightoptimizer:item/loop_factory_pattern_encoding_panel",
            "tints": [
                {"type": "minecraft:constant", "value": -1},
                {"type": "ae2:color", "color": "fluix", "variant": "dark"},
                {"type": "ae2:color", "color": "fluix", "variant": "medium"},
                {"type": "ae2:color", "color": "fluix", "variant": "bright"},
                {"type": "ae2:color", "color": "fluix", "variant": "medium_bright"},
            ],
        }
    }


def write_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2) + "\n", encoding="utf-8")


def asset_record(path: Path, generation: str) -> dict[str, object]:
    return {
        "generation": generation,
        "asset": path.name,
        "path": str(path.relative_to(ROOT)).replace("\\", "/"),
        "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
    }


def validate_layer(
    output: Image.Image,
    generation: str,
    layer: str,
    expected_nonzero: int,
) -> dict[str, object]:
    output_pixels = list(output.getdata())
    nonzero = sum(1 for pixel in output_pixels if pixel[3] > 0)
    transparent = sum(1 for pixel in output_pixels if pixel[3] == 0)
    allowed_alpha = {0, BACKGROUND_ALPHA, *CORE_ALPHA.values()}
    if any(pixel[3] not in allowed_alpha for pixel in output_pixels):
        raise SystemExit(f"{generation}/{layer}: unexpected alpha value in pattern mask")
    if any(pixel[3] > 0 and pixel[:3] != TINT_RGB for pixel in output_pixels):
        raise SystemExit(f"{generation}/{layer}: pattern mask contains a non-tint pixel")
    if nonzero != expected_nonzero:
        raise SystemExit(
            f"{generation}/{layer}: expected {expected_nonzero} colored pixels, found {nonzero}"
        )
    if nonzero and transparent == 0:
        raise SystemExit(f"{generation}/{layer}: pattern mask must retain a transparent background")
    return {
        "generation": generation,
        "asset": f"loop_factory_pattern_encoding_panel_{layer}.png",
        "size": list(output.size),
        "coloredPixels": nonzero,
        "transparentPixels": transparent,
        "alphaCounts": {str(alpha): sum(1 for pixel in output_pixels if pixel[3] == alpha)
                        for alpha in sorted(allowed_alpha)},
        "colors": [[list(color), count] for color, count in Counter(output_pixels).most_common()],
    }


def update_bbmodel(generation: str, output_paths: list[Path]) -> str | None:
    path = (
        ROOT
        / "design"
        / "loop_factory"
        / generation
        / "loop_factory_pattern_encoding_panel.bbmodel"
    )
    if not path.exists():
        return None
    model = json.loads(path.read_text(encoding="utf-8"))
    template = model["textures"][0]
    textures = []
    for index, output_path in enumerate(output_paths):
        texture = dict(template)
        texture.update(
            {
                "name": output_path.name,
                "id": str(index),
                "uuid": str(uuid.uuid5(uuid.NAMESPACE_URL, f"ae2lf:{generation}:panel:{output_path.stem}")),
                "source": "data:image/png;base64,"
                + base64.b64encode(output_path.read_bytes()).decode("ascii"),
            }
        )
        textures.append(texture)
    model["textures"] = textures
    if model.get("elements"):
        element = model["elements"][0]
        element["from"] = [2, 2, 0]
        element["to"] = [14, 14, 2]
        for face, face_data in element.get("faces", {}).items():
            face_data["texture"] = 0 if face == "north" else 0
    path.write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")
    return str(path)


def main() -> None:
    report: list[dict[str, object]] = []
    for generation in GENERATIONS:
        reference_dir = ROOT / "design" / "loop_factory" / "references" / generation
        output_dir = (
            ROOT
            / "versions"
            / f"neoforge-{generation}"
            / "src"
            / "main"
            / "resources"
            / "assets"
            / "ae2lightoptimizer"
            / "textures"
            / "part"
        )
        output_dir.mkdir(parents=True, exist_ok=True)
        for obsolete in (
            "loop_factory_pattern_encoding_panel.png",
            "loop_factory_pattern_encoding_panel_item_base.png",
            "loop_factory_pattern_encoding_panel_empty.png",
            "combined-mask.png",
        ):
            path = output_dir / obsolete
            if path.exists():
                path.unlink()
        core_source = Image.open(reference_dir / "recipe_ring_solver_terminal_connected.png").convert("RGBA")
        native_layers = [
            Image.open(reference_dir / f"pattern_encoding_terminal_{layer}.png").convert("RGBA")
            for layer in LAYERS
        ]
        background = make_background_layer(native_layers)
        core = make_core_layer(core_source)
        layers = {
            "bright": background,
            "medium": empty_layer(core_source.size),
            "dark": core,
        }
        expected = {
            "bright": sum(1 for pixel in background.getdata() if pixel[3] > 0),
            "medium": 0,
            "dark": sum(1 for pixel in core.getdata() if pixel[3] > 0),
        }
        if expected["bright"] != 100:
            raise SystemExit(f"{generation}: native mask union must cover 100 pixels")
        if expected["dark"] != 32:
            raise SystemExit(f"{generation}: Recipe Ring Solver core must cover 32 pixels")
        outputs: list[Path] = []
        for layer in LAYERS:
            output = layers[layer]
            output_path = output_dir / f"loop_factory_pattern_encoding_panel_{layer}.png"
            output.save(output_path, format="PNG", optimize=False)
            outputs.append(output_path)
            report.append(validate_layer(output, generation, layer, expected[layer]))

        assets_root = ROOT / "versions" / f"neoforge-{generation}" / "src" / "main" / "resources" / "assets" / "ae2lightoptimizer"
        off, on = panel_models(generation)
        item_model = panel_item_model()
        model_paths = [
            assets_root / "models" / "part" / "loop_factory_panel_off.json",
            assets_root / "models" / "part" / "loop_factory_panel_on.json",
            assets_root / "models" / "item" / "loop_factory_pattern_encoding_panel.json",
        ]
        write_json(model_paths[0], off)
        write_json(model_paths[1], on)
        write_json(model_paths[2], item_model)
        report.extend(asset_record(path, generation) for path in model_paths)

        if generation == "26.1.2":
            descriptor_path = assets_root / "items" / "loop_factory_pattern_encoding_panel.json"
            write_json(descriptor_path, panel_item_descriptor())
            report.append(asset_record(descriptor_path, generation))

        combined = Image.new("RGBA", core_source.size, (0, 0, 0, 0))
        for output_path in outputs:
            combined.alpha_composite(Image.open(output_path).convert("RGBA"))
        if combined.getbbox() is None:
            raise SystemExit(f"{generation}: combined pattern mask is empty")

        bbmodel = update_bbmodel(generation, outputs)
        if bbmodel:
            report.append({"generation": generation, "bbmodel": bbmodel})

    report_path = ROOT / "archive" / "2026-09-09-render-fix" / "panel-mask-report.json"
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"status": "passed", "assets": len(report), "report": str(report_path)}, indent=2))


if __name__ == "__main__":
    main()
