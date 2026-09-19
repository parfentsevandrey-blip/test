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
LITERAL = r'((?:[^"\\]|\\\((?:[^()"]|"[^"]*"|\((?:[^()"]|"[^"]*")*\))*\)|\\.)*)'
PATTERNS = [
    re.compile(r'String\(localized:\s*"' + LITERAL + '"'),
    re.compile(r'\bText\(\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bLabel\(\s*"' + LITERAL + r'"\s*,'),
    re.compile(r'\bButton\(\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bButton\(\s*"' + LITERAL + r'"\s*,'),
    re.compile(r'\bToggle\(\s*"' + LITERAL + r'"\s*,'),
    re.compile(r'\bPicker\(\s*"' + LITERAL + r'"\s*,'),
    re.compile(r'\.help\(\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\.navigationTitle\(\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bGroupBox\(\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bSecuritySection\(title:\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bDetailRow\(label:\s*"' + LITERAL + r'"\s*,'),
    re.compile(r'\bAction\(title:\s*"' + LITERAL + r'"\s*\)'),
    re.compile(r'\bPaddingStat\(label:\s*"' + LITERAL + r'"\s*,'),
]

# Swift interpolation -> the specifier Xcode records in the catalogue.

# Interpolations whose value is an Int, and therefore render as %lld rather than %@. Listed by
# name because a regex cannot see Swift's types; a new Int interpolation must be added here.
INTEGER_EXPRESSIONS = {
    "classes", "count", "milliseconds", "open", "percent", "built", "launched", "attempt",
    "report.reachable", "report.total", "summary.samples", "app.videoFanCircuits",
    "stats.tor", "stats.direct", "stats.antiThrottle", "stats.blocked",
}


def is_integer(body):
    """An interpolation Swift formats with %lld: an Int(...) cast, a count, or a known integer."""
    return (body in INTEGER_EXPRESSIONS or body.startswith("Int(")
            or body.endswith(".count") or body.endswith(".milliseconds") or body.endswith(".requests")
            or body.endswith(".targetKilobytes"))


def interpolations(raw):
    """Yields (start, end, body) for every `\\(…)` in a Swift literal, whatever the nesting depth."""
    index = 0
    while True:
        start = raw.find("\\(", index)
        if start < 0:
            return
        depth = 0
        for position in range(start + 1, len(raw)):
            if raw[position] == "(":
                depth += 1
            elif raw[position] == ")":
                depth -= 1
                if depth == 0:
                    yield start, position + 1, raw[start + 2:position].strip()
                    index = position + 1
                    break
        else:
            return

# Names Veil never translates.
PROPER_NOUNS = {"Veil", "Tor", "Snowflake", "YouTube", "obfs4", "meek"}


def key_for(raw):
    """Turns a Swift literal into the catalogue key: interpolations become format specifiers."""
    out = []
    last = 0
    for start, end, body in interpolations(raw):
        out.append(raw[last:start])
        out.append("%lld" if is_integer(body) else "%@")
        last = end
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
