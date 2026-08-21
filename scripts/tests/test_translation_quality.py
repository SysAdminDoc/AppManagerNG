#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "translation_quality.py"
SPEC = importlib.util.spec_from_file_location("translation_quality", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
quality = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(quality)


def write_resources(path: Path, body: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(f"<resources>{body}</resources>\n", encoding="utf-8")


class TranslationQualityTest(unittest.TestCase):
    def make_repo(self) -> Path:
        raw = tempfile.TemporaryDirectory()
        self.addCleanup(raw.cleanup)
        repo = Path(raw.name)
        write_resources(
            repo / "app/src/main/res/values/strings.xml",
            '<string name="one">One</string>'
            '<string name="two">Two</string>'
            '<string name="not_translated" translatable="false">Internal</string>',
        )
        (repo / "app").mkdir(exist_ok=True)
        (repo / "app/build.gradle").write_text(
            "android { defaultConfig { pseudoLocalesEnabled = true } }\n", encoding="utf-8"
        )
        return repo

    def test_qualifier_directories_are_not_counted_as_locales(self) -> None:
        repo = self.make_repo()
        write_resources(repo / "app/src/main/res/values-fr/strings.xml", '<string name="one">Un</string>')
        write_resources(repo / "app/src/main/res/values-night/strings.xml", '<string name="two">Two</string>')

        data = quality.snapshot(repo)

        self.assertEqual(2, data["sourceCount"])
        self.assertEqual({"fr"}, set(data["locales"]))
        self.assertEqual(1, data["locales"]["fr"]["translated"])

    def test_baseline_detects_a_new_coverage_gap(self) -> None:
        repo = self.make_repo()
        write_resources(repo / "app/src/main/res/values-fr/strings.xml", '<string name="one">Un</string>')
        baseline = quality.build_baseline(quality.snapshot(repo))

        write_resources(repo / "app/src/main/res/values-fr/strings.xml", "")
        problems, summary = quality.evaluate(quality.snapshot(repo), baseline)

        self.assertTrue(any("newly missing" in problem for problem in problems))
        self.assertEqual(1, summary["newMissing"])

    def test_stale_locale_names_are_blocking(self) -> None:
        repo = self.make_repo()
        write_resources(
            repo / "app/src/main/res/values-fr/strings.xml",
            '<string name="one">Un</string><string name="removed">Vieux</string>',
        )
        baseline = quality.build_baseline(quality.snapshot(repo))
        problems, summary = quality.evaluate(quality.snapshot(repo), baseline)

        self.assertTrue(any("stale string" in problem for problem in problems))
        self.assertEqual(1, summary["staleStrings"])

    def test_region_locale_requires_base_language_strings(self) -> None:
        repo = self.make_repo()
        write_resources(repo / "app/src/main/res/values-fr-rCA/strings.xml", '<string name="one">Un</string>')
        data = quality.snapshot(repo)
        baseline = quality.build_baseline(data)

        problems, summary = quality.evaluate(data, baseline)

        self.assertTrue(any("no values-fr/strings.xml fallback" in problem for problem in problems))
        self.assertEqual(1, summary["regionLocalesWithoutBase"])


if __name__ == "__main__":
    unittest.main()
