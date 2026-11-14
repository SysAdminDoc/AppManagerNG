#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Sorts dependency-check findings by whether the code ships in a release APK.

Dependency-Check scans every resolvable configuration, so one report mixes the libraries packed
into the APKs with Gradle plugin, lint, unit test and device test tooling. This splits each
flavor's findings into what its release runtime classpath carries, what doesn't apply to the
artifact at all (a CPE name collision or a CVE upstream calls fabricated), and what only the build
host runs. Each finding keeps its evidence: the module and configuration that resolved it, the
files scanned, the lockfile entry, and with release APKs given, how many of the artifact's classes
R8 kept.

The release APKs are minified, so a class name search of the DEX files misses renamed classes.
The R8 mapping names every class it renamed and the DEX names every class it didn't, so together
they say exactly which of an artifact's classes are in the APK.

Nothing here unblocks a release. The scanner's CVSS threshold still decides, and only a
suppression can clear a finding; the classification checks those suppressions instead of
trusting them.
"""

from __future__ import annotations

import functools
import importlib
import itertools
import json
import re
import struct
import zipfile
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path
from typing import Any, Iterable, Mapping, Sequence
from urllib.parse import unquote
import xml.etree.ElementTree as ElementTree


SHIPPED_MODULE = "app"
FLAVORS = ("floss", "full")
# Tags a suppression's notes start with, so the report can say why a finding was cleared.
SUPPRESSION_TAGS = ("host-only", "cpe-name-collision", "not-affected", "fabricated")
# Dispositions recorded without suppressing anything: they label findings, never clear them.
FILE_DISPOSITIONS = ("fabricated", "cpe-name-collision", "not-affected", "held")
NOT_APPLICABLE = ("cpe-name-collision", "fabricated", "not-affected")
# Configurations whose code goes into every release APK besides the flavor's runtime classpath: L8
# dexes the desugared JDK library from coreLibraryDesugaring into each one.
SHIPPED_IN_EVERY_FLAVOR = ("coreLibraryDesugaring",)
# A CPE disposition names one product: part, vendor and product, ending at the version separator so
# sqlite:sqlite can't also take sqlite:sqlite_jdbc.
CPE_PRODUCT = re.compile(r"cpe:2\.3:[aho]:[^:*?]+:[^:*?]+:")
# Dependency-Check's estimate for a finding that carries a severity word but no score.
UNSCORED_SEVERITY = {"critical": 10.0, "high": 8.9, "moderate": 6.9, "medium": 6.9, "low": 3.9}
SUPPRESSION_NAMESPACE = "{https://jeremylong.github.io/DependencyCheck/dependency-suppression.1.4.xsd}"
PROBE_VERSION = "0.0.0-appmanagerng-version-probe"
DEX_MAGIC = b"dex\n"


class ReachabilityError(RuntimeError):
    pass


def release_configuration(flavor: str) -> str:
    return f"{flavor}ReleaseRuntimeClasspath"


@dataclass(frozen=True, order=True)
class Reference:
    module: str
    configuration: str

    def __str__(self) -> str:
        return f"{self.module}:{self.configuration}"


@dataclass
class Finding:
    id: str
    artifact: str
    source: str
    severity: str
    scores: dict
    score: float
    suppressed: bool
    files: set = field(default_factory=set)
    references: set = field(default_factory=set)
    # The vulnerable-software CPEs the scanner matched this artifact to
    cpes: set = field(default_factory=set)
    description: str = ""

    @property
    def coordinate(self) -> str | None:
        return purl_coordinate(self.artifact)


@dataclass(frozen=True)
class SuppressionRule:
    index: int
    tag: str | None
    notes: str
    ids: tuple
    package_url: str | None
    package_url_regex: bool
    case_sensitive: bool
    blanket_selectors: tuple
    has_file_selector: bool

    @property
    def regex_flags(self) -> int:
        return 0 if self.case_sensitive else re.IGNORECASE

    def matches_package(self, purl: str) -> bool:
        if self.package_url is None:
            return False
        if self.package_url_regex:
            return re.fullmatch(self.package_url, purl, self.regex_flags) is not None
        return purl == self.package_url if self.case_sensitive else purl.lower() == self.package_url.lower()

    def describe(self) -> str:
        names = ", ".join(self.ids) or "no named vulnerability"
        return f"suppression {self.index} ({names})"


@dataclass(frozen=True)
class Disposition:
    index: int
    ids: tuple
    kind: str
    reason: str
    source: str
    reviewed: str
    package_url: str | None
    reachability: str | None
    # A CPE name collision can cover every CVE filed against the product it was mistaken for
    cpe: str | None = None

    @property
    def name(self) -> str:
        return f"disposition {self.index} ({self.cpe or ', '.join(self.ids) or 'no ids'})"

    def matches_package(self, purl: str) -> bool:
        return self.package_url is None or re.fullmatch(self.package_url, purl) is not None

    def applies(self, finding: Finding) -> bool:
        if not self.matches_package(finding.artifact):
            return False
        if self.cpe is not None:
            # Every CPE the finding was matched by has to be that product, or part of it may be real
            return bool(finding.cpes) and all(cpe.startswith(self.cpe) for cpe in finding.cpes)
        return finding.id in self.ids

    def as_json(self) -> dict:
        record = {"kind": self.kind, "reason": self.reason, "source": self.source,
                  "reviewed": self.reviewed, "recordedIn": "dispositions"}
        if self.cpe:
            record["cpe"] = self.cpe
        if self.reachability:
            record["reachability"] = self.reachability
        return record


def purl_coordinate(purl: str) -> str | None:
    match = re.fullmatch(r"pkg:maven/([^/]+)/([^@]+)@(.+)", purl)
    if not match:
        return None
    return ":".join(unquote(part) for part in match.groups())


def version_probes(purl: str, known: Iterable[str] = ()) -> list[str]:
    """The same package at versions next to this one, to tell a rule pinned to its versions from one
    that takes others too: a made-up version, each number in it raised by one, lowered by one and set
    to zero, and the version with a part added at either end. Versions the scan itself found are left
    out, since a rule may name each of them."""
    if "@" not in purl:
        return []
    package, version = purl.rsplit("@", 1)
    versions = {PROBE_VERSION, version + ".1", "1" + version}
    for number in re.finditer(r"\d+", version):
        value = int(number.group())
        for neighbor in {value + 1, max(value - 1, 0), 0}:
            versions.add(version[:number.start()] + str(neighbor) + version[number.end():])
    skip = {k.lower() for k in known} | {purl.lower()}
    return [probe for probe in (f"{package}@{v}" for v in sorted(versions)) if probe.lower() not in skip]


_sre: Any
_sre_constants: Any
try:
    # The regex parser is private, but it's the only way to read which strings a pattern takes.
    # Without it the blanket check falls back to the probes above.
    _sre = importlib.import_module("re._parser")
    _sre_constants = importlib.import_module("re._constants")
except ImportError:
    _sre = _sre_constants = None
# Stand for what a pattern doesn't pin down: _ONE for exactly one character, like . or [^@], and
# _OPEN for a run of any length, like [^@]+, .* or a part too big or too odd to list
_OPEN = "\x00"
_ONE = "\x01"
# Characters plus strings, so neither one string repeated a few levels deep nor thousands of short
# or empty ones get built
_SIZE_LIMIT = 16384
# All the strings one read builds, at every level together. Each group is read while the strings
# before it are still held, so a per-part limit alone lets deep nesting hold one full part per level.
# It also bounds the time, since every string built is charged to it.
_WORK_LIMIT = 4 * _SIZE_LIMIT
_CLASS_LIMIT = 64
_REPEAT_LIMIT = 64
_SPLIT_LIMIT = 64
# Templates with unpinned characters, each of which is tried on every scanned package
_PATTERNED_LIMIT = 256
# Every error re can raise on a pattern it won't compile, not only re.error
PATTERN_ERRORS = (re.error, OverflowError, RecursionError)


def pattern_versions(pattern: str, flags: int = 0) -> list[tuple[str, str | None]] | None:
    """Each package and version a pattern takes, as (package template, version) pairs. The package
    template marks unpinned characters with _ONE and _OPEN, and the version is None when the pattern
    doesn't pin it to a fixed string. Parts too big or too odd to list are kept as _OPEN, so the pairs
    cover at least every string the pattern takes. None when the pattern doesn't parse."""
    if _sre is None:
        return None
    try:
        items = list(_sre.parse(pattern, flags))
    except PATTERN_ERRORS:
        return None
    try:
        strings = _expand(items, _Budget())
    except (RecursionError, _OverBudget):
        # Nested deeper, or bigger, than this reader goes, so it's read as taking anything
        strings = [_OPEN]
    pairs = []
    for string in strings:
        package, at, version = string.rpartition("@")
        if at:
            pairs.append((package, None if _ONE in version or _OPEN in version else version))
            continue
        # No literal @, so one of the unpinned characters stands for it
        markers = [index for index, char in enumerate(string) if char in (_ONE, _OPEN)]
        if len(markers) > _SPLIT_LIMIT:
            # Too many places to split, so everything after the first one is read as open
            markers = markers[:1]
            string = string[:markers[0]] + _OPEN
        for index in markers:
            if string[index] == _OPEN:
                # A run takes the @ and any characters on either side of it, so it covers every
                # split after it too
                pairs.append((string[:index] + _OPEN, None))
                break
            rest = string[index + 1:]
            pairs.append((string[:index], None if _ONE in rest or _OPEN in rest else rest))
    pairs = list(dict.fromkeys(pairs))
    if sum(_is_patterned(template) for template, _ in pairs) > _PATTERNED_LIMIT:
        # Too many to try on every package, so they're read as one that takes anything
        pairs = [pair for pair in pairs if not _is_patterned(pair[0])] + [(_OPEN, None)]
    return pairs


