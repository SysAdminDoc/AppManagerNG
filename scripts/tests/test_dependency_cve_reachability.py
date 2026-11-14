# SPDX-License-Identifier: GPL-3.0-or-later

import importlib.util
import io
import json
import re
import struct
import sys
import tempfile
import time
import tracemalloc
import unittest
import zipfile
from pathlib import Path
from types import FrameType
from typing import Any
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "dependency_cve_reachability.py"
SPEC = importlib.util.spec_from_file_location("dependency_cve_reachability", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
reach = importlib.util.module_from_spec(SPEC)
# Its dataclasses look their module up while it loads
sys.modules[SPEC.name] = reach
SPEC.loader.exec_module(reach)

NS = "https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd"
FLOSS_RELEASE = "app:flossReleaseRuntimeClasspath"
FULL_RELEASE = "app:fullReleaseRuntimeClasspath"
SQLITE = "pkg:maven/androidx.sqlite/sqlite-android@2.5.1"
GUAVA = "pkg:maven/com.google.guava/guava@32.1.3-android"
NETTY = "pkg:maven/io.netty/netty-codec@4.1.110.Final"


def vulnerability(name, score=None, severity="CRITICAL", cpes=()):
    record = {"name": name, "source": "NVD", "severity": severity, "description": "fixture"}
    if score is not None:
        record["cvssv3"] = {"baseScore": score}
    if cpes:
        # The scanner lists every CPE in the CVE's configuration and flags the ones it matched
        record["vulnerableSoftware"] = [{"software": {"id": cpe, "vulnerabilityIdMatched": "true"}} for cpe in cpes]
        record["vulnerableSoftware"].append({"software": {"id": "cpe:2.3:o:other:os:*:*:*:*:*:*:*:*"}})
    return record


def dependency(purl, file_path, references, vulnerabilities=(), suppressed=()):
    return {"fileName": Path(file_path).name, "filePath": str(file_path),
            "packages": [{"id": purl}], "projectReferences": list(references),
            "vulnerabilities": list(vulnerabilities), "suppressedVulnerabilities": list(suppressed)}


def dex_with_types(descriptors):
    """A DEX file whose type table holds exactly these descriptors, enough for read_dex_types."""
    strings = sorted(descriptors)
    count = len(strings)
    string_ids_off = 0x70
    type_ids_off = string_ids_off + 4 * count
    data_off = type_ids_off + 4 * count
    data = bytearray()
    offsets = []
    for text in strings:
        offsets.append(data_off + len(data))
        data += bytes([len(text)]) + text.encode("utf-8") + b"\0"
    header = bytearray(0x70)
    header[0:8] = b"dex\n035\0"
    struct.pack_into("<4I", header, 0x38, count, string_ids_off, count, type_ids_off)
    ids = b"".join(struct.pack("<I", offset) for offset in offsets)
    types = b"".join(struct.pack("<I", index) for index in range(count))
    return bytes(header) + ids + types + bytes(data)


def jar_bytes(class_names):
    buffer = io.BytesIO()
    with zipfile.ZipFile(buffer, "w") as archive:
        for name in class_names:
            archive.writestr(name.replace(".", "/") + ".class", b"\xca\xfe\xba\xbe")
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
    return buffer.getvalue()


class ReachabilityTestBase(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def write_report(self, dependencies) -> Path:
        path = self.root / "dependency-check-report.json"
        path.write_text(json.dumps({"dependencies": dependencies}), encoding="utf-8")
        return path

    def write_suppressions(self, *rules) -> Path:
        path = self.root / "suppressions.xml"
        path.write_text(f'<suppressions xmlns="{NS}">' + "".join(rules) + "</suppressions>", encoding="utf-8")
        return path

    def write_dispositions(self, entries) -> Path:
        path = self.root / "dispositions.json"
        path.write_text(json.dumps({"schemaVersion": 1, "dispositions": entries}), encoding="utf-8")
        return path

    def write_jar(self, name, class_names) -> Path:
        path = self.root / name
        path.write_bytes(jar_bytes(class_names))
        return path

    def write_apk(self, descriptors, renamed):
        apk = self.root / "AppManagerNG-reproducible-floss-release.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("classes.dex", dex_with_types(descriptors))
            archive.writestr("AndroidManifest.xml", b"\0")
        mapping = self.root / "AppManagerNG-reproducible-floss-release-mapping.txt"
        lines = ["# compiler: R8"]
        for original, obfuscated in renamed.items():
            lines += [f"{original} -> {obfuscated}:", "    void run() -> a"]
        mapping.write_text("\n".join(lines) + "\n", encoding="utf-8")
        return reach.ApkEvidence.load(apk, mapping)


def rule(cve, purl_regex, notes):
    return (f"<suppress><notes>{notes}</notes><packageUrl regex=\"true\">{purl_regex}</packageUrl>"
            f"<cve>{cve}</cve></suppress>")


class ReportTest(ReachabilityTestBase):
    def test_copies_of_one_finding_merge_and_one_unsuppressed_copy_keeps_it_open(self) -> None:
        suppressed_copy = dependency(SQLITE, "a/sqlite-android-2.5.1.aar", [FLOSS_RELEASE],
                                     suppressed=[vulnerability("CVE-2026-1", 7.5)])
        open_copy = dependency(SQLITE, "b/sqlite-android-2.5.1.aar", ["app:fullDebugRuntimeClasspath"],
                               vulnerabilities=[vulnerability("CVE-2026-1", 9.1)])
        for order in ([suppressed_copy, open_copy], [open_copy, suppressed_copy]):
            with self.subTest(first=order[0]["filePath"]):
                findings, purls = reach.load_report(self.write_report(order))

                self.assertEqual(1, len(findings))
                finding = findings[0]
                self.assertFalse(finding.suppressed)
                self.assertEqual(9.1, finding.score)
                self.assertEqual({"a/sqlite-android-2.5.1.aar", "b/sqlite-android-2.5.1.aar"}, finding.files)
                self.assertEqual({reach.Reference("app", "flossReleaseRuntimeClasspath"),
                                  reach.Reference("app", "fullDebugRuntimeClasspath")}, finding.references)
                self.assertEqual({SQLITE}, purls)

    def test_an_unscored_finding_takes_its_severity_estimate_and_an_unknown_one_the_worst(self) -> None:
        report = self.write_report([dependency(GUAVA, "guava.jar", [FLOSS_RELEASE], vulnerabilities=[
            vulnerability("GHSA-high", severity="HIGH"), vulnerability("GHSA-odd", severity="whatever")])])

        scores = {f.id: f.score for f in reach.load_report(report)[0]}

        self.assertEqual({"GHSA-high": 8.9, "GHSA-odd": 10.0}, scores)

    def test_an_unreadable_report_is_an_error(self) -> None:
        path = self.root / "broken.json"
        path.write_text("{", encoding="utf-8")
        with self.assertRaises(reach.ReachabilityError):
            reach.load_report(path)


class SuppressionTest(ReachabilityTestBase):
    def test_a_tagged_rule_pinned_to_a_package_and_cve_passes(self) -> None:
        rules = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$",
                 "[cpe-name-collision] AndroidX SQLite carries no SQLite of its own.")))

        self.assertEqual([], reach.check_suppressions(rules))
        self.assertEqual("cpe-name-collision", rules[0].tag)

    def test_untagged_unnamed_blanket_and_unpinned_rules_are_reported(self) -> None:
        rules = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", "^pkg:maven/a/b@1$", "No tag here."),
            "<suppress><notes>[host-only] x</notes><packageUrl regex=\"true\">^pkg:maven/a/b@1$</packageUrl></suppress>",
            "<suppress><notes>[host-only] x</notes><packageUrl regex=\"true\">^pkg:maven/a/b@1$</packageUrl>"
            "<cvssBelow>7</cvssBelow><cve>CVE-2026-2</cve></suppress>",
            "<suppress><notes>[host-only] x</notes><cve>CVE-2026-3</cve></suppress>",
            # Java reads \Q...\E, Python doesn't, and the checks after the scan use Python
            rule("CVE-2026-4", r"^pkg:maven/\Qio.netty\E/netty-codec@4\.1\.110\.Final$", "[host-only] x"),
            # re raises OverflowError, not re.error, on this one
            rule("CVE-2026-5", r"^pkg:maven/a/b@1(x{0,9999999999})?$", "[host-only] x"),
        ))

        problems = "\n".join(reach.check_suppressions(rules))

        self.assertIn("suppression 1 (CVE-2026-1) has no disposition", problems)
        self.assertIn("suppression 2 (no named vulnerability) names no CVE", problems)
        self.assertIn("suppression 3 (CVE-2026-2) also matches by cvssBelow", problems)
        self.assertIn("suppression 4 (CVE-2026-3) has no packageUrl", problems)
        self.assertIn("suppression 5 (CVE-2026-4) has a packageUrl pattern this check can't read: bad escape \\Q",
                      problems)
        self.assertIn("suppression 6 (CVE-2026-5) has a packageUrl pattern this check can't read: "
                      "the repetition number is too large", problems)

    def test_a_rule_matching_every_version_and_a_host_only_rule_on_shipped_code_are_reported(self) -> None:
        rules = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/[^@]+@.+$", "[host-only] UTP only."),
            rule("CVE-2026-2", r"^pkg:maven/io\.netty/netty-codec@4\.1\.110\.Final$", "[host-only] UTP only."),
        ))
        report = self.write_report([dependency(NETTY, "netty-codec.jar", [FLOSS_RELEASE], suppressed=[
            vulnerability("CVE-2026-1", 7.5), vulnerability("CVE-2026-2", 7.5)])])
        findings, purls = reach.load_report(report)

        problems = "\n".join(reach.check_suppressions_against_scan(rules, purls, findings))

        self.assertIn("suppression 1 (CVE-2026-1) also matches pkg:maven/io.netty/netty-codec@", problems)
        self.assertIn("covers versions of pkg:maven/io.netty/netty-codec nobody reviewed", problems)
        self.assertNotIn("suppression 2 (CVE-2026-2) also matches", problems)
        self.assertIn("suppression 2 (CVE-2026-2) is tagged host-only, but " + NETTY
                      + " now resolves into " + FLOSS_RELEASE, problems)

    def test_a_pattern_taking_a_neighboring_version_is_blanket_but_one_naming_each_scanned_version_is_not(self) -> None:
        netty_93 = "pkg:maven/io.netty/netty-codec@4.1.93.Final"
        report = self.write_report([dependency(purl, purl.rsplit("@", 1)[1] + ".jar", ["app:androidLintTool"],
                                               suppressed=[vulnerability("CVE-2026-1", 7.5)])
                                    for purl in (NETTY, netty_93)])
        findings, purls = reach.load_report(report)
        loose = (r"[0-9.]+Final", r"4\.1\.\d+\.Final", r"4\.1\.(93|110)\.Final.*", r".*4\.1\.(93|110)\.Final",
                 r"4\.1\.(9.|110)\.Final",
                 # Below the scanned versions, a gap in a list, and syntax the list reader can't follow
                 r"4\.1\.(\d|[1-8]\d|9[0-3]|110)\.Final", r"4\.1\.(52|93|110)\.Final",
                 # The lookahead turns away both versions one above, so only a probe below catches it
                 r"4\.1\.(?!94|111)\d+\.Final")
        for version in loose:
            with self.subTest(version=version):
                rules = reach.load_suppressions(self.write_suppressions(
                    rule("CVE-2026-1", rf"^pkg:maven/io\.netty/netty-codec@{version}$", "[host-only] UTP only.")))

                problems = reach.check_suppressions_against_scan(rules, purls, findings)

                self.assertEqual(1, len(problems), problems)
                self.assertIn("also matches", problems[0])
        # Any artifact, but the version list has a gap no probe next to 4.1.93 or 4.1.110 reaches
        gap = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/[^@]+@4\.1\.(52|93|110)\.Final$", "[host-only] UTP only.")))
        self.assertIn("also matches pkg:maven/io.netty/netty-codec@4.1.52.Final",
                      "\n".join(reach.check_suppressions_against_scan(gap, purls, findings)))
        # The same gap behind a . or a class standing in for the @ ([^/] reads as one character, [^/:] as
        # a class), a \b the list reader skips, and an inline (?i) in a case-sensitive rule
        gaps = [rule("CVE-2026-1", rf"^pkg:maven/io\.netty/netty-codec{at}4\.1\.(52|93|110){boundary}\.Final$",
                     "[host-only] UTP only.") for at, boundary in ((".", ""), ("[^/]", ""), ("[^/:]", ""), ("@", r"\b"))]
        gaps.append('<suppress><notes>[host-only] UTP only.</notes><packageUrl regex="true" caseSensitive="true">'
                    r"(?i)^pkg:maven/IO\.NETTY/netty-codec@4\.1\.(52|93|110)\.Final$"
                    "</packageUrl><cve>CVE-2026-1</cve></suppress>")
        # A dotted capital I, which re takes for an i when it ignores case, though its lower() is two characters
        gaps.append(rule("CVE-2026-1", "^pkg:maven/" + chr(0x130) + r"o\.netty/netty-codec@4\.1\.(52|93|110)\.Final$",
                         "[host-only] UTP only."))
        for xml in gaps:
            with self.subTest(rule=xml):
                problems = reach.check_suppressions_against_scan(
                    reach.load_suppressions(self.write_suppressions(xml)), purls, findings)
                self.assertEqual(1, len(problems), problems)
                self.assertIn("also matches pkg:maven/io.netty/netty-codec@4.1.52.Final", problems[0])
        # An open-ended version no probe happens to hit is still reported: a repeat, any character, a
        # negated class, a bounded repeat too big to list, a backreference, and a run standing in for the @
        for pattern in (r"@4\.1\.(93|110)\.Final(-[a-z]+)*", r"@4\.1\.(93|110)\.Fina.", r"@4\.1\.(93|110)\.Fina[^xy]",
                        r"@(4\.1\.(93|110)\.Final|3\.[0-9]{1,4})", r"@(4)\.1\.(93|110)\.Final(\1)?",
                        r"[^0-9]+4\.1\.(93|110)\.Final"):
            with self.subTest(pattern=pattern):
                open_ended = reach.load_suppressions(self.write_suppressions(
                    rule("CVE-2026-1", rf"^pkg:maven/io\.netty/netty-codec{pattern}$", "[host-only] UTP only.")))
                self.assertEqual(["suppression 1 (CVE-2026-1) takes versions of pkg:maven/io.netty/netty-codec "
                                  "its pattern doesn't list, so it covers versions nobody reviewed; pin it to the "
                                  "versions it was reviewed for"],
                                 reach.check_suppressions_against_scan(open_ended, purls, findings))
        pinned = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/netty-codec@4\.1\.(93|110)\.Final$", "[host-only] UTP only.")))
        self.assertEqual([], reach.check_suppressions_against_scan(pinned, purls, findings))
        # Spelled with a letter re takes for the i when it ignores case, it still names the scanned
        # versions, though lower() turns that letter into something else
        for letter in (chr(0x130), chr(0x131)):
            with self.subTest(letter=hex(ord(letter))):
                spelled = reach.load_suppressions(self.write_suppressions(rule(
                    "CVE-2026-1", r"^pkg:maven/io\.netty/netty-codec@4\.1\.(93|110)\.F" + letter + "nal$",
                    "[host-only] UTP only.")))
                self.assertEqual([], reach.check_suppressions_against_scan(spelled, purls, findings))
        # A longer version that starts out like a scanned one is still another version. No probe ends
        # in -x, so only the pattern's own spelling of it can catch this.
        longer = reach.load_suppressions(self.write_suppressions(rule(
            "CVE-2026-1", r"^pkg:maven/io\.netty/netty-codec@4\.1\.(93|110)\.(Final|F" + chr(0x130) + r"nal-x)$",
            "[host-only] UTP only.")))
        problems = reach.check_suppressions_against_scan(longer, purls, findings)
        self.assertEqual(1, len(problems), problems)
        self.assertIn("also matches pkg:maven/io.netty/netty-codec@4.1.", problems[0])
        self.assertIn(".F" + chr(0x130) + "nal-x,", problems[0])
        # Looked up the same way, including each ASCII letter re takes for one outside ASCII
        scanned = reach._KnownPurls(["pkg:maven/a/b@1.Final", "pkg:maven/a/b@2.F" + chr(0x130) + "nal",
                                     "pkg:maven/a/b@4.rc-s"])
        for purl, expected in (("pkg:maven/a/b@1.FINAL", True), ("pkg:maven/a/b@1.F" + chr(0x131) + "nal", True),
                               ("pkg:maven/a/b@2.final", True), ("PKG:MAVEN/A/B@1.FINAL", True),
                               ("p" + chr(0x212A) + "g:maven/a/b@1.Final", True), ("pkg:maven/a/b@4.RC-S", True),
                               ("pkg:maven/a/b@4.rc-" + chr(0x17F), True), ("pkg:maven/a/b@1.Final.1", False),
                               ("pkg:maven/a/b@1.F" + chr(0x130) + "nal.1", False), ("pkg:maven/a/b@3.Final", False),
                               ("pkg:maven/a/b@4.rc-t", False)):
            with self.subTest(purl=purl):
                self.assertEqual(expected, purl in scanned)
        # A probe outside ASCII is compared only with the purls that share its key. Compared with each
        # purl of its length instead, a rule with a few hundred such versions took seconds on a scan.
        many = reach._KnownPurls([f"pkg:maven/a/b@9.{index:03d}" for index in range(1000)] + ["pkg:maven/a/b@9.i42"])
        probes = [f"pkg:maven/a/b@9.{chr(0x130)}{index:02d}" for index in range(100)]
        with mock.patch.object(reach, "_same_letter", wraps=reach._same_letter) as compared:
            found = [probe for probe in probes if probe in many]
        self.assertEqual([probes[42]], found)
        self.assertLess(compared.call_count, len(probes) * len(probes[0]))
        any_artifact = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/[^@]+@4\.1\.(93|110)\.Final$", "[host-only] UTP only.")))
        self.assertEqual([], reach.check_suppressions_against_scan(any_artifact, purls, findings))
        any_separator = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/netty-codec.4\.1\.(93|110)\.Final$", "[host-only] UTP only.")))
        self.assertEqual([], reach.check_suppressions_against_scan(any_separator, purls, findings))

    def test_a_repeat_too_big_to_list_is_kept_open_instead_of_built(self) -> None:
        package = "pkg:maven/io.netty/netty-codec"
        # Listing every string here would take minutes and gigabytes
        for repeat in (r"(-x{0,16000})?", r"(?:){0,2000000000}", r"(x{0,64}){0,64}"):
            with self.subTest(repeat=repeat):
                pairs = reach.pattern_versions(rf"^pkg:maven/io\.netty/netty-codec@4\.1\.110\.Final{repeat}$")
                self.assertIn((package, None), pairs)
                self.assertLessEqual(len(pairs), 2)
        # Each class alone is small, but five in a row would make 100,000 strings
        pairs = reach.pattern_versions(r"^pkg:maven/io\.netty/netty-codec@4\.1\.110\.Final-[0-9][0-9][0-9][0-9][0-9]$")
        self.assertIn((package, None), pairs)
        self.assertLessEqual(len(pairs), reach._SIZE_LIMIT)
        # One string each, but 64 to the fourth characters long, and a branch whose other side is that size
        nested = "x"
        for _ in range(4):
            nested = f"(?:{nested}){{64}}"
        for pattern in (rf"^pkg:maven/a/b@1(?:{nested})?$",
                        r"^(?:pkg:maven/a/b@1|pkg:maven/a/b(?:(?:.{64}){64}){4})$"):
            with self.subTest(pattern=pattern[:40]):
                pairs = reach.pattern_versions(pattern)
                self.assertTrue(any(template.startswith("pkg:maven/a/b") and version is None
                                    for template, version in pairs), pairs)
                self.assertLessEqual(sum(len(t) + len(v or "") for t, v in pairs), 2 * reach._SIZE_LIMIT)
        # The repeat itself is turned down before it builds a string 64 cubed characters long
        low, high, item = reach._sre.parse(r"(?:(?:(?:x){64}){64}){64}")[0][1]
        self.assertEqual([reach._OPEN], reach._repeat_options(low, high, item, reach._Budget()))
        # Past the budget, what follows is read as one run instead of growing every string, and empty
        # strings count toward it too
        for pattern in (r"^pkg:maven/a/b@[0-9][0-9][0-9]" + "x" * 3000 + "$", "^" + "(?:|)" * 20 + "pkg:maven/a/b@1$"):
            with self.subTest(pattern=pattern[:30]):
                strings = reach._expand(list(reach._sre.parse(pattern)), reach._Budget())
                self.assertLessEqual(reach._size(strings), 2 * reach._SIZE_LIMIT)
        # A class that fits the budget is still listed in full
        listed = "".join(chr(0x100 + code) for code in range(6000))
        self.assertEqual(6000, len(reach._expand(list(reach._sre.parse(f"[{listed}]")), reach._Budget())))
        # Counted before it's built, since many narrow ranges add up to a wide class. Past half the
        # budget it would go over even on its own and be read as a run, so it's one character of any kind.
        half = reach._SIZE_LIMIT // 2

        def ranges(count: int, width: int) -> str:
            return "".join(chr(0x100 + index * (width + 1)) + "-" + chr(0x100 + index * (width + 1) + width - 1)
                           for index in range(count))

        letters = "".join(chr(0x100 + code) for code in range(half + 1))
        for body, size in ((letters[:half], half), (letters, None), (letters[:half - 10] + r"\d", half),
                           (letters[:half - 9] + r"\d", None), (ranges(half // 2, 2), half),
                           (ranges(half // 2 + 1, 2), None)):
            with self.subTest(body=body[-2:], length=len(body)):
                options = reach._class_options(reach._sre.parse(f"[{body}]")[0][1])
                if size is None:
                    self.assertEqual([reach._ONE], options)
                else:
                    self.assertEqual(size, len(options))
        # In the package part, where a run would take any name that follows
        self.assertEqual([("pkg:maven/a/b" + reach._ONE, "1")],
                         reach.pattern_versions(f"^pkg:maven/a/b[{ranges(200, 64)}]@1$"))
        # Where it stands, a class only fits as many characters as copies of the string so far fit the
        # limit: here 16384 // (16 + 1), with "pkg:maven/a/b@1" sized as its 15 characters plus one
        self.assertEqual(963, len(reach.pattern_versions(f"^pkg:maven/a/b@1[{letters[:963]}]$")))
        # A class that won't fit the package part is a run there, which takes any name that follows
        self.assertEqual([("pkg:maven/a/b" + reach._OPEN, "1")],
                         reach.pattern_versions(f"^pkg:maven/a/b[{letters[:1093]}]@1$"))
        # One that won't fit is read as a run without being listed, from one character over to 200
        # classes that would each list 8192 only to drop them as soon as they were sized
        listed = []

        def class_options(*args) -> list[str]:
            options = real_class_options(*args)
            listed.append(len(options))
            return options

        real_class_options = reach._class_options
        full = f"[{ranges(128, 64)}]"
        with mock.patch.object(reach, "_class_options", class_options):
            over = reach.pattern_versions(f"^pkg:maven/a/b@1[{letters[:964]}]$")
            pairs = reach.pattern_versions("^pkg:maven/a/b@1" + full * 200 + "$")
        self.assertEqual([("pkg:maven/a/b", None)], over)
        self.assertEqual([("pkg:maven/a/b", None)], pairs)
        self.assertEqual([1] * 201, listed)
        # Built first, this one took twenty times the memory re's own parse of it takes
        wide = "".join(chr(0x100 + start) + "-" + chr(0x100 + start + 63) for start in range(20000))
        tracemalloc.start()
        try:
            pairs = reach.pattern_versions(f"^pkg:maven/a/b@1[{wide}]$")
            peak = tracemalloc.get_traced_memory()[1]
        finally:
            tracemalloc.stop()
        self.assertEqual([("pkg:maven/a/b", None)], pairs)
        self.assertLess(peak, 12_000_000)
        # A branch stops collecting once it's over the budget, instead of holding every side first
        sides = "|".join(f"(?:(?:{chr(0x100 + side)}){{64}}){{64}}" for side in range(3000))
        tracemalloc.start()
        try:
            reach._expand(list(reach._sre.parse(f"(?:{sides})")), reach._Budget())
            peak = tracemalloc.get_traced_memory()[1]
        finally:
            tracemalloc.stop()
        self.assertLess(peak, 4_000_000)
        # More templates with unpinned characters than are worth trying on every package
        many = r"^pkg:maven/a/(?:" + "|".join(f"b{i}." for i in range(reach._PATTERNED_LIMIT + 1)) + ")@1$"
        self.assertEqual([(reach._OPEN, None)], reach.pattern_versions(many))
        # Too many characters standing in for the @ to split at each one, and a run, which covers every
        # split after it
        for tail in ("." * (reach._SPLIT_LIMIT + 1), ".*" + "." * 10):
            with self.subTest(tail=tail[:8]):
                self.assertEqual([("pkg:maven/a/b" + reach._OPEN, None)], reach.pattern_versions(r"^pkg:maven/a/b" + tail + "$"))
        # Nested deeper than the reader goes, though Python still compiles it
        deep = "^pkg:maven/a/b@1" + "(x" * 400 + ")?" * 400 + "$"
        re.compile(deep)
        self.assertEqual([(reach._OPEN, None)], reach.pattern_versions(deep))

    def test_one_read_stays_within_one_budget_however_its_parts_are_laid_out(self) -> None:
        # Each level holds two classes' worth of strings while the group inside it is read, so a limit
        # per part alone lets every level hold a full part
        for pair in ("[!-`a-i][!-`a-i]", r"\d\d\d"):
            for depth in (100, 450):
                with self.subTest(pair=pair, depth=depth):
                    tracemalloc.start()
                    try:
                        pairs = reach.pattern_versions("^pkg:maven/a/b@1" + (pair + "(") * depth + ")" * depth + "$")
                        peak = tracemalloc.get_traced_memory()[1]
                    finally:
                        tracemalloc.stop()
                    self.assertEqual([(reach._OPEN, None)], pairs)
                    self.assertLess(peak, 4_000_000)
        # Repeats built one after another and dropped in turn are charged too, so the read stops early
        # instead of building every one of them
        with mock.patch.object(reach, "_repeat_options", wraps=reach._repeat_options) as repeats:
            pairs = reach.pattern_versions("^pkg:maven/a/b@1" + "[!-`a-i]{2}" * 700 + "$")
        self.assertEqual([(reach._OPEN, None)], pairs)
        # Each repeat builds most of a part, so a handful of them use up the budget
        self.assertLess(repeats.call_count, 10)
        # The budget is charged for what each step adds, so a long rule listing many full names is
        # still read exactly instead of as taking anything. Each starts with its own letter, so re
        # can't pull a shared start out of the list and leave the sides short.
        names = [f"{chr(ord('a') + index % 26)}{index}.example.group/some-artifact-with-a-long-name-{index}"
                 f"@1.0.{index}" for index in range(60)]
        pairs = reach.pattern_versions("^pkg:maven/(?:" + "|".join(map(re.escape, names)) + ")$")
        self.assertEqual([tuple(("pkg:maven/" + name).split("@")) for name in names], pairs)
        long_name = "pkg:maven/io.netty/" + "netty-codec-" * 33
        self.assertEqual([(long_name, "4.1.110.Final")],
                         reach.pattern_versions("^" + re.escape(long_name) + r"@4\.1\.110\.Final$"))
        # A step that adds nothing doesn't rebuild every string, which over many of them takes seconds
        start = time.perf_counter()
        pairs = reach.pattern_versions("^" + "(?:|)" * 14 + r"\b" * 20000 + "pkg:maven/a/b@1$")
        self.assertLess(time.perf_counter() - start, 1.0)
        self.assertEqual([(reach._OPEN, None)], pairs)
        # Each side of a branch is sized once, not every side again after each new one
        with mock.patch.object(reach, "_size", wraps=reach._size) as sized:
            reach.pattern_versions("^pkg:maven/a/b@1(?:" + "|" * 16000 + ")$")
        self.assertLess(sum(len(call.args[0]) for call in sized.call_args_list), 4 * 16000)

    def test_a_package_matched_at_one_version_is_checked_at_the_others_the_scan_found_it_at(self) -> None:
        codec_93 = "pkg:maven/io.netty/netty-codec@4.1.93.Final"
        handler_110 = "pkg:maven/io.netty/netty-handler@4.1.110.Final"
        report = self.write_report([dependency(purl, purl.rsplit("/", 1)[1] + ".jar", ["app:androidLintTool"],
                                               suppressed=[vulnerability("CVE-2026-1", 7.5)])
                                    for purl in (codec_93, handler_110)])
        findings, purls = reach.load_report(report)
        # Only codec matches at its scanned version, but the pattern also takes handler at one nobody scanned
        for pattern in (r"netty-(codec|handler)@4\.1\.93\.Final", r"netty-(?:codec@4\.1\.93|handler@4\.1\.9[0-3])\.Final"):
            with self.subTest(pattern=pattern):
                rules = reach.load_suppressions(self.write_suppressions(
                    rule("CVE-2026-1", rf"^pkg:maven/io\.netty/{pattern}$", "[host-only] UTP only.")))
                problems = reach.check_suppressions_against_scan(rules, purls, findings)
                self.assertEqual(1, len(problems), problems)
                self.assertIn("also matches pkg:maven/io.netty/netty-handler@4.1.9", problems[0])
        pinned = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-1", r"^pkg:maven/io\.netty/netty-(codec@4\.1\.93|handler@4\.1\.110)\.Final$",
                 "[host-only] UTP only.")))
        self.assertEqual([], reach.check_suppressions_against_scan(pinned, purls, findings))

    def test_spellings_re_takes_for_each_other_share_one_lookup_key(self) -> None:
        inside = "".join(map(chr, range(0x80)))
        outside = "".join(map(chr, range(0x80, 0x110000)))
        kept = "".join(char for char in inside if reach._case_key(char) != b"?")
        # No ASCII character that keeps its own key pairs with one outside ASCII, either way round, so
        # a Python whose Unicode tables pair a new one fails here instead of missing purls
        self.assertEqual([], re.findall(f"[{re.escape(kept)}]", outside, re.IGNORECASE))
        self.assertEqual([], re.findall(f"[{chr(0x80)}-{chr(0x10FFFF)}]", kept, re.IGNORECASE))
        # The ones re takes for each other share a key, within ASCII and across it
        for char in inside:
            for other in re.findall(re.escape(char), inside + outside, re.IGNORECASE):
                self.assertEqual(reach._case_key(char), reach._case_key(other), (char, other))
        # One key character for each character of the purl, astral ones and lone surrogates too
        spelled = "pkg:maven/a/b@1." + chr(0x10FFFF) + chr(0xD800) + chr(0x130) + "x"
        self.assertEqual(len(spelled), len(reach._case_key(spelled)))

    def test_a_lookup_key_costs_no_python_step_for_each_character(self) -> None:
        # str.translate with a table that misses calls back into Python for every character outside
        # ASCII, which over long versions like these across a full scan took seconds. A loop or a
        # comprehension costs as much without making any call, and a generator resumes without running
        # a line, so this counts both. Each kind of character gets purls of its own and a share of
        # mixed ones, so a shortcut taken for one kind doesn't hide Python work for another.
        def tail(index: int) -> str:
            ascii_only = "kSi0." * 100
            across_the_bmp = "".join(chr(0x100 + (index * 500 + n) % 0xD700) for n in range(500))
            astral_and_surrogates = "".join(chr(0x10000 + index * 500 + n) if n % 2 else chr(0xD800 + n)
                                            for n in range(500))
            kinds = (ascii_only, across_the_bmp, astral_and_surrogates)
            if index % 4 < 3:
                return kinds[index % 4]
            return "".join(kinds[n % 3][n] for n in range(500))

        versions = [f"pkg:maven/a/b@9.{index:03d}" + tail(index) for index in range(200)]
        lines = []

        def trace(frame: FrameType, event: str, arg: Any) -> Any:
            if event in ("call", "line"):
                lines.append(event)
            return trace

        previous = sys.gettrace()
        sys.settrace(trace)
        try:
            known = reach._KnownPurls(versions[:100])
            found = [probe for probe in versions[100:] if probe in known]
        finally:
            sys.settrace(previous)
        self.assertEqual([], found)
        # A few calls and lines for each purl, where one for each character would be 500 each
        self.assertLess(len(lines), 10 * len(versions))

    def test_a_template_match_stays_linear_where_a_regex_would_backtrack(self) -> None:
        open_, one = reach._OPEN, reach._ONE
        # As a regex of .* parts this takes over an hour on a name it doesn't fit
        self.assertFalse(reach._template_matches("pkg:maven/a/" + (open_ + "-") * 40 + "x",
                                                 "pkg:maven/a/b" + "-b" * 39))
        for template, package, expected in (
                ("pkg:maven/a/" + open_ + "-" + open_ + "x", "pkg:maven/a/b-b-x", True),
                ("pkg:maven/a/b" + one + "c", "pkg:maven/a/bzc", True),
                ("pkg:maven/a/b" + one + "c", "pkg:maven/a/bc", False),
                ("PKG:MAVEN/A/" + open_, "pkg:maven/a/b", True),
                ("pkg:maven/a/b" + open_ + "b", "pkg:maven/a/b", False),
                ("a" + open_ + "xy" + open_ + "y", "axy", False),
                ("a" + open_ + "xy" + open_ + "y", "axyy", True),
                ("pkg:maven/a/b" + open_, "pkg:maven/a/b", True),
                ("pkg:maven/a/b", "pkg:maven/a/bb", False)):
            with self.subTest(template=template, package=package):
                self.assertEqual(expected, reach._template_matches(template, package))
        # Letters re takes for each other when it ignores case, where lower() disagrees for all but the
        # Kelvin sign: dotted and dotless i, the theta forms, the Kelvin sign and the long s
        for one, other, expected in (
                (0x130, "i", True), (0x130, "I", True), ("i", 0x130, True), (0x3D1, 0x3F4, True),
                (0x3F4, 0x3D1, True), (0x3B8, 0x3D1, True), (0x212A, "k", True), ("k", 0x212A, True),
                (0x17F, "s", True), ("s", 0x17F, True), (0x131, "i", True), ("i", 0x131, True), ("a", "b", False)):
            one, other = (chr(char) if isinstance(char, int) else char for char in (one, other))
            with self.subTest(one=hex(ord(one)), other=hex(ord(other))):
                self.assertEqual(expected, reach._template_matches("pkg:maven/" + one + "o", "pkg:maven/" + other + "o"))

    def test_a_rule_naming_a_cve_the_scan_no_longer_reports_is_stale(self) -> None:
        rules = reach.load_suppressions(self.write_suppressions(
            '<suppress><notes>[host-only] UTP only.</notes><packageUrl regex="true">'
            r"^pkg:maven/io\.netty/netty-codec@4\.1\.110\.Final$"
            "</packageUrl><cve>CVE-2026-1</cve><cve>CVE-2026-9</cve></suppress>"))
        findings, purls = reach.load_report(self.write_report([dependency(
            NETTY, "netty-codec.jar", ["app:androidLintTool"], suppressed=[vulnerability("CVE-2026-1", 7.5)])]))

        problems = reach.check_suppressions_against_scan(rules, purls, findings)

        self.assertEqual(["suppression 1 (CVE-2026-1, CVE-2026-9) no longer matches CVE-2026-9 in this scan; "
                          "update or remove it"], problems)

    def test_a_suppression_file_in_another_schema_is_an_error_not_an_empty_policy(self) -> None:
        path = self.root / "suppressions.xml"
        path.write_text('<suppressions xmlns="https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.3.xsd">'
                        "<suppress><notes>[host-only] x</notes><cve>CVE-2026-1</cve></suppress></suppressions>",
                        encoding="utf-8")

        with self.assertRaisesRegex(reach.ReachabilityError, "expected suppressions in"):
            reach.load_suppressions(path)


class DispositionTest(ReachabilityTestBase):
    def entry(self, **fields):
        base = {"ids": ["CVE-2026-51296"], "disposition": "fabricated", "reason": "SQLite lists it as bogus.",
                "source": "https://sqlite.org/cves.html", "reviewed": "2026-11-14"}
        base.update(fields)
        return base

    def test_a_fabricated_cve_needs_no_package_but_everything_else_does(self) -> None:
        dispositions = reach.load_dispositions(self.write_dispositions([
            self.entry(),
            self.entry(ids=["CVE-2026-2"], disposition="cpe-name-collision"),
            self.entry(ids=["CVE-2026-3"], disposition="held", packageUrl="^pkg:maven/a/b@1$"),
            self.entry(ids=["CVE-2026-4"], source="http://example.com", reviewed="soon"),
            self.entry(ids=["CVE-2026-5"], disposition="ignored", packageUrl="(unclosed"),
            self.entry(ids=["CVE-2026-6"], disposition="not-affected", packageUrl="^pkg:maven/a/b@1x{0,9999999999}$"),
        ]))

        problems = "\n".join(reach.check_dispositions(dispositions))

        self.assertNotIn("disposition 1 ", problems)
        self.assertIn("disposition 2 (CVE-2026-2) must name the packageUrl", problems)
        self.assertIn("disposition 3 (CVE-2026-3) is held, so it must say how the shipped code could be reached",
                      problems)
        self.assertIn("disposition 4 (CVE-2026-4) needs a reason and an https source", problems)
        self.assertIn("disposition 4 (CVE-2026-4) needs a reviewed date", problems)
        self.assertIn("disposition 5 (CVE-2026-5) has disposition 'ignored'", problems)
        self.assertIn("disposition 5 (CVE-2026-5) has an invalid packageUrl pattern", problems)
        self.assertIn("disposition 6 (CVE-2026-6) has an invalid packageUrl pattern: the repetition number is "
                      "too large", problems)

    def test_a_disposition_the_scan_no_longer_matches_or_that_matches_every_version_is_reported(self) -> None:
        dispositions = reach.load_dispositions(self.write_dispositions([
            self.entry(ids=["CVE-2026-9"], disposition="cpe-name-collision", packageUrl=r"^pkg:maven/a/gone@1$"),
            self.entry(ids=["CVE-2026-1"], disposition="cpe-name-collision",
                       packageUrl=r"^pkg:maven/androidx\.sqlite/sqlite-android@.+$"),
            # A fabricated label covers every package, but each id it names must still be in the scan
            self.entry(ids=["CVE-2026-1", "CVE-2026-51296"]),
            self.entry(ids=["CVE-2026-1"], disposition="not-affected",
                       packageUrl=r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$"),
            # No probe next to 2.5.1 lands on 2.4.0, so only reading the list finds it
            self.entry(ids=["CVE-2026-1"], disposition="not-affected",
                       packageUrl=r"^pkg:maven/(androidx\.sqlite/sqlite-android@(2\.4\.0|2\.5\.1)|a/b@1)$"),
        ]))
        findings, purls = reach.load_report(self.write_report([
            dependency(SQLITE, "sqlite.aar", [FLOSS_RELEASE], vulnerabilities=[vulnerability("CVE-2026-1", 9.8)])]))

        problems = reach.check_dispositions_against_scan(dispositions, purls, findings)
        text = "\n".join(problems)

        self.assertIn("disposition 1 (CVE-2026-9) matches no CVE-2026-9 finding in this scan", text)
        self.assertIn("disposition 2 (CVE-2026-1) also matches pkg:maven/androidx.sqlite/sqlite-android@", text)
        self.assertIn("disposition 3 (CVE-2026-1, CVE-2026-51296) matches no CVE-2026-51296 finding", text)
        self.assertIn("disposition 5 (CVE-2026-1) also matches pkg:maven/androidx.sqlite/sqlite-android@2.4.0", text)
        self.assertEqual(4, len(problems), problems)

    def test_a_cpe_disposition_labels_a_finding_only_when_every_matched_cpe_is_that_product(self) -> None:
        dispositions = reach.load_dispositions(self.write_dispositions([
            self.entry(ids=None, cpe="cpe:2.3:a:sqlite:sqlite:", disposition="cpe-name-collision",
                       packageUrl=r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$")]))
        sqlite_c = "cpe:2.3:a:sqlite:sqlite:*:*:*:*:*:*:*:*"
        findings, purls = reach.load_report(self.write_report([
            dependency(SQLITE, "sqlite.aar", [FLOSS_RELEASE], vulnerabilities=[
                vulnerability("CVE-2026-1", 9.8, cpes=[sqlite_c]),
                # Also matched on a JDBC driver's CPE, so the label can't vouch for it
                vulnerability("CVE-2026-2", 9.8, cpes=[sqlite_c, "cpe:2.3:a:sqlite:sqlite_jdbc:*:*:*:*:*:*:*:*"]),
                vulnerability("CVE-2026-3", 9.8)]),
            dependency(GUAVA, "guava.jar", [FLOSS_RELEASE], vulnerabilities=[
                vulnerability("CVE-2026-4", 9.8, cpes=[sqlite_c])]),
        ]))
        by_id = {finding.id: finding for finding in findings}

        self.assertEqual({sqlite_c}, by_id["CVE-2026-1"].cpes)
        self.assertEqual([True, False, False, False],
                         [dispositions[0].applies(by_id[f"CVE-2026-{n}"]) for n in range(1, 5)])
        self.assertEqual([], reach.check_dispositions(dispositions))
        self.assertEqual([], reach.check_dispositions_against_scan(dispositions, purls, findings))

        gone = reach.load_dispositions(self.write_dispositions([
            self.entry(ids=None, cpe="cpe:2.3:a:github:github:", disposition="cpe-name-collision",
                       packageUrl=r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$")]))
        self.assertEqual(["disposition 1 (cpe:2.3:a:github:github:) matches no finding in this scan; "
                          "update or remove it"], reach.check_dispositions_against_scan(gone, purls, findings))

    def test_a_cpe_disposition_must_be_a_name_collision_on_one_product_named_one_way(self) -> None:
        dispositions = reach.load_dispositions(self.write_dispositions([
            self.entry(ids=None, cpe="cpe:2.3:a:sqlite:sqlite:", disposition="not-affected", packageUrl="^a$"),
            self.entry(ids=None, cpe="cpe:2.3:a:sqlite:", disposition="cpe-name-collision", packageUrl="^a$"),
            self.entry(ids=None, cpe="cpe:2.3:a:sqlite:*:", disposition="cpe-name-collision", packageUrl="^a$"),
            self.entry(cpe="cpe:2.3:a:sqlite:sqlite:", disposition="cpe-name-collision", packageUrl="^a$"),
            self.entry(ids=None, disposition="not-affected", packageUrl="^a$"),
        ]))

        problems = "\n".join(reach.check_dispositions(dispositions))

        self.assertIn("disposition 1 (cpe:2.3:a:sqlite:sqlite:) is keyed by cpe, which only a cpe-name-collision "
                      "can be", problems)
        self.assertIn("disposition 2 (cpe:2.3:a:sqlite:) needs a cpe that names one product", problems)
        self.assertIn("disposition 3 (cpe:2.3:a:sqlite:*:) needs a cpe that names one product", problems)
        self.assertIn("disposition 4 (cpe:2.3:a:sqlite:sqlite:) names its vulnerabilities both by id and by cpe",
                      problems)
        self.assertIn("disposition 5 (no ids) names no vulnerability", problems)
        self.assertNotIn("disposition 5 (no ids) has disposition", problems)

    def test_an_unsupported_disposition_file_is_an_error(self) -> None:
        path = self.root / "dispositions.json"
        path.write_text(json.dumps({"schemaVersion": 2, "dispositions": []}), encoding="utf-8")
        with self.assertRaises(reach.ReachabilityError):
            reach.load_dispositions(path)


class ClassifyTest(ReachabilityTestBase):
    def classify(self, dependencies, rules=(), dispositions=(), lockfile=None, apks=None):
        findings, _ = reach.load_report(self.write_report(dependencies))
        return reach.classify(findings, rules, dispositions, lockfile or {}, apks or {}, 9.0)

    @staticmethod
    def ids(classification, flavor, section):
        return [entry["id"] for entry in classification["flavors"][flavor][section]]

    def test_release_runtime_findings_ship_and_test_only_ones_stay_on_the_host(self) -> None:
        result = self.classify([
            dependency(GUAVA, "guava.jar", [FLOSS_RELEASE, FULL_RELEASE],
                       vulnerabilities=[vulnerability("CVE-2026-102554", 5.3)]),
            dependency(NETTY, "netty.jar", ["app:flossDebugUnitTestRuntimeClasspath", "app:androidLintTool"],
                       vulnerabilities=[vulnerability("CVE-2026-42579", 7.5)]),
        ])

        self.assertEqual(["CVE-2026-102554"], self.ids(result, "floss", "packagedRelease"))
        self.assertEqual(["CVE-2026-102554"], self.ids(result, "full", "packagedRelease"))
        self.assertEqual(["CVE-2026-42579"], self.ids(result, "floss", "hostBuildTest"))
        # The lint tool belongs to both flavors, the FLOSS unit tests only to FLOSS
        full_host = result["flavors"]["full"]["hostBuildTest"]
        self.assertEqual(["app:androidLintTool"], full_host[0]["reachability"]["configurations"])
        shipped = result["flavors"]["floss"]["packagedRelease"][0]
        self.assertEqual([{"module": "app", "configuration": "flossReleaseRuntimeClasspath"},
                          {"module": "app", "configuration": "fullReleaseRuntimeClasspath"}],
                         shipped["references"])
        self.assertEqual(["guava.jar"], shipped["files"])
        self.assertIn("the scan resolved it in app:flossReleaseRuntimeClasspath",
                      shipped["reachability"]["classpath"])
        self.assertEqual([], result["blocking"])

    def test_a_label_never_unblocks_and_only_a_suppression_clears_a_finding(self) -> None:
        rules = reach.load_suppressions(self.write_suppressions(
            rule("CVE-2026-11822", r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$",
                 "[cpe-name-collision] No SQLite of its own.")))
        dispositions = reach.load_dispositions(self.write_dispositions([
            {"ids": ["CVE-2026-51296"], "disposition": "fabricated", "reason": "Bogus.",
             "source": "https://sqlite.org/cves.html", "reviewed": "2026-11-14"}]))

        result = self.classify([
            dependency(SQLITE, "sqlite.aar", [FLOSS_RELEASE],
                       vulnerabilities=[vulnerability("CVE-2026-51296", 9.8)],
                       suppressed=[vulnerability("CVE-2026-11822", 9.8)]),
            dependency(NETTY, "netty.jar", ["app:androidLintTool"], vulnerabilities=[vulnerability("CVE-2026-9", 9.8)]),
        ], rules=rules, dispositions=dispositions)

        not_applicable = {entry["id"]: entry for entry in result["flavors"]["floss"]["notApplicable"]}
        self.assertEqual({"CVE-2026-51296", "CVE-2026-11822"}, set(not_applicable))
        self.assertEqual("fabricated", not_applicable["CVE-2026-51296"]["disposition"]["kind"])
        self.assertEqual("suppressions", not_applicable["CVE-2026-11822"]["disposition"]["recordedIn"])
        self.assertFalse(not_applicable["CVE-2026-11822"]["blocking"])
        # A fabricated label without a suppression still blocks, and so does a finding only the host runs
        self.assertEqual(["CVE-2026-51296 " + SQLITE, "CVE-2026-9 " + NETTY],
                         sorted(reach.summarize(result)["blocking"]))

    def test_the_lockfile_and_a_missing_attribution_both_count_as_shipped(self) -> None:
        lockfile = {"com.google.guava:guava:32.1.3-android": {"flossReleaseRuntimeClasspath"}}

        result = self.classify([
            dependency(GUAVA, "guava.jar", [], vulnerabilities=[vulnerability("CVE-2026-1", 5.0)]),
            dependency("pkg:maven/x/unknown@1", "unknown.jar", [], vulnerabilities=[vulnerability("CVE-2026-2", 5.0)]),
        ], lockfile=lockfile)

        floss = {entry["id"]: entry for entry in result["flavors"]["floss"]["packagedRelease"]}
        self.assertEqual({"CVE-2026-1", "CVE-2026-2"}, set(floss))
        self.assertIn("app/gradle.lockfile lists com.google.guava:guava:32.1.3-android under "
                      "flossReleaseRuntimeClasspath", floss["CVE-2026-1"]["reachability"]["classpath"])
        self.assertIn("the scan names no configuration for it, so it's treated as shipped",
                      floss["CVE-2026-2"]["reachability"]["classpath"])
        # The lockfile puts Guava in FLOSS only; the unattributed one goes in both
        self.assertEqual(["CVE-2026-2"], self.ids(result, "full", "packagedRelease"))

    def test_desugared_library_code_ships_in_both_flavors_whether_the_scan_or_the_lockfile_says_so(self) -> None:
        configuration = "pkg:maven/com.android.tools/desugar_jdk_libs_configuration@2.1.5"
        lockfile = {"com.android.tools:desugar_jdk_libs_configuration:2.1.5": {"coreLibraryDesugaring"}}

        result = self.classify([
            dependency("pkg:maven/com.android.tools/desugar_jdk_libs@2.1.5", "desugar_jdk_libs.jar",
                       ["app:coreLibraryDesugaring"], vulnerabilities=[vulnerability("CVE-2026-1", 5.0)]),
            dependency(configuration, "desugar_jdk_libs_configuration.jar", [],
                       vulnerabilities=[vulnerability("CVE-2026-2", 5.0)]),
        ], lockfile=lockfile)

        for flavor in ("floss", "full"):
            shipped = {entry["id"]: entry["reachability"]["classpath"]
                       for entry in result["flavors"][flavor]["packagedRelease"]}
            self.assertEqual(["the scan resolved it in app:coreLibraryDesugaring"], shipped["CVE-2026-1"])
            self.assertEqual(["app/gradle.lockfile lists com.android.tools:desugar_jdk_libs_configuration:2.1.5 "
                              "under coreLibraryDesugaring"], shipped["CVE-2026-2"])
            self.assertEqual([], result["flavors"][flavor]["hostBuildTest"])

    def test_a_not_affected_or_cpe_label_moves_a_shipped_finding_out_of_packaged_but_never_unblocks_it(self) -> None:
        dispositions = reach.load_dispositions(self.write_dispositions([
            {"cpe": "cpe:2.3:a:sqlite:sqlite:", "disposition": "cpe-name-collision", "reason": "Java API only.",
             "source": "https://developer.android.com/jetpack/androidx/releases/sqlite", "reviewed": "2026-11-14",
             "packageUrl": r"^pkg:maven/androidx\.sqlite/sqlite-android@2\.5\.1$"},
            {"ids": ["CVE-2026-2"], "disposition": "not-affected", "reason": "Only in the GUI module.",
             "source": "https://nvd.nist.gov/vuln/detail/CVE-2026-2", "reviewed": "2026-11-14",
             "packageUrl": r"^pkg:maven/com\.google\.guava/guava@32\.1\.3-android$"}]))

        result = self.classify([
            dependency(SQLITE, "sqlite.aar", [FLOSS_RELEASE], vulnerabilities=[
                vulnerability("CVE-2026-1", 9.8, cpes=["cpe:2.3:a:sqlite:sqlite:*:*:*:*:*:*:*:*"]),
                vulnerability("CVE-2026-3", 5.0, cpes=["cpe:2.3:a:xerial:sqlite-jdbc:*:*:*:*:*:*:*:*"])]),
            dependency(GUAVA, "guava.jar", [FLOSS_RELEASE], vulnerabilities=[vulnerability("CVE-2026-2", 9.8)]),
        ], dispositions=dispositions)

        not_applicable = {entry["id"]: entry["disposition"] for entry in result["flavors"]["floss"]["notApplicable"]}
        self.assertEqual({"CVE-2026-1", "CVE-2026-2"}, set(not_applicable))
        self.assertEqual(("cpe-name-collision", "cpe:2.3:a:sqlite:sqlite:"),
                         (not_applicable["CVE-2026-1"]["kind"], not_applicable["CVE-2026-1"]["cpe"]))
        self.assertEqual("not-affected", not_applicable["CVE-2026-2"]["kind"])
        self.assertEqual(["CVE-2026-3"], self.ids(result, "floss", "packagedRelease"))
        self.assertEqual(["CVE-2026-1 " + SQLITE, "CVE-2026-2 " + GUAVA], sorted(reach.summarize(result)["blocking"]))

    def test_the_apk_shows_which_classes_r8_kept_renamed_or_not(self) -> None:
        guava = self.write_jar("guava.jar", ["com.google.common.collect.CompactHashMap",
                                             "com.google.common.base.Strings", "com.google.common.io.Files"])
        sqlite = self.write_jar("sqlite.jar", ["androidx.sqlite.db.SupportSQLiteDatabase"])
        evidence = self.write_apk(["Lcom/google/common/base/Strings;", "Lio/github/App;"],
                                  {"com.google.common.collect.CompactHashMap": "a.b"})

        result = self.classify([
            dependency(GUAVA, guava, [FLOSS_RELEASE, FULL_RELEASE],
                       vulnerabilities=[vulnerability("CVE-2026-1", 5.0)]),
            dependency(SQLITE, sqlite, [FLOSS_RELEASE], vulnerabilities=[vulnerability("CVE-2026-2", 5.0)]),
            dependency("pkg:maven/x/gone@1", self.root / "missing.jar", [FLOSS_RELEASE],
                       vulnerabilities=[vulnerability("CVE-2026-3", 5.0)]),
        ], apks={"floss": evidence})

        floss = {entry["id"]: entry["reachability"] for entry in result["flavors"]["floss"]["packagedRelease"]}
        self.assertEqual("in-apk", floss["CVE-2026-1"]["kind"])
        self.assertEqual(2, floss["CVE-2026-1"]["keptClasses"])
        self.assertEqual(3, floss["CVE-2026-1"]["artifactClasses"])
        self.assertIn("com.google.common.collect.CompactHashMap -> a.b", floss["CVE-2026-1"]["examples"])
        self.assertEqual("removed-by-r8", floss["CVE-2026-2"]["kind"])
        self.assertEqual("artifact-unread", floss["CVE-2026-3"]["kind"])
        self.assertTrue(result["flavors"]["floss"]["apkChecked"])
        self.assertFalse(result["flavors"]["full"]["apkChecked"])
        # No FULL APK was given, so its copy of Guava rests on the classpath alone
        self.assertEqual(["release-runtime-classpath"],
                         [entry["reachability"]["kind"] for entry in result["flavors"]["full"]["packagedRelease"]])

    def test_the_text_report_names_every_section_and_what_blocks(self) -> None:
        result = self.classify([dependency(GUAVA, "guava.jar", [FLOSS_RELEASE],
                                           vulnerabilities=[vulnerability("CVE-2026-1", 9.8)])])

        text = reach.render_text(result)

        self.assertIn("FLOSS, packaged in the release APK (1, no APK checked)", text)
        self.assertIn("FULL, build host, lint and tests only (0, no APK checked)", text)
        self.assertIn("BLOCKING", text)
        self.assertIn("Blocking (1)\n  CVE-2026-1 9.8 " + GUAVA + " (guava.jar)\n"
                      "    floss: no release APK given, so it's treated as shipped; "
                      "the scan resolved it in app:flossReleaseRuntimeClasspath\n", text)
        self.assertNotIn("    full:", text)
        self.assertNotIn("problems", text)

        # A host-only blocking finding lists every module and configuration, not the first three
        host_refs = ["app:androidLintTool", "server:runtimeClasspath", "libcore:testRuntimeClasspath",
                     "docs:compileClasspath"]
        host = self.classify([dependency(NETTY, "netty.jar", host_refs,
                                         vulnerabilities=[vulnerability("CVE-2026-2", 9.8)])])
        every = "app:androidLintTool, docs:compileClasspath, libcore:testRuntimeClasspath, server:runtimeClasspath"
        host_text = reach.render_text(host)
        self.assertIn(f"    floss: {every}\n    full: {every}\n", host_text)
        self.assertIn("app:androidLintTool, docs:compileClasspath, libcore:testRuntimeClasspath and 1 more",
                      host_text)

        result["validationProblems"] = ["disposition 1 (CVE-2026-9) matches no finding in this scan"]
        self.assertIn("Suppression and disposition problems (1)\n"
                      "  disposition 1 (CVE-2026-9) matches no finding in this scan", reach.render_text(result))


class ApkEvidenceTest(ReachabilityTestBase):
    def test_an_apk_without_dex_and_an_empty_mapping_are_errors(self) -> None:
        apk = self.root / "empty.apk"
        with zipfile.ZipFile(apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"\0")
        mapping = self.root / "mapping.txt"
        mapping.write_text("# compiler: R8\n", encoding="utf-8")

        with self.assertRaisesRegex(reach.ReachabilityError, "has no DEX classes"):
            reach.read_dex_types(apk)
        with self.assertRaisesRegex(reach.ReachabilityError, "lists no classes"):
            reach.read_mapping(mapping)

    def test_an_aar_contributes_its_classes_jar_and_bundled_libs(self) -> None:
        aar = self.root / "lib.aar"
        with zipfile.ZipFile(aar, "w") as archive:
            archive.writestr("classes.jar", jar_bytes(["a.One"]))
            archive.writestr("libs/extra.jar", jar_bytes(["a.Two"]))
            archive.writestr("res/values/values.xml", "<resources/>")

        self.assertEqual({"a.One", "a.Two"}, reach.artifact_classes(aar))
        self.assertIsNone(reach.artifact_classes(self.root / "absent.aar"))


if __name__ == "__main__":
    unittest.main()
