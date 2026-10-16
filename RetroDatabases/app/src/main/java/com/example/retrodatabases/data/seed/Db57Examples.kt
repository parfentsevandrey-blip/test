package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Runnable examples of 1470 to 1480: distinct letters of a title, counting by key, brackets, medians, a quire lookup and an ink mix by weight. */
val db57Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Hyphenation",
        title = "Breaking a long word into hyphenated parts",
        originalTitle = "The Recuyell of the Historyes of Troye",
        author = "William Caxton",
        year = 1474,
        summary = "Hyphenation breaks a long word at syllable points, and a line width shows whether the broken word fits on one line.",
        details = "Caxton's printed English of 1474 sets long words across line ends, and the example breaks three of his words at invented syllable points. The program prints each broken word with its length and whether it fits a line of eight characters.",
        instructions = """
            1. Save the example as hyphenate.py.
            2. Run it: python3 hyphenate.py
        """,
        example = """
            SPLITS = {
                "Historyes": ["His", "to", "ryes"],
                "Recuyell": ["Re", "cuy", "ell"],
                "Troye": ["Troy", "e"],
            }
            LINE = 8   # characters available on the line

            for word, parts in SPLITS.items():
                broken = "-".join(parts)
                fit = "fits" if len(broken) <= LINE else "too long"
                print(f"{broken} ({len(broken)} characters): {fit} on a line of {LINE}")
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Fables",
        title = "Counting animals in an illustrative list of fables",
        originalTitle = "Aesopi Fabulae",
        author = "Aesop",
        year = 1480,
        summary = "A dictionary counts how often each animal appears in a list, and the counts are then sorted from the largest to the smallest.",
        details = "The example is linked to the Greek Aesop of 1480, but the list is invented for illustration and is not the count of any edition. Ties are sorted by name, so the output is always the same.",
        instructions = """
            1. Save the example as fables.py.
            2. Run it: python3 fables.py
        """,
        example = """
            from collections import Counter

            animals = ["fox", "crow", "fox", "wolf", "lamb", "fox", "wolf"]
            counts = Counter(animals)
            for animal, count in sorted(counts.items(), key=lambda item: (-item[1], item[0])):
                print(animal, count)
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Latin usage",
        title = "Checking brackets in a Latin sentence",
        originalTitle = "Elegantiae linguae Latinae",
        author = "Lorenzo Valla",
        year = 1471,
        summary = "A stack checks that every opening bracket in a sentence is closed by the matching bracket in the right order.",
        details = "Valla's book on Latin usage has nothing on brackets, and the four sentences are invented to test the program. The example reports the first place where the brackets fail to match, or the bracket that is left open at the end.",
        instructions = """
            1. Save the example as brackets.py.
            2. Run it: python3 brackets.py
        """,
        example = """
            OPENERS = "([{"
            CLOSERS = ")]}"
            MATCH = {")": "(", "]": "[", "}": "{"}

            def check(sentence):
                stack = []
                for position, ch in enumerate(sentence, start=1):
                    if ch in OPENERS:
                        stack.append((ch, position))
                    elif ch in CLOSERS:
                        if not stack or stack[-1][0] != MATCH[ch]:
                            return f"unbalanced at position {position}"
                        stack.pop()
                if stack:
                    opener, where = stack[-1]
                    return f"unbalanced: {opener} at position {where} is never closed"
                return "balanced"

            for sentence in ["amor (dei) et [pax]", "pax (pacis] est", "rex {regum}", "ars (bona"]:
                print(sentence, "->", check(sentence))
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Print runs",
        title = "The median of illustrative print runs",
        originalTitle = "Chronica Hungarorum",
        author = "Andreas Hess",
        year = 1473,
        summary = "The median of a list is its middle value after sorting, or the mean of the two middle values when the count is even.",
        details = "The anchor is Hess's Buda press, but the four print runs are invented for illustration and are not the run of any book. The example sorts the runs and averages the two middle ones.",
        instructions = """
            1. Save the example as median_runs.py.
            2. Run it: python3 median_runs.py
        """,
        example = """
            runs = [300, 250, 500, 400]
            ordered = sorted(runs)
            middle = len(ordered) // 2
            median = (ordered[middle - 1] + ordered[middle]) / 2
            print("runs:", len(runs))
            print("median run:", median)
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Quires",
        title = "Finding which quire holds a given leaf",
        originalTitle = "Sixtus Riessinger",
        author = "Sixtus Riessinger",
        year = 1471,
        summary = "A leaf number is matched against the leaves of each quire in turn, and the first quire whose range includes the leaf is the one that holds it.",
        details = "The anchor is Riessinger's Naples press, but the quire sizes are an invented set for illustration. A quire is a gathering of folded sheets, and the example reports the quire for seven chosen leaves.",
        instructions = """
            1. Save the example as quire_lookup.py.
            2. Run it: python3 quire_lookup.py
        """,
        example = """
            quires = [8, 8, 6, 4]
            for leaf in [1, 8, 9, 16, 17, 22, 26]:
                start = 1
                for number, size in enumerate(quires, start=1):
                    if start <= leaf < start + size:
                        print(f"leaf {leaf}: quire {number}")
                        break
                    start += size
        """,
    ),

    entry(
        databaseId = 57,
        kind = Kind.EXAMPLE,
        section = "Printing materials",
        title = "Mixing printer's ink by weight",
        originalTitle = "Eusebius, Praeparatio evangelica",
        author = "Nicolas Jenson",
        year = 1470,
        summary = "A mixing ratio splits a batch by weight into parts, and integer division gives each part in whole grams.",
        details = "The anchor is Jenson's Venetian work of 1470, but the ratio of one part pigment to four parts varnish is illustrative and is not a historical recipe. The example shares a batch of 480 grams by that ratio.",
        instructions = """
            1. Save the example as ink_mix.py.
            2. Run it: python3 ink_mix.py
        """,
        example = """
            batch = 480
            parts = {"lampblack": 1, "varnish": 4}
            total_parts = sum(parts.values())
            for name, share in parts.items():
                print(f"{name}: {batch * share // total_parts} g")
        """,
    ),

)
