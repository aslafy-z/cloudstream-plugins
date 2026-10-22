#!/usr/bin/env python3
"""Package every Gradle plugin as a GitHub release asset and rewrite its catalog URL."""
import argparse
import hashlib
import json
import shutil
from pathlib import Path


def package(root: Path, output: Path, repository: str, tag: str) -> None:
    catalog = json.loads((root / "build/plugins.json").read_text())
    output.mkdir(parents=True, exist_ok=True)
    artifacts = {p.name: p for p in root.glob("*/build/*.cs3")}
    if not catalog or not artifacts:
        raise ValueError("No plugins were built")
    for plugin in catalog:
        filename = plugin["internalName"] + ".cs3"
        source = artifacts.get(filename)
        if source is None:
            raise ValueError(f"Missing artifact: {filename}")
        shutil.copy2(source, output / filename)
        plugin["url"] = f"https://github.com/{repository}/releases/download/{tag}/{filename}"
        plugin["fileSize"] = source.stat().st_size
    (output / "plugins.json").write_text(json.dumps(catalog, indent=2, ensure_ascii=False) + "\n")
    shutil.copy2(root / "repo.json", output / "repo.json")
    sums = []
    for path in sorted(output.iterdir()):
        if path.is_file() and path.name != "SHA256SUMS":
            sums.append(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}")
    (output / "SHA256SUMS").write_text("\n".join(sums) + "\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path("."))
    parser.add_argument("--output", type=Path, default=Path("dist"))
    parser.add_argument("--repository", required=True)
    parser.add_argument("--tag", required=True)
    args = parser.parse_args()
    package(args.root, args.output, args.repository, args.tag)
