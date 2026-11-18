"""Copy an AE2LF data/resource pack into a new ZIP using AE2LpT resource IDs."""

import argparse
from pathlib import Path, PurePosixPath
import re
import shutil
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED


OLD = "ae2lightoptimizer"
TEXT = {".json", ".mcmeta", ".mcfunction", ".snbt", ".md"}
RESOURCE = re.compile(rb"(?<![a-z0-9_.-])ae2lightoptimizer:")
LANGUAGE_NAMESPACE = re.compile(rb"\.ae2lightoptimizer(?=[.\"])")
PACK_FILTER_NAMESPACE = re.compile(rb'("namespace"\s*:\s*")ae2lightoptimizer(")')


def convert(entries, output):
    written = set()
    with ZipFile(output, "w", ZIP_DEFLATED) as pack:
        for name, payload in entries:
            path = PurePosixPath(name)
            if path.is_absolute() or ".." in path.parts or "\\" in name:
                raise ValueError(f"Invalid pack entry: {name}")
            parts = list(path.parts)
            for i in range(1, len(parts)):
                if parts[i - 1] in {"assets", "data"} and parts[i] == OLD:
                    parts[i] = "ae2lpt"
            target = "/".join(parts)
            if target in written:
                raise ValueError(f"Two entries map to {target}")
            written.add(target)
            if path.suffix in TEXT:
                payload = RESOURCE.sub(b"ae2lpt:", payload)
                if "lang" in parts:
                    payload = LANGUAGE_NAMESPACE.sub(b".ae2lpt", payload)
                if path.suffix == ".mcmeta":
                    payload = PACK_FILTER_NAMESPACE.sub(rb"\g<1>ae2lpt\2", payload)
            pack.writestr(target, payload)
    if "pack.mcmeta" not in written:
        raise ValueError("Missing pack.mcmeta at the root")


def migrate(source, destination):
    if destination.exists() or (source.is_dir() and destination.resolve().is_relative_to(source.resolve())):
        raise ValueError("Choose a new ZIP outside the input pack")
    with TemporaryDirectory(dir=destination.parent) as tmp:
        staged = Path(tmp) / "pack.zip"
        if source.is_dir():
            convert(((p.relative_to(source).as_posix(), p.read_bytes())
                     for p in source.rglob("*") if p.is_file()), staged)
        else:
            with ZipFile(source) as pack:
                convert(((e.filename, pack.read(e)) for e in pack.infolist() if not e.is_dir()), staged)
        with destination.open("xb") as out, staged.open("rb") as stream:
            shutil.copyfileobj(stream, out)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    args = parser.parse_args()
    migrate(args.source, args.destination)
    print(args.destination)
