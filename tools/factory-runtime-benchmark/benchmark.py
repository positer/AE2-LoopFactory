"""比较给定 Git 基线与工作树的解释器分配量；结果是微基准，不是整服性能。"""
import argparse
import json
from pathlib import Path
import subprocess

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--baseline", required=True)
parser.add_argument("--java-home", required=True, type=Path)
parser.add_argument("--output", required=True, type=Path)
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
output = args.output.resolve()
output.mkdir(parents=True, exist_ok=False)
source = repo / "shared/src/main/java/com/example/ae2lightoptimizer/factory"
baseline = subprocess.check_output(["git", "rev-parse", "--verify", args.baseline + "^{commit}"], cwd=repo, text=True).strip()
results = {"baseline": baseline, "scope": "32 call frames, 202 instructions per tick, 2000 warmup ticks, 5000 measured ticks"}
for label in ("before", "after"):
    dest = output / label
    dest.mkdir()
    inputs = []
    for path in sorted(source.glob("*.java")):
        data = path.read_bytes() if label == "after" else subprocess.check_output(
            ["git", "show", baseline + ":" + path.relative_to(repo).as_posix()], cwd=repo)
        target = dest / path.name
        target.write_bytes(data)
        inputs.append(str(target))
    subprocess.run([str(args.java_home / "bin/javac.exe"), "-d", str(dest), *inputs,
                    str(Path(__file__).with_name("FactoryPerf.java"))], check=True)
    raw = subprocess.check_output([str(args.java_home / "bin/java.exe"), "-Xmx512m", "-cp", str(dest), "FactoryPerf"], text=True)
    results[label] = json.loads(raw)
    if results[label]["stopped"] or results[label]["error"]:
        raise AssertionError(results[label])
results["allocationReduction"] = 1 - results["after"]["allocatedBytes"] / results["before"]["allocatedBytes"]
(output / "result.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
print(json.dumps(results, indent=2))