def _is_patterned(template: str) -> bool:
    return _ONE in template or _OPEN in template


def _size(strings: list[str]) -> int:
    return sum(map(len, strings)) + len(strings)


class _OverBudget(Exception):
    """The strings one read built went over _WORK_LIMIT."""


class _Budget:
    def __init__(self) -> None:
        self.left = _WORK_LIMIT

    def spend(self, size: int) -> None:
        self.left -= size
        if self.left < 0:
            raise _OverBudget


def _expand(items: list, budget: _Budget) -> list[str]:
    c = _sre_constants
    results = [""]
    size = _size(results)
    open_tail = False
    for op, av in items:
        if op == c.LITERAL:
            options = [chr(av)]
        elif op in (c.AT, c.ASSERT, c.ASSERT_NOT):
            # Takes no characters, and leaving its condition out only takes more strings
            options = [""]
        elif op == c.SUBPATTERN:
            options = _expand(list(av[-1]), budget)
        elif op == c.ATOMIC_GROUP:
            options = _expand(list(av), budget)
        elif op == c.BRANCH:
            options = []
            options_size = 0
            for branch in av[1]:
                side = _expand(list(branch), budget)
                options += side
                # A running total: summing every side again after each one is quadratic in the sides
                options_size += _size(side)
                if options_size > _SIZE_LIMIT:
                    options = [_OPEN]
                    break
        elif op == c.IN:
            # Each character adds a copy of every string so far plus one more character to each
            options = _class_options(av, _SIZE_LIMIT // (size + len(results)))
        elif op in (c.ANY, c.NOT_LITERAL):
            options = [_ONE]
        elif op in (c.MAX_REPEAT, c.MIN_REPEAT, c.POSSESSIVE_REPEAT):
            options = _repeat_options(*av, budget)
        else:
            options = [_OPEN]
        if options == [""]:
            # Adds nothing to any string, and rebuilding every string for it adds up over many of them
            continue
        # Sized before building: each string so far followed by each option
        option_size = _size(options)
        new_size = len(options) * size + len(results) * option_size - len(results) * len(options)
        if new_size > _SIZE_LIMIT:
            options = [_OPEN]
            new_size = size + len(results)
        if options == [_OPEN] and open_tail:
            # Two runs in a row take what one does, so the strings stop growing here
            continue
        # Only what this step adds, since charging every string again at each step would make one long
        # string cost the square of its length
        budget.spend(new_size - size)
        size = new_size
        results = [result + option for result in results for option in options]
        open_tail = options == [_OPEN]
    return results


def _repeat_options(low: int, high: int, item: list, budget: _Budget) -> list[str]:
    if high == _sre_constants.MAXREPEAT or high > _REPEAT_LIMIT:
        return [_OPEN]
    inner = _expand(list(item), budget)
    longest = max(map(len, inner), default=0)
    # Sized before building, since even one option repeated a few levels deep is a lot of text
    size = sum(len(inner) ** count * (count * longest + 1) for count in range(low, high + 1))
    if size > _SIZE_LIMIT:
        return [_OPEN]
    budget.spend(size)
    return ["".join(parts) for count in range(low, high + 1)
            for parts in itertools.product(inner, repeat=count)]


def _class_options(items: list, most: int = _SIZE_LIMIT // 2) -> list[str]:
    """The characters a class takes, [_ONE] for one character of any kind, or [_OPEN] when there are
    more than the `most` that fit where the class stands."""
    c = _sre_constants
    count = 0
    for op, av in items:
        if op == c.LITERAL:
            count += 1
        elif op == c.RANGE and av[1] - av[0] < _CLASS_LIMIT:
            count += av[1] - av[0] + 1
        elif op == c.CATEGORY and av == c.CATEGORY_DIGIT:
            count += 10
        else:
            # A negated class or a wide range takes characters nobody listed
            return [_ONE]
    if count > _SIZE_LIMIT // 2:
        # Counted before building, since many narrow ranges make a wide one. Listed, it would go over
        # the limit even on its own and be read as a run, and one character of any kind is closer.
        return [_ONE]
    if count > most:
        # The step would go over the limit with these and read them as a run anyway, so they aren't
        # listed only to be dropped
        return [_OPEN]
    chars: list[str] = []
    for op, av in items:
        if op == c.LITERAL:
            chars.append(chr(av))
        elif op == c.RANGE:
            chars.extend(chr(code) for code in range(av[0], av[1] + 1))
        else:
            chars.extend("0123456789")
    return chars


# Report -------------------------------------------------------------------------------------

def _score(vulnerability: dict) -> tuple[dict, float]:
    scores = {}
    for name, keys in (("cvssv2", ("score", "baseScore")), ("cvssv3", ("baseScore",)),
                       ("cvssv4", ("baseScore",))):
        block = vulnerability.get(name)
        if isinstance(block, dict):
            for key in keys:
                value = block.get(key)
                if isinstance(value, (int, float)):
                    scores[name] = float(value)
                    break
    if scores:
        return scores, max(scores.values())
    # No score at all: estimate from the severity word the way the scanner does, and treat a
    # word it can't read as the worst case.
    severity = str(vulnerability.get("severity", "")).lower()
    return scores, UNSCORED_SEVERITY.get(severity, 10.0)


def _references(dependency: dict) -> set:
    references = set()
    for raw in dependency.get("projectReferences") or ():
        text = str(raw)
        if ":" in text:
            module, configuration = text.rsplit(":", 1)
            references.add(Reference(module, configuration))
    return references


def load_report(report_path: Path) -> tuple[list[Finding], set]:
    """Reads Dependency-Check's JSON report into one finding per vulnerability and artifact.

    Also returns every package URL the scan identified, vulnerable or not, so suppression rules
    can be checked against the packages they could match.
    """
    try:
        report = json.loads(report_path.read_text(encoding="utf-8"))
        dependencies = report["dependencies"]
        if not isinstance(dependencies, list):
            raise TypeError("dependencies is not a list")
    except (OSError, ValueError, KeyError, TypeError) as exc:
        raise ReachabilityError(f"the dependency-check JSON report {report_path} is unreadable: {exc}") from exc
    grouped: dict[tuple[str, str], Finding] = {}
    every_purl: set = set()
    for dependency in dependencies:
        purls = [str(package.get("id")) for package in dependency.get("packages") or ()
                 if str(package.get("id", "")).startswith("pkg:")]
        every_purl.update(purls)
        artifact = purls[0] if purls else str(dependency.get("fileName") or dependency.get("filePath"))
        references = _references(dependency)
        file_path = dependency.get("filePath")
        for suppressed, key in ((False, "vulnerabilities"), (True, "suppressedVulnerabilities")):
            for vulnerability in dependency.get(key) or ():
                finding_id = str(vulnerability.get("name"))
                scores, score = _score(vulnerability)
                finding = grouped.get((finding_id, artifact))
                if finding is None:
                    finding = Finding(finding_id, artifact, str(vulnerability.get("source", "")),
                                      str(vulnerability.get("severity", "")), scores, score, suppressed,
                                      description=str(vulnerability.get("description", ""))[:300])
                    grouped[(finding_id, artifact)] = finding
                else:
                    # One copy left unsuppressed keeps the whole finding unsuppressed.
                    finding.suppressed = finding.suppressed and suppressed
                    if score > finding.score:
                        finding.score, finding.scores = score, scores
                if file_path:
                    finding.files.add(str(file_path))
                finding.references |= references
                finding.cpes |= {str(entry["software"]["id"]) for entry in vulnerability.get("vulnerableSoftware") or ()
                                 if isinstance(entry, dict) and isinstance(entry.get("software"), dict)
                                 and str(entry["software"].get("vulnerabilityIdMatched")).lower() == "true"
                                 and entry["software"].get("id")}
    return sorted(grouped.values(), key=lambda f: (-f.score, f.id, f.artifact)), every_purl


# Suppressions -------------------------------------------------------------------------------

def _text(element: ElementTree.Element | None) -> str:
    return "" if element is None or element.text is None else element.text.strip()


def load_suppressions(path: Path) -> list[SuppressionRule]:
    if not path.is_file():
        return []
    try:
        root = ElementTree.parse(path).getroot()
    except (OSError, ElementTree.ParseError) as exc:
        raise ReachabilityError(f"the suppression file {path} is unreadable: {exc}") from exc
    ns = SUPPRESSION_NAMESPACE
    # Rules in any other schema would read as no rules at all, so nothing they suppress gets checked
    if root.tag != f"{ns}suppressions":
        raise ReachabilityError(f"the suppression file {path} has root {root.tag}; expected suppressions "
                                f"in {ns.strip('{}')}")
    rules = []
    for index, suppress in enumerate(root.findall(f"{ns}suppress"), start=1):
        notes = " ".join(_text(suppress.find(f"{ns}notes")).split())
        tag_match = re.match(r"\[([a-z-]+)\]", notes)
        ids = tuple(_text(child) for name in ("cve", "vulnerabilityName")
                    for child in suppress.findall(f"{ns}{name}"))
        package = suppress.find(f"{ns}packageUrl")
        blanket = tuple(name for name in ("cvssBelow", "cvssV2Below", "cvssV3Below", "cvssV4Below", "cwe", "cpe")
                        if suppress.find(f"{ns}{name}") is not None)
        has_file_selector = any(suppress.find(f"{ns}{name}") is not None for name in ("sha1", "filePath", "gav"))
        rules.append(SuppressionRule(
            index=index,
            tag=tag_match.group(1) if tag_match else None,
            notes=notes,
            ids=ids,
            package_url=_text(package) if package is not None else None,
            package_url_regex=package is not None and package.get("regex", "false").lower() == "true",
            case_sensitive=package is not None and package.get("caseSensitive", "false").lower() == "true",
            blanket_selectors=blanket,
            has_file_selector=has_file_selector,
        ))
    return rules


def check_suppressions(rules: Sequence[SuppressionRule]) -> list[str]:
    """Problems visible without a scan: every rule names its vulnerabilities, its package and why."""
    problems = []
    for rule in rules:
        if rule.tag not in SUPPRESSION_TAGS:
            problems.append(f"{rule.describe()} has no disposition: its notes must start with one of "
                            + ", ".join(f"[{tag}]" for tag in SUPPRESSION_TAGS))
        if not rule.ids:
            problems.append(f"{rule.describe()} names no CVE, so it clears every vulnerability of its package")
        if rule.blanket_selectors:
            problems.append(f"{rule.describe()} also matches by {', '.join(rule.blanket_selectors)}, "
                            "which clears findings nobody reviewed")
        if rule.package_url is None:
            problems.append(f"{rule.describe()} has no packageUrl, so its reach can't be checked against "
                            "the scan; pin it to the package and version it was reviewed for")
        elif rule.has_file_selector:
            problems.append(f"{rule.describe()} mixes packageUrl with another package selector")
        if rule.package_url is not None and rule.package_url_regex:
            try:
                re.compile(rule.package_url, rule.regex_flags)
            except PATTERN_ERRORS as exc:
                # Java takes some syntax Python doesn't (\Q...\E, possessive classes), and the
                # checks after the scan read the pattern with Python
                problems.append(f"{rule.describe()} has a packageUrl pattern this check can't read: {exc}")
    return problems


def check_suppressions_against_scan(rules: Sequence[SuppressionRule], purls: Iterable[str],
                                    findings: Sequence[Finding]) -> list[str]:
    """Problems that need the scan: stale rules, version-blanket rules and host-only rules on shipped code."""
    problems = []
    purls = sorted(set(purls))
    for rule in rules:
        if rule.package_url is None:
            continue
        for cve in rule.ids:
            if not any(finding.id == cve and rule.matches_package(finding.artifact) for finding in findings):
                problems.append(f"{rule.describe()} no longer matches {cve} in this scan; update or remove it")
        problems += _blanket_problems(rule.describe(), rule.matches_package,
                                      [purl for purl in purls if rule.matches_package(purl)], purls,
                                      rule.package_url if rule.package_url_regex else None, rule.regex_flags)
        if rule.tag != "host-only":
            continue
        for finding in findings:
            if finding.suppressed and finding.id in rule.ids and rule.matches_package(finding.artifact):
                shipped = sorted(str(ref) for ref in finding.references if is_shipped(ref))
                if shipped:
                    problems.append(f"{rule.describe()} is tagged host-only, but {finding.artifact} "
                                    f"now resolves into {', '.join(shipped)}")
    return problems


def _blanket_problems(name: str, matches, matched_purls: Iterable[str], known: Iterable[str],
                      pattern: str | None = None, flags: int = 0) -> list[str]:
    """One problem when a package pattern also takes a version of a scanned package that the scan
    doesn't contain. Every version the pattern names is tried on every scanned package it fits, and
    the probes next to each matched version, so neither a gap in a list nor a range below the scanned
    version slips through."""
    known = set(known)
    scanned = _KnownPurls(known)
    pairs = pattern_versions(pattern, flags) if pattern is not None else None
    # A template with nothing unpinned is one package, looked up instead of tried on every package
    concrete: dict[str, list[str | None]] = {}
    patterned = []
    for template, version in pairs or ():
        if template.isascii() and not _is_patterned(template):
            concrete.setdefault(template.lower(), []).append(version)
        else:
            patterned.append((template, version))
    matched = sorted(set(matched_purls))
    # The packages it matched first, then every other scanned one, since a pattern that matched
    # one package at its scanned version can still take another one at a version nobody scanned
    packages = [purl.rsplit("@", 1)[0] for purl in matched]
    packages += sorted({purl.rsplit("@", 1)[0] for purl in known} - set(packages))
    probes: dict[str, list[str]] = {}
    for purl in matched:
        probes.setdefault(purl.rsplit("@", 1)[0], []).extend(version_probes(purl, known))
    open_ended = []
    for package in packages:
        candidates = probes.get(package, [])
        if package.isascii():
            versions = list(concrete.get(package.lower(), ()))
        else:
            versions = [version for template, listed in concrete.items() if _template_matches(template, package)
                        for version in listed]
        versions += [version for template, version in patterned if _template_matches(template, package)]
        for version in versions:
            if version is None:
                open_ended.append(package)
            else:
                candidates.append(f"{package}@{version}")
        for probe in candidates:
            if probe not in scanned and matches(probe):
                return [f"{name} also matches {probe}, so it covers versions of {package} "
                        "nobody reviewed; pin it to the versions it was reviewed for"]
    if open_ended:
        # No concrete version came up, but the pattern still takes ones it doesn't name
        return [f"{name} takes versions of {open_ended[0]} its pattern doesn't list, so it covers versions "
                "nobody reviewed; pin it to the versions it was reviewed for"]
    return []


class _KnownPurls:
    """The scan's purls, looked up ignoring case the way re.IGNORECASE does, so a rule spelling a
    scanned version with a letter re takes for its other case is read as naming that version."""

    def __init__(self, purls: Iterable[str]) -> None:
        # Kept by key, so a lookup compares only the few purls that could be spelled the same way.
        # Comparing every purl the scan holds took seconds over many probes outside ASCII.
        self._by_key: dict[bytes, list[str]] = {}
        for purl in purls:
            self._by_key.setdefault(_case_key(purl), []).append(purl)

    def __contains__(self, purl: str) -> bool:
        # A shared key means the same length, so the letters pair up one to one
        return any(all(char == known or _same_letter(char, known) for char, known in zip(purl, other))
                   for other in self._by_key.get(_case_key(purl), ()))


def _case_key(purl: str) -> bytes:
    """The purl with each ASCII letter in lower case, and a ? for every character re.IGNORECASE may
    pair with one outside ASCII. Only a dotted capital I and a dotless i (for i), a long s (for s)
    and the Kelvin sign (for k) pair with an ASCII letter, so two spellings re takes for each other
    always get the same key, and str.lower() never has to agree with re. Each character outside
    ASCII encodes to one ?, so the key keeps the purl's length. Both steps run in C: str.translate
    calls back into Python for every character outside ASCII, which took seconds over a long one."""
    return purl.encode("ascii", "replace").translate(_CASE_KEY)


_CASE_KEY = bytes(ord("?") if chr(code) in "IKSiks" else ord(chr(code).lower()) for code in range(0x80)) + b"?" * 0x80


def _template_matches(template: str, package: str) -> bool:
    """Whether the package fits the template, with _OPEN as any run and _ONE as any one character.
    A glob match, so it stays linear where a regex of .* parts can backtrack for minutes. Always
    ignoring case, since an inline (?i) can turn it on for part of a case-sensitive rule. That only
    picks more of the versions to try, and each one is then tried on the rule itself."""
    first, *middle = template.split(_OPEN)
    if not middle:
        return len(first) == len(package) and _fits(first, package, 0)
    last = middle.pop()
    end = len(package) - len(last)
    if end < len(first) or not _fits(first, package, 0) or not _fits(last, package, end):
        return False
    position = len(first)
    for part in middle:
        # The leftmost place each fixed-length part fits leaves the most room for the rest
        position = next((start for start in range(position, end - len(part) + 1)
                         if _fits(part, package, start)), -1)
        if position < 0:
            return False
        position += len(part)
    return True


def _fits(part: str, text: str, start: int) -> bool:
    return all(char == _ONE or char == other or _same_letter(char, other)
               for char, other in zip(part, text[start:start + len(part)]))


@functools.lru_cache(maxsize=4096)
def _same_letter(char: str, other: str) -> bool:
    """Whether re.IGNORECASE takes one character for the other. str.lower() and str.upper() disagree
    with it outside ASCII (re takes a dotted capital I for i, and the two forms of theta for each
    other), so anything outside ASCII is left to re itself."""
    if char.isascii() and other.isascii():
        return char.lower() == other.lower()
    return re.fullmatch(re.escape(char), other, re.IGNORECASE) is not None


def suppression_for(rules: Sequence[SuppressionRule], finding: Finding) -> SuppressionRule | None:
    for rule in rules:
        if finding.id in rule.ids and rule.matches_package(finding.artifact):
            return rule
    return None


# Dispositions -------------------------------------------------------------------------------

def load_dispositions(path: Path) -> list[Disposition]:
    if not path.is_file():
        return []
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        entries = data["dispositions"]
        if data.get("schemaVersion") != 1 or not isinstance(entries, list):
            raise ValueError("expected schemaVersion 1 and a dispositions list")
    except (OSError, ValueError, KeyError, TypeError, AttributeError) as exc:
        raise ReachabilityError(f"the disposition file {path} is unreadable: {exc}") from exc
    dispositions = []
    for index, entry in enumerate(entries, start=1):
        if not isinstance(entry, dict):
            raise ReachabilityError(f"disposition {index} in {path} is not an object")
        ids = entry.get("ids")
        dispositions.append(Disposition(
            index=index,
            ids=tuple(str(i) for i in ids) if isinstance(ids, list) else (),
            kind=str(entry.get("disposition", "")),
            reason=str(entry.get("reason", "")),
            source=str(entry.get("source", "")),
            reviewed=str(entry.get("reviewed", "")),
            package_url=str(entry["packageUrl"]) if entry.get("packageUrl") else None,
            reachability=str(entry["reachability"]) if entry.get("reachability") else None,
            cpe=str(entry["cpe"]) if entry.get("cpe") else None,
        ))
    return dispositions


def check_dispositions(dispositions: Sequence[Disposition]) -> list[str]:
    problems = []
    for disposition in dispositions:
        name = disposition.name
        if not disposition.ids and disposition.cpe is None:
            problems.append(f"{name} names no vulnerability")
        if disposition.ids and disposition.cpe is not None:
            problems.append(f"{name} names its vulnerabilities both by id and by cpe; use one")
        if disposition.cpe is not None:
            if disposition.kind != "cpe-name-collision":
                problems.append(f"{name} is keyed by cpe, which only a cpe-name-collision can be")
            if not CPE_PRODUCT.fullmatch(disposition.cpe):
                problems.append(f"{name} needs a cpe that names one product, like cpe:2.3:a:vendor:product:")
        if disposition.kind not in FILE_DISPOSITIONS:
            problems.append(f"{name} has disposition {disposition.kind!r}; expected one of "
                            + ", ".join(FILE_DISPOSITIONS))
        if not disposition.reason or not disposition.source.startswith("https://"):
            problems.append(f"{name} needs a reason and an https source")
        try:
            date.fromisoformat(disposition.reviewed)
        except ValueError:
            problems.append(f"{name} needs a reviewed date (YYYY-MM-DD)")
        if disposition.kind != "fabricated" and disposition.package_url is None:
            problems.append(f"{name} must name the packageUrl it was reviewed for; only a fabricated "
                            "CVE applies to every package")
        if disposition.kind == "held" and not disposition.reachability:
            problems.append(f"{name} is held, so it must say how the shipped code could be reached")
        if disposition.package_url is not None:
            try:
                re.compile(disposition.package_url)
            except PATTERN_ERRORS as exc:
                problems.append(f"{name} has an invalid packageUrl pattern: {exc}")
    return problems


def check_dispositions_against_scan(dispositions: Sequence[Disposition], purls: Iterable[str],
                                    findings: Sequence[Finding]) -> list[str]:
    """A disposition the scan no longer reports something for is stale, one id at a time."""
    problems = []
    purls = sorted(set(purls))
    for disposition in dispositions:
        name = disposition.name
        matched = [f for f in findings if disposition.applies(f)]
        if disposition.cpe is not None:
            if not matched:
                problems.append(f"{name} matches no finding in this scan; update or remove it")
        else:
            for finding_id in disposition.ids:
                if not any(f.id == finding_id for f in matched):
                    problems.append(f"{name} matches no {finding_id} finding in this scan; update or remove it")
        if disposition.package_url is not None:
            problems += _blanket_problems(name, disposition.matches_package,
                                          [purl for purl in purls if disposition.matches_package(purl)], purls,
                                          disposition.package_url)
    return problems


# Lockfile, mapping and DEX evidence ---------------------------------------------------------

def shipped_configurations(flavor: str) -> set:
    return {release_configuration(flavor), *SHIPPED_IN_EVERY_FLAVOR}


def is_shipped(reference: Reference, flavor: str | None = None) -> bool:
    if reference.module.rsplit(":", 1)[-1] != SHIPPED_MODULE:
        return False
    flavors = FLAVORS if flavor is None else (flavor,)
    return any(reference.configuration in shipped_configurations(f) for f in flavors)


def load_lockfile(path: Path) -> dict:
    """Maps group:name:version to the configurations that resolve it."""
    entries: dict[str, set] = {}
    if not path.is_file():
        return entries
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        coordinate, configurations = line.split("=", 1)
        entries[coordinate] = {c for c in configurations.split(",") if c}
    return entries


def read_dex_types(apk: Path) -> set:
    """Class names, dotted, that the APK's DEX files define or reference by their real name."""
    names = set()
    try:
        with zipfile.ZipFile(apk) as archive:
            for entry in archive.namelist():
                if re.fullmatch(r"classes\d*\.dex", entry):
                    names |= _dex_type_names(archive.read(entry))
    except (OSError, zipfile.BadZipFile) as exc:
        raise ReachabilityError(f"the APK {apk} is unreadable: {exc}") from exc
    if not names:
        raise ReachabilityError(f"the APK {apk} has no DEX classes")
    return names


def _uleb128(data: bytes, offset: int) -> tuple[int, int]:
    result = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7


def _dex_type_names(data: bytes) -> set:
    if data[:4] != DEX_MAGIC:
        raise ReachabilityError("a DEX entry has no dex magic")
    string_ids_size, string_ids_off, type_ids_size, type_ids_off = struct.unpack_from("<4I", data, 0x38)
    names = set()
    for index in range(type_ids_size):
        (string_index,) = struct.unpack_from("<I", data, type_ids_off + 4 * index)
        if string_index >= string_ids_size:
            raise ReachabilityError("a DEX type points past the string table")
        (string_offset,) = struct.unpack_from("<I", data, string_ids_off + 4 * string_index)
        _, start = _uleb128(data, string_offset)
        descriptor = data[start:data.index(b"\0", start)].decode("utf-8", "replace")
        if descriptor.startswith("L") and descriptor.endswith(";"):
            names.add(descriptor[1:-1].replace("/", "."))
    return names


def read_mapping(mapping: Path) -> dict:
    """Original class name to the name R8 gave it, for every class the mapping lists."""
    renamed = {}
    try:
        with mapping.open(encoding="utf-8") as stream:
            for line in stream:
                if line[:1] in ("#", " ", "\t", "\n", ""):
                    continue
                match = re.match(r"(\S+) -> (\S+):$", line.rstrip("\n"))
                if match:
                    renamed[match.group(1)] = match.group(2)
    except OSError as exc:
        raise ReachabilityError(f"the R8 mapping {mapping} is unreadable: {exc}") from exc
    if not renamed:
        raise ReachabilityError(f"the R8 mapping {mapping} lists no classes")
    return renamed


@dataclass
class ApkEvidence:
    apk: Path
    mapping: Path
    dex_types: set
    renamed: dict

    @classmethod
    def load(cls, apk: Path, mapping: Path) -> "ApkEvidence":
        return cls(apk, mapping, read_dex_types(apk), read_mapping(mapping))

    def kept(self, class_name: str) -> str | None:
        """The name the class has in the APK, or None when R8 removed it."""
        if class_name in self.renamed:
            return self.renamed[class_name]
        return class_name if class_name in self.dex_types else None


def artifact_classes(path: Path) -> set | None:
    """Class names in a jar, or in an AAR's jars; None when the file isn't there to read."""
    if not path.is_file():
        return None
    try:
        with zipfile.ZipFile(path) as archive:
            if path.suffix.lower() == ".aar":
                classes = set()
                for entry in archive.namelist():
                    if entry == "classes.jar" or re.fullmatch(r"libs/[^/]+\.jar", entry):
                        with zipfile.ZipFile(archive.open(entry)) as inner:
                            classes |= _jar_classes(inner)
                return classes
            return _jar_classes(archive)
    except (OSError, zipfile.BadZipFile):
        return None


def _jar_classes(archive: zipfile.ZipFile) -> set:
    return {entry[:-len(".class")].replace("/", ".") for entry in archive.namelist()
            if entry.endswith(".class") and not entry.startswith("META-INF/")
            and not entry.endswith("module-info.class")}


# Classification -----------------------------------------------------------------------------

def _in_flavor(configuration: str, flavor: str) -> bool:
    """A configuration belongs to a flavor unless it's another flavor's; lint and UTP belong to both."""
    return not any(configuration.startswith(other) for other in FLAVORS if other != flavor)


def _apk_reachability(finding: Finding, evidence: ApkEvidence) -> dict:
    classes: set = set()
    unread = []
    for file in sorted(finding.files):
        found = artifact_classes(Path(file))
        if found is None:
            unread.append(Path(file).name)
        else:
            classes |= found
    if not classes and unread:
        return {"kind": "artifact-unread", "detail": "the scanned file isn't readable here ("
                + ", ".join(unread) + "), so it's treated as shipped"}
    if not classes:
        return {"kind": "no-code", "detail": "the artifact carries no classes"}
    kept = {}
    for name in sorted(classes):
        renamed = evidence.kept(name)
        if renamed is not None:
            kept[name] = renamed
    examples = [f"{name} -> {renamed}" for name, renamed in list(kept.items())[:3]]
    record: dict = {"kind": "in-apk" if kept else "removed-by-r8", "keptClasses": len(kept),
                    "artifactClasses": len(classes), "apk": evidence.apk.name}
    if examples:
        record["examples"] = examples
    if not kept:
        record["detail"] = "R8 kept none of its classes; code it inlined elsewhere can't be ruled out"
    return record


def classify(findings: Sequence[Finding], rules: Sequence[SuppressionRule],
             dispositions: Sequence[Disposition], lockfile: Mapping[str, set],
             apk_evidence: Mapping[str, ApkEvidence], blocking_cvss: float) -> dict:
    flavors = {flavor: {"apkChecked": flavor in apk_evidence, "packagedRelease": [],
                        "notApplicable": [], "hostBuildTest": []} for flavor in FLAVORS}
    blocking = []
    for finding in findings:
        rule = suppression_for(rules, finding) if finding.suppressed else None
        disposition = None
        if rule is not None:
            disposition = {"kind": rule.tag, "reason": rule.notes, "recordedIn": "suppressions"}
        else:
            for candidate in dispositions:
                if candidate.applies(finding):
                    disposition = candidate.as_json()
                    break
        coordinate = finding.coordinate
        locked = sorted(lockfile.get(coordinate, ())) if coordinate else []
        base = {
            "id": finding.id,
            "score": finding.score,
            "scores": finding.scores,
            "severity": finding.severity,
            "source": finding.source,
            "artifact": finding.artifact,
            "files": sorted(Path(f).name for f in finding.files),
            "references": [{"module": r.module, "configuration": r.configuration}
                           for r in sorted(finding.references)],
            "suppressed": finding.suppressed,
            "blocking": not finding.suppressed and finding.score >= blocking_cvss,
        }
        if disposition:
            base["disposition"] = disposition
        unattributed = not finding.references and not locked
        reachability = {}
        for flavor in FLAVORS:
            configuration = release_configuration(flavor)
            shipped_by_scan = sorted({r.configuration for r in finding.references if is_shipped(r, flavor)})
            shipped_by_lockfile = [c for c in locked if c in shipped_configurations(flavor)]
            in_flavor = {str(r) for r in finding.references if _in_flavor(r.configuration, flavor)}
            in_flavor |= {f"{SHIPPED_MODULE}:{c}" for c in locked if _in_flavor(c, flavor)}
            if shipped_by_scan or shipped_by_lockfile or unattributed:
                sources = []
                if shipped_by_scan:
                    sources.append("the scan resolved it in "
                                   + ", ".join(f"{SHIPPED_MODULE}:{c}" for c in shipped_by_scan))
                if shipped_by_lockfile:
                    sources.append(f"{SHIPPED_MODULE}/gradle.lockfile lists {coordinate} under "
                                   + ", ".join(shipped_by_lockfile))
                if unattributed:
                    sources.append("the scan names no configuration for it, so it's treated as shipped")
                evidence: dict
                if flavor in apk_evidence:
                    evidence = _apk_reachability(finding, apk_evidence[flavor])
                else:
                    evidence = {"kind": "release-runtime-classpath", "detail": "no release APK given, "
                                "so it's treated as shipped"}
                evidence["classpath"] = sources
                section = "notApplicable" if disposition and disposition["kind"] in NOT_APPLICABLE else "packagedRelease"
            elif in_flavor:
                evidence = {"kind": "host-only", "detail": f"not in {SHIPPED_MODULE}:{configuration}",
                            "configurations": sorted(in_flavor)}
                section = "hostBuildTest"
            else:
                continue
            reachability[flavor] = evidence
            flavors[flavor][section].append({**base, "reachability": evidence})
        if base["blocking"]:
            blocking.append({**base, "reachability": reachability})
    return {"schemaVersion": 1, "blockingCvss": blocking_cvss, "flavors": flavors, "blocking": blocking}


def summarize(classification: Mapping) -> dict:
    return {
        "flavors": {flavor: {"apkChecked": sections["apkChecked"],
                             **{name: len(sections[name]) for name in ("packagedRelease", "notApplicable", "hostBuildTest")}}
                    for flavor, sections in classification["flavors"].items()},
        "blocking": [f"{entry['id']} {entry['artifact']}" for entry in classification["blocking"]],
    }


def _where(reach: Mapping, every_configuration: bool = False) -> str:
    if reach["kind"] == "in-apk":
        return f"APK keeps {reach['keptClasses']} of {reach['artifactClasses']} classes"
    if reach["kind"] == "host-only":
        configurations = reach["configurations"]
        if every_configuration or len(configurations) <= 3:
            return ", ".join(configurations)
        return ", ".join(configurations[:3]) + f" and {len(configurations) - 3} more"
    return reach.get("detail", reach["kind"])


def _line(entry: Mapping) -> str:
    line = f"  {entry['id']:<22} {entry['score']:>4.1f}  {entry['artifact']}  {_where(entry['reachability'])}"
    if entry.get("disposition"):
        line += f"  [{entry['disposition']['kind']}]"
    if entry["suppressed"]:
        line += "  suppressed"
    if entry["blocking"]:
        line += "  BLOCKING"
    return line


def render_text(classification: Mapping) -> str:
    titles = {
        "packagedRelease": "packaged in the release APK",
        "notApplicable": "in the release APK, but the CVE doesn't apply to this artifact",
        "hostBuildTest": "build host, lint and tests only",
    }
    lines = [f"Dependency CVE reachability, blocking at CVSS {classification['blockingCvss']}", ""]
    for flavor, sections in classification["flavors"].items():
        checked = "R8 mapping and DEX checked" if sections["apkChecked"] else "no APK checked"
        for name, title in titles.items():
            entries = sections[name]
            lines.append(f"{flavor.upper()}, {title} ({len(entries)}, {checked})")
            lines.extend(_line(entry) for entry in entries)
            lines.append("")
    lines.append(f"Blocking ({len(classification['blocking'])})")
    for entry in classification["blocking"]:
        lines.append(f"  {entry['id']} {entry['score']:.1f} {entry['artifact']} ({', '.join(entry['files'])})")
        # Per flavor, where it ships from and what the APK shows, ahead of the host configurations
        for flavor, reach in entry["reachability"].items():
            detail = [_where(reach, every_configuration=True)] + list(reach.get("classpath") or ()) \
                + list(reach.get("examples") or ())[:1]
            lines.append(f"    {flavor}: " + "; ".join(detail))
        if not entry["reachability"]:
            refs = ", ".join(f"{r['module']}:{r['configuration']}" for r in entry["references"]) or "no configuration"
            lines.append(f"    no flavor resolves it, references: {refs}")
    problems = classification.get("validationProblems") or []
    if problems:
        lines += ["", f"Suppression and disposition problems ({len(problems)})"]
        lines.extend(f"  {problem}" for problem in problems)
    return "\n".join(lines) + "\n"
