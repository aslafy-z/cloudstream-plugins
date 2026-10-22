#!/usr/bin/env python3
"""Independent plugin releases and fixed catalogues; no generated files in Git."""
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
NAME = re.compile(r"[A-Za-z][A-Za-z0-9_-]*")
TAG = re.compile(r"([A-Za-z][A-Za-z0-9_-]*)-v(\d{4}\.\d{2}\.\d{2})\.(\d+)")
CHANNELS = {"dev": "catalog-dev", "stable": "catalog-stable"}


def encoded(data):
    return (json.dumps(data, indent=2, sort_keys=True, ensure_ascii=False) + "\n").encode()


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def write_json(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(encoded(data))


def command(*args, cwd=None):
    result = subprocess.run(args, cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise RuntimeError(f"{args[0]} failed: {result.stderr.strip()}")
    return result.stdout


def repository():
    value = os.environ.get("GH_REPO", os.environ.get("GITHUB_REPOSITORY", "aslafy-z/cloudstream-plugins"))
    if not re.fullmatch(r"[\w.-]+/[\w.-]+", value):
        raise ValueError("Invalid GitHub repository")
    return value


def release_info(tag):
    result = subprocess.run(["gh", "release", "view", tag, "--repo", repository(),
        "--json", "tagName,isPrerelease,isDraft,targetCommitish,assets"], text=True, capture_output=True)
    if result.returncode:
        if "release not found" in result.stderr.lower() or "HTTP 404" in result.stderr:
            return None
        raise RuntimeError(result.stderr.strip())
    return json.loads(result.stdout)


def download(tag, directory, pattern="*"):
    command("gh", "release", "download", tag, "--repo", repository(), "--dir", str(directory), "--pattern", pattern)


def asset(tag, filename):
    with tempfile.TemporaryDirectory() as directory:
        download(tag, directory, filename)
        return (Path(directory) / filename).read_bytes()


def asset_url(tag, filename):
    return f"https://github.com/{repository()}/releases/download/{tag}/{filename}"


def entry_tag(entry):
    prefix = f"https://github.com/{repository()}/releases/download/"
    url = entry["url"]
    if not url.startswith(prefix):
        raise ValueError("Catalogue asset belongs to a different repository")
    tag, filename = url[len(prefix):].split("/")
    if not TAG.fullmatch(tag) or filename != entry["internalName"] + ".cs3":
        raise ValueError("Invalid versioned plugin asset URL")
    return tag


def validate_entries(entries):
    names = set()
    if not isinstance(entries, list):
        raise ValueError("Catalogue must be an array")
    for entry in entries:
        name = entry["internalName"]
        if not NAME.fullmatch(name) or name in names:
            raise ValueError("Invalid or duplicate plugin name")
        names.add(name)
        if type(entry["version"]) is not int or not 0 < entry["version"] < 2**31:
            raise ValueError("Plugin version must be a positive 32-bit integer")
        entry_tag(entry)
    return entries


def read_channel(channel):
    tag = CHANNELS[channel]
    if release_info(tag) is None:
        return []
    repo = json.loads(asset(tag, "repo.json"))
    links = repo["pluginLists"]
    if links != [asset_url(tag, "plugins.json")]:
        raise ValueError("Catalogue must reference this channel's plugins.json")
    return validate_entries(json.loads(asset(tag, "plugins.json")))


def write_channel(channel, entries, sha):
    """Publish one plugin list per channel, then its repository descriptor."""
    entries = sorted(validate_entries(entries), key=lambda item: item["internalName"])
    tag = CHANNELS[channel]
    data = encoded(entries)
    repo = {"name": f"aslafy-z CloudStream plugins - {channel.title()}",
        "description": "Development builds." if channel == "dev" else "Validated plugin builds.",
        "manifestVersion": 1, "pluginLists": [asset_url(tag, "plugins.json")]}
    with tempfile.TemporaryDirectory() as directory:
        directory = Path(directory)
        (directory / "plugins.json").write_bytes(data)
        write_json(directory / "repo.json", repo)
        info = release_info(tag)
        if info is None:
            args = ["gh", "release", "create", tag, *map(str, directory.iterdir()), "--repo", repository(),
                "--target", sha, "--title", tag, "--notes", f"Permanent {channel.title()} catalogue. Add the repo.json asset to CloudStream."]
            args += ["--prerelease", "--latest=false"] if channel == "dev" else ["--latest"]
            command(*args)
        else:
            command("gh", "release", "upload", tag, str(directory / "plugins.json"), "--repo", repository(), "--clobber")
            command("gh", "release", "upload", tag, str(directory / "repo.json"), "--repo", repository(), "--clobber")
        if read_channel(channel) != entries:
            raise RuntimeError("Published catalogue verification failed")


def merge_entries(current, incoming, strict=False):
    result = {item["internalName"]: item for item in validate_entries(current)}
    for item in validate_entries(incoming):
        old = result.get(item["internalName"])
        if old:
            if item["version"] < old["version"]:
                if strict:
                    raise ValueError("Automatic downgrade refused; publish the rollback as a new build")
                continue
            if item["version"] == old["version"] and item["fileHash"] != old["fileHash"]:
                raise ValueError("One plugin version cannot identify different binaries")
        result[item["internalName"]] = item
    return sorted(result.values(), key=lambda item: item["internalName"])


def verify_bundle(directory, expected=None):
    metadata = json.loads((directory / "build.json").read_text())
    entry = metadata["plugin"]
    validate_entries([entry])
    tag = entry_tag(entry)
    if TAG.fullmatch(tag).group(1) != entry["internalName"]:
        raise ValueError("Plugin and release names disagree")
    binary = (directory / (entry["internalName"] + ".cs3")).read_bytes()
    if entry["fileHash"] != "sha256-" + sha256(binary) or entry["fileSize"] != len(binary):
        raise ValueError("Plugin checksum or size mismatch")
    with zipfile.ZipFile(directory / (entry["internalName"] + ".cs3")) as archive:
        manifest = json.loads(archive.read("manifest.json"))
    if manifest["version"] != entry["version"] or manifest["name"] != entry["internalName"]:
        raise ValueError("Manifest and catalogue disagree")
    if expected is not None and entry != expected:
        raise ValueError("Release does not match catalogue entry")
    return metadata


def verified_release(tag, expected=None):
    if not TAG.fullmatch(tag):
        raise ValueError("Expected a plugin CalVer release tag")
    info = release_info(tag)
    if info is None or info["isDraft"]:
        raise ValueError("Release is not published")
    with tempfile.TemporaryDirectory() as directory:
        download(tag, directory)
        metadata = verify_bundle(Path(directory), expected)
    if entry_tag(metadata["plugin"]) != tag:
        raise ValueError("Release tag and asset metadata disagree")
    return metadata


def fingerprint(root, name):
    tracked = command("git", "ls-files", "-z", cwd=root).split("\0")
    paths = []
    for path in tracked:
        if not path or Path(path).suffix == ".md":
            continue
        if path.startswith(f"plugins/{name}/") or (not path.startswith("plugins/") and
            (path in {"build.gradle.kts", "settings.gradle.kts", "gradle.properties", "gradlew"} or
             path.startswith(("gradle/", "scripts/", ".github/workflows/")))):
            paths.append(path)
    return sha256(b"".join(path.encode() + b"\0" + (root / path).read_bytes() + b"\0" for path in sorted(paths)))


def plan(args):
    run = json.loads(command("gh", "api", f"repos/{repository()}/actions/runs/{args.run}"))
    if run["run_number"] != args.version or run["head_sha"] != args.sha:
        raise ValueError("Workflow build identity mismatch")
    date = run["created_at"][:10].replace("-", ".")
    current = {item["internalName"]: item for item in read_channel("dev")}
    modules = sorted(path.parent.name for path in (ROOT / "plugins").glob("*/build.gradle.kts"))
    builds = []
    for name in modules:
        digest = fingerprint(ROOT, name)
        previous = current.get(name)
        old = json.loads(asset(entry_tag(previous), "build.json")) if previous else None
        if old and old.get("fingerprint") == digest and os.environ.get("REBUILD_PLUGINS") != "true":
            continue
        if previous and previous["version"] > args.version:
            continue  # An older run must not move this plugin backwards.
        builds.append({"name": name, "tag": f"{name}-v{date}.{args.version}", "fingerprint": digest})
    write_json(ROOT / "build/release-plan.json", {"source": args.sha, "version": args.version, "builds": builds})
    print("Affected plugins:", ", ".join(item["name"] for item in builds) or "none")


def package(args):
    plan = json.loads((ROOT / "build/release-plan.json").read_text())
    catalog = {item["internalName"]: item for item in json.loads((ROOT / "build/plugins.json").read_text())}
    output = ROOT / "dist"
    if output.exists():
        shutil.rmtree(output)
    output.mkdir()
    write_json(output / "release-plan.json", plan)
    for build in plan["builds"]:
        name, tag = build["name"], build["tag"]
        target = output / name
        target.mkdir()
        source = ROOT / "plugins" / name / "build" / (name + ".cs3")
        binary = source.read_bytes()
        entry = dict(catalog[name], url=asset_url(tag, name + ".cs3"), fileSize=len(binary), fileHash="sha256-" + sha256(binary))
        if entry["version"] != plan["version"]:
            raise ValueError("CI version was not embedded in the catalogue")
        shutil.copy2(source, target / source.name)
        write_json(target / "build.json", {"source": plan["source"], "fingerprint": build["fingerprint"], "plugin": entry})
        write_json(target / "plugins.json", [entry])
        verify_bundle(target)
        (target / "SHA256SUMS").write_text("".join(f"{sha256(path.read_bytes())}  {path.name}\n" for path in sorted(target.iterdir())))


def publish(args):
    directory = ROOT / "dist"
    plan = json.loads((directory / "release-plan.json").read_text())
    incoming = []
    for build in plan["builds"]:
        target, tag = directory / build["name"], build["tag"]
        metadata = verify_bundle(target)
        existing = release_info(tag)
        if existing is None:
            command("gh", "release", "create", tag, *map(str, sorted(target.iterdir())), "--repo", repository(),
                "--target", plan["source"], "--title", tag, "--prerelease", "--latest=false", "--notes",
                f"{build['name']} development build from {plan['source']}. Promote through the Stable workflow after testing.")
        # A rerun reuses the already published binary; it never replaces versioned assets.
        published = verified_release(tag)
        if published["source"] != metadata["source"] or published["fingerprint"] != metadata["fingerprint"]:
            raise ValueError("Existing release belongs to a different source/build")
        incoming.append(published["plugin"])
    if incoming:
        current = read_channel("dev")
        merged = merge_entries(current, incoming)
        if current != merged:
            write_channel("dev", merged, plan["source"])


def promote(args):
    # Read Dev once: all-latest is a snapshot, even if Dev advances during verification.
    dev, stable = read_channel("dev"), read_channel("stable")
    if args.scope == "all-latest":
        if args.plugin or args.release:
            raise ValueError("all-latest cannot be combined with a plugin or release")
        selected = dev
    else:
        if not NAME.fullmatch(args.plugin):
            raise ValueError("Specify the plugin module name")
        selected = [verified_release(args.release)["plugin"]] if args.release else [item for item in dev if item["internalName"] == args.plugin]
        if len(selected) != 1 or selected[0]["internalName"] != args.plugin:
            raise ValueError("Requested plugin/release is not available")
    if not selected:
        raise ValueError("No Dev plugins are available to promote")
    # Verify ALL selected artifacts before changing Stable at all.
    builds = [verified_release(entry_tag(item), item) for item in selected]
    merged = merge_entries(stable, selected, strict=True)
    write_channel("stable", merged, builds[0]["source"])
    for item in selected:
        command("gh", "release", "edit", entry_tag(item), "--repo", repository(), "--prerelease=false", "--latest=false")
    command("gh", "release", "edit", CHANNELS["stable"], "--repo", repository(), "--latest")
    print("Promoted:", ", ".join(item["internalName"] for item in selected))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("plan")
    p.add_argument("--run", required=True, type=int)
    p.add_argument("--version", required=True, type=int)
    p.add_argument("--sha", required=True)
    sub.add_parser("package")
    sub.add_parser("publish")
    p = sub.add_parser("promote")
    p.add_argument("--scope", choices=["all-latest", "one-plugin"], required=True)
    p.add_argument("--plugin", default="")
    p.add_argument("--release", default="")
    args = parser.parse_args()
    globals()[args.action](args)


if __name__ == "__main__":
    main()
