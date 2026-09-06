"""Resolve model textures against the addon and its pinned AE2 runtime dependency."""
import json
import struct
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
for generation, ae_version in (("1.21.1", "19.2.17"), ("26.1.2", "26.1.10-beta")):
    resources = ROOT / f"versions/neoforge-{generation}/src/main/resources"
    cache = ROOT / f".gradle-user-home/{generation}/caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/{ae_version}"
    jars = [p for p in cache.rglob("*.jar") if p.name == f"appliedenergistics2-{ae_version}.jar"]
    assert len(jars) == 1, jars
    checked = set()
    with zipfile.ZipFile(jars[0]) as ae:
        for path in (resources / "assets/ae2lightoptimizer/models").rglob("*.json"):
            model = json.loads(path.read_text(encoding="utf-8"))
            for ref in model.get("textures", {}).values():
                if ref.startswith("#"):
                    continue
                namespace, target = ref.split(":", 1)
                entry = f"assets/{namespace}/textures/{target}.png"
                if namespace == "minecraft":
                    continue
                data = ae.read(entry) if namespace == "ae2" else (resources / entry).read_bytes()
                assert data[:8] == b"\x89PNG\r\n\x1a\n", entry
                width, height = struct.unpack(">II", data[16:24])
                assert width > 0 and height > 0, entry
                checked.add(entry)
        for recipe in (resources / "data/ae2lightoptimizer/recipe").rglob("*.json"):
            json.loads(recipe.read_text(encoding="utf-8"))
        for item in ("crafting_ripper", "loop_card"):
            for language in ("en_us", "zh_cn"):
                lang = json.loads((resources / f"assets/ae2lightoptimizer/lang/{language}.json").read_text(encoding="utf-8"))
                prefix = "block" if item == "crafting_ripper" else "item"
                assert lang[f"{prefix}.ae2lightoptimizer.{item}"]
            for prefix in ("", "_zh_cn/"):
                guide = resources / f"assets/ae2lightoptimizer/ae2guide/{prefix}items-blocks-machines/{item}.md"
                text = guide.read_text(encoding="utf-8")
                assert f"item_ids:\n- ae2lightoptimizer:{item}" in text
                assert f'<RecipeFor id="ae2lightoptimizer:{item}" />' in text
    print(f"{generation}: {len(checked)} unique runtime texture references resolved; recipes and new bilingual guides valid")
