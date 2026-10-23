import argparse
import io
import json
import tempfile
import unittest
import zipfile
from pathlib import Path
from unittest.mock import patch
import releases as r


def entry(name="M6Plus", version=37):
    return {"internalName": name, "version": version, "fileHash": "sha256-test", "fileSize": 42,
        "url": r.asset_url(f"{name}-v2026.10.22.{version}", name + ".cs3")}


class ReleaseTests(unittest.TestCase):
    def test_merge_preserves_other_plugins_and_ignores_older_dev_run(self):
        old = [entry(), entry("TF1Plus", 38)]
        result = r.merge_entries(old, [entry(version=36), entry("ARTE", 39)])
        self.assertEqual({e["internalName"]: e["version"] for e in result}, {"ARTE": 39, "M6Plus": 37, "TF1Plus": 38})

    def test_stable_refuses_downgrade(self):
        with self.assertRaises(ValueError):
            r.merge_entries([entry(version=38)], [entry()], strict=True)

    def test_same_version_cannot_replace_binary(self):
        changed = dict(entry(), fileHash="sha256-different")
        with self.assertRaises(ValueError):
            r.merge_entries([entry()], [changed])

    def test_duplicate_or_foreign_entries_are_rejected(self):
        for entries in ([entry(), entry()], [dict(entry(), url="https://example.invalid/a.cs3")],
                        [dict(entry(), version=-1)], [dict(entry(), internalName="../secret")]):
            with self.assertRaises(ValueError):
                r.validate_entries(entries)

    def test_all_latest_verifies_every_plugin_before_any_write(self):
        dev = [entry(), entry("TF1Plus")]
        with patch.object(r, "read_channel", side_effect=[dev, []]), \
             patch.object(r, "verified_release", side_effect=[{"source": "abc", "plugin": dev[0]}, ValueError("bad artifact")]), \
             patch.object(r, "write_channel") as write, patch.object(r, "command") as command:
            with self.assertRaises(ValueError):
                r.promote(argparse.Namespace(scope="all-latest", plugin="", release=""))
            write.assert_not_called()
            command.assert_not_called()

    def test_all_latest_publishes_one_complete_snapshot(self):
        dev = [entry(), entry("TF1Plus")]
        with patch.object(r, "read_channel", side_effect=[dev, []]) as read, \
             patch.object(r, "verified_release", side_effect=lambda tag, item: {"source": "abc", "plugin": item}), \
             patch.object(r, "write_channel") as write, patch.object(r, "command"):
            r.promote(argparse.Namespace(scope="all-latest", plugin="", release=""))
            self.assertEqual(read.call_count, 2)
            write.assert_called_once_with("stable", dev, "abc")

    def test_one_plugin_promotion_preserves_other_stable_version(self):
        dev = [entry(version=39), entry("TF1Plus", 39)]
        stable = [entry(), entry("TF1Plus")]
        with patch.object(r, "read_channel", side_effect=[dev, stable]), \
             patch.object(r, "verified_release", return_value={"source": "abc"}), \
             patch.object(r, "write_channel") as write, patch.object(r, "command"):
            r.promote(argparse.Namespace(scope="one-plugin", plugin="M6Plus", release=""))
            self.assertEqual(write.call_args.args[1], [dev[0], stable[1]])

    def test_bundle_validates_embedded_manifest_and_checksum(self):
        with tempfile.TemporaryDirectory() as directory:
            directory = Path(directory)
            with zipfile.ZipFile(directory / "M6Plus.cs3", "w") as archive:
                archive.writestr("manifest.json", json.dumps({"name": "M6Plus", "version": 37}))
            binary = (directory / "M6Plus.cs3").read_bytes()
            item = dict(entry(), fileSize=len(binary), fileHash="sha256-" + r.sha256(binary))
            r.write_json(directory / "build.json", {"plugin": item})
            self.assertEqual(r.verify_bundle(directory)["plugin"], item)
            item["version"] = 38
            r.write_json(directory / "build.json", {"plugin": item})
            with self.assertRaisesRegex(ValueError, "Manifest"):
                r.verify_bundle(directory)
            item["version"] = 37
            r.write_json(directory / "build.json", {"plugin": item})
            (directory / "M6Plus.cs3").write_bytes(b"corrupted")
            with self.assertRaisesRegex(ValueError, "checksum"):
                r.verify_bundle(directory)

    def test_channel_reads_fixed_plugin_list(self):
        repo = {"pluginLists": [r.asset_url("catalog-dev", "plugins.json")]}
        with patch.object(r, "release_info", return_value={}), \
             patch.object(r, "asset", side_effect=[r.encoded(repo), r.encoded([entry()])]) as asset:
            self.assertEqual(r.read_channel("dev"), [entry()])
            self.assertEqual(asset.call_args_list[-1].args, ("catalog-dev", "plugins.json"))

    def test_channel_rejects_foreign_or_other_channel_lists(self):
        for url in ("https://example.invalid/plugins.json", r.asset_url("catalog-stable", "plugins.json"),
                    r.asset_url("catalog-dev", "plugins-old.json")):
            with patch.object(r, "release_info", return_value={}), \
                 patch.object(r, "asset", return_value=r.encoded({"pluginLists": [url]})):
                with self.assertRaisesRegex(ValueError, "this channel"):
                    r.read_channel("dev")

    def test_publication_only_uploads_fixed_files_in_order(self):
        uploaded = []
        def upload(*args):
            path = Path(args[4])
            uploaded.append((path.name, json.loads(path.read_text())))
        with patch.object(r, "release_info", return_value={"assets": []}), \
             patch.object(r, "command", side_effect=upload), \
             patch.object(r, "read_channel", return_value=[entry()]):
            r.write_channel("dev", [entry()], "abc")
        self.assertEqual([name for name, _ in uploaded], ["plugins.json", "repo.json"])
        self.assertEqual(uploaded[0][1], [entry()])
        self.assertEqual(uploaded[1][1]["pluginLists"], [r.asset_url("catalog-dev", "plugins.json")])

    def test_new_channel_contains_only_fixed_files(self):
        captured = []
        def create(*args):
            captured.extend(Path(arg).name for arg in args[4:6])
        with patch.object(r, "release_info", return_value=None), \
             patch.object(r, "command", side_effect=create), \
             patch.object(r, "read_channel", return_value=[entry()]):
            r.write_channel("stable", [entry()], "abc")
        self.assertEqual(set(captured), {"plugins.json", "repo.json"})

    def publish_with_dev(self, dev_entry, dev_fingerprint, version=40):
        with tempfile.TemporaryDirectory() as directory:
            r.write_json(Path(directory) / "dist/release-plan.json", {"source": "abc", "version": version,
                "builds": [{"name": "M6Plus", "tag": f"M6Plus-v2026.10.23.{version}", "fingerprint": "new"}]})
            (Path(directory) / "dist/M6Plus").mkdir()
            published = {"source": "abc", "fingerprint": "new", "plugin": entry(version=version)}
            with patch.object(r, "ROOT", Path(directory)), \
                 patch.object(r, "read_channel", return_value=[dev_entry]), \
                 patch.object(r, "asset", return_value=r.encoded({"fingerprint": dev_fingerprint})), \
                 patch.object(r, "verify_bundle", return_value=published), \
                 patch.object(r, "release_info", return_value=None), \
                 patch.object(r, "verified_release", return_value=published), \
                 patch.object(r, "command") as command, patch.object(r, "write_channel") as write:
                r.publish(argparse.Namespace())
            return command, write

    def test_publish_skips_build_another_run_already_released(self):
        command, write = self.publish_with_dev(entry(version=38), "new")
        command.assert_not_called()
        write.assert_not_called()

    def test_publish_releases_changed_build(self):
        command, write = self.publish_with_dev(entry(version=38), "old")
        self.assertEqual(command.call_args.args[:4], ("gh", "release", "create", "M6Plus-v2026.10.23.40"))
        self.assertEqual(write.call_args.args[1], [entry(version=40)])

    def test_publish_rerun_reuses_its_own_release(self):
        command, write = self.publish_with_dev(entry(version=40), "new")
        command.assert_called_once()
        write.assert_not_called()

    def test_publish_never_moves_dev_backwards(self):
        command, write = self.publish_with_dev(entry(version=41), "old")
        command.assert_not_called()
        write.assert_not_called()

    def test_fingerprints_ignore_other_plugins_and_docs_but_include_shared_build(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            files = {"plugins/M6Plus/code.kt": "a", "plugins/TF1Plus/code.kt": "b",
                     "plugins/M6Plus/README.md": "docs", "build.gradle.kts": "shared"}
            for path, text in files.items():
                (root / path).parent.mkdir(parents=True, exist_ok=True)
                (root / path).write_text(text)
            with patch.object(r, "command", return_value="\0".join(files)):
                original = r.fingerprint(root, "M6Plus")
                (root / "plugins/TF1Plus/code.kt").write_text("changed")
                (root / "plugins/M6Plus/README.md").write_text("new docs")
                self.assertEqual(original, r.fingerprint(root, "M6Plus"))
                (root / "build.gradle.kts").write_text("new dependency")
                self.assertNotEqual(original, r.fingerprint(root, "M6Plus"))


if __name__ == "__main__":
    unittest.main()
