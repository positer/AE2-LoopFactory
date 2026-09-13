"""Read-only byte comparison of a frozen production JAR and ModDev's main source set."""

import argparse
import hashlib
import json
from pathlib import Path
import sys
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def verify(generation, production):
    root = Path(__file__).resolve().parents[2]
    project = root / "versions" / ("neoforge-" + generation)
    classes = project / "build/classes/java/main"
    resources = project / "build/resources/main"
    report = {
        "generation": generation,
        "production": str(production.resolve()),
        "classes": str(classes),
        "resources": str(resources),
        "mode": "ModDev main source set; exact JAR entry bytes and complete class/resource inventories; generated manifest excluded",
        "status": "failed",
    }
    try:
        report["productionSha256"] = sha256(production.read_bytes())
        expected_classes, expected_resources, manifest, failures = set(), set(), [], []
        with zipfile.ZipFile(production) as archive:
            seen = set()
            for entry in archive.infolist():
                name = entry.filename
                if entry.is_dir() or name == "META-INF/MANIFEST.MF":
                    continue
                if name in seen:
                    failures.append("Duplicate JAR entry: " + name)
                    continue
                seen.add(name)
                if name.startswith(("com/example/ae2loprobe/", "com/example/ae2lfprobe/")) or name.endswith(".jar"):
                    failures.append("Helper or nested JAR in production: " + name)
                is_class = name.endswith(".class")
                (expected_classes if is_class else expected_resources).add(name)
                candidate = (classes if is_class else resources) / name
                jar_hash = sha256(archive.read(entry))
                runtime_hash = sha256(candidate.read_bytes()) if candidate.is_file() else None
                manifest.append({"entry": name, "jarSha256": jar_hash, "runtimeSha256": runtime_hash})
                if runtime_hash != jar_hash:
                    failures.append("Missing or different runtime entry: " + name)
        actual_classes = {p.relative_to(classes).as_posix() for p in classes.rglob("*.class")}
        actual_resources = {
            p.relative_to(resources).as_posix() for p in resources.rglob("*")
            if p.is_file() and p.relative_to(resources).as_posix() != "META-INF/MANIFEST.MF"
        }
        for label, expected, actual in (("classes", expected_classes, actual_classes),
                                       ("resources", expected_resources, actual_resources)):
            for name in sorted(actual - expected):
                failures.append("Runtime " + label + " entry absent from frozen JAR: " + name)
        if not expected_classes:
            failures.append("Production JAR has no classes")
        report.update(classCount=len(expected_classes), resourceCount=len(expected_resources),
                      runtimeClassCount=len(actual_classes), runtimeResourceCount=len(actual_resources),
                      entries=manifest, failures=failures)
        # Detect replacement while this read-only comparison was in progress.
        if sha256(production.read_bytes()) != report["productionSha256"]:
            failures.append("Production JAR changed during comparison")
        if not failures:
            report["status"] = "passed"
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        report["failure"] = str(error)
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--generation", choices=("1.21.1", "26.1.2"), required=True)
    parser.add_argument("--production", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True)
    args = parser.parse_args()
    report = verify(args.generation, args.production)
    # Never replace an earlier observation, including a failed preflight.
    with args.report.open("x", encoding="utf-8") as output:
        json.dump(report, output, ensure_ascii=False, indent=2)
        output.write("\n")
    print(f"Runtime identity {report['status']}: {args.generation}; {args.report}")
    for failure in report.get("failures", [])[:8]:
        print(failure, file=sys.stderr)
    if "failure" in report:
        print(report["failure"], file=sys.stderr)
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
