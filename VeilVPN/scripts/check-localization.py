#!/usr/bin/env python3
"""Checks Localizable.xcstrings against the strings the source actually asks for.

The build has no signal for a missing translation, and every screen here carries copy that has to
read well in five languages. This runs in CI before the tests, and locally with no Xcode.
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CATALOG = os.path.join(ROOT, "Veil/Resources/Localizable.xcstrings")
LANGUAGES = ["ru", "uk", "fa", "zh-Hans"]

# String(localized: "..."), Text("..."), Label("...", ...), Button("..."), Toggle("...", ...),
# Picker("...", ...), .help("..."), .navigationTitle("...")
PATTERNS = [
    re.compile(r'String\(localized:\s*"((?:[^"\\]|\\.)*)"'),
    re.compile(r'\bText\(\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\bLabel\(\s*"((?:[^"\\]|\\.)*)"\s*,'),
    re.compile(r'\bButton\(\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\bButton\(\s*"((?:[^"\\]|\\.)*)"\s*,'),
    re.compile(r'\bToggle\(\s*"((?:[^"\\]|\\.)*)"\s*,'),
    re.compile(r'\bPicker\(\s*"((?:[^"\\]|\\.)*)"\s*,'),
    re.compile(r'\.help\(\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\.navigationTitle\(\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\bGroupBox\(\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\bSecuritySection\(title:\s*"((?:[^"\\]|\\.)*)"\s*\)'),
    re.compile(r'\bDetailRow\(label:\s*"((?:[^"\\]|\\.)*)"\s*,'),
    re.compile(r'\bAction\(title:\s*"((?:[^"\\]|\\.)*)"\s*\)'),
]

# Swift interpolation -> the specifier Xcode records in the catalogue.
INTERPOLATION = re.compile(r'\\\((?:[^()]|\((?:[^()]|\([^()]*\))*\))*\)')

# Interpolations whose value is an Int, and therefore render as %lld rather than %@. Listed by
# name because a regex cannot see Swift's types; a new Int interpolation must be added here.
INTEGER_EXPRESSIONS = {
    "classes", "count", "milliseconds", "open", "percent", "built", "launched",
    "report.reachable", "report.total",
    "stats.tor", "stats.direct", "stats.antiThrottle", "stats.blocked",
    "Int((latency * 1000).rounded())",
}

# Names Veil never translates.
PROPER_NOUNS = {"Veil", "Tor", "Snowflake", "YouTube", "obfs4", "meek"}


def key_for(raw):
    """Turns a Swift literal into the catalogue key: interpolations become format specifiers."""
    out = []
    last = 0
    for match in INTERPOLATION.finditer(raw):
        out.append(raw[last:match.start()])
        body = match.group(0)[2:-1].strip()
        out.append("%lld" if body in INTEGER_EXPRESSIONS else "%@")
        last = match.end()
    out.append(raw[last:])
    return "".join(out).replace('\\"', '"').replace("\\n", "\n")


def specifiers(text):
    return re.findall(r'%(?:lld|@|d|\d+\$(?:lld|@))', text)


def main():
    with open(CATALOG, encoding="utf-8") as handle:
        catalog = json.load(handle)
    strings = catalog.get("strings", {})

    wanted = set()
    for base, _, files in os.walk(os.path.join(ROOT, "Veil")):
        for name in files:
            if not name.endswith(".swift"):
                continue
            with open(os.path.join(base, name), encoding="utf-8") as handle:
                source = handle.read()
            for pattern in PATTERNS:
                for match in pattern.finditer(source):
                    key = key_for(match.group(1))
                    if key.strip() and key not in PROPER_NOUNS:
                        wanted.add(key)

    missing = sorted(key for key in wanted if key not in strings)
    untranslated = []
    mismatched = []
    for key, entry in strings.items():
        localizations = entry.get("localizations", {})
        if key in PROPER_NOUNS:
            continue
        for language in LANGUAGES:
            unit = localizations.get(language, {}).get("stringUnit", {})
            value = unit.get("value")
            if not value or unit.get("state") != "translated":
                untranslated.append(f"{language}: {key}")
                continue
            if sorted(specifiers(value)) != sorted(specifiers(key)):
                mismatched.append(f"{language}: {key!r} -> {value!r}")

    ok = True
    if missing:
        ok = False
        print(f"{len(missing)} string(s) used in code but absent from the catalogue:")
        for key in missing:
            print(f"  {key!r}")
    if untranslated:
        ok = False
        print(f"{len(untranslated)} missing translation(s):")
        for line in untranslated[:60]:
            print(f"  {line}")
        if len(untranslated) > 60:
            print(f"  … and {len(untranslated) - 60} more")
    if mismatched:
        ok = False
        print(f"{len(mismatched)} format-specifier mismatch(es) — these crash at runtime:")
        for line in mismatched:
            print(f"  {line}")
    if ok:
        print(f"Localization OK: {len(strings)} keys, {len(LANGUAGES)} languages.")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
