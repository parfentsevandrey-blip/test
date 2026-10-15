package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1660 to 1670: German compounds, the rules of the syllogism, a tally of burials by cause and a shorthand sign table. */
val db38Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.PROJECT,
        section = "Language",
        title = "Splitting German compounds into their roots",
        originalTitle = "Ausfuehrliche Arbeit von der Teutschen HaubtSprache",
        author = "Justus Georg Schottelius",
        year = 1663,
        summary = "A program breaks a German compound word into known roots by taking the longest root at the start of the word, then repeating on what is left.",
        details = "Schottelius's grammar of 1663 gave much attention to how German words join roots. The method here is a simple longest-match rule against a short root list, which is invented for the example, and the words Haustuer, Wasserbau and Buchstab are split against it.",
        instructions = """
            1. Save the example as german_compounds.py.
            2. Run it: python3 german_compounds.py
        """,
        example = """
            ROOTS = ["haus", "tuer", "wasser", "bau", "sprach", "kunst", "buch", "stab"]

            def split_compound(word):
                # Take the longest known root at the start of the word, then repeat on what is left.
                parts = []
                rest = word.lower()
                while rest:
                    matches = [root for root in ROOTS if rest.startswith(root)]
                    if not matches:
                        return None
                    root = max(matches, key=len)
                    parts.append(root)
                    rest = rest[len(root):]
                return parts

            for word in ("Haustuer", "Wasserbau", "Sprachkunst", "Buchstab", "Xylo"):
                parts = split_compound(word)
                print(word, "->", " + ".join(parts) if parts else "no split")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.PROJECT,
        section = "Logic",
        title = "A checker for the rules of the syllogism",
        originalTitle = "La Logique, ou l'art de penser",
        author = "Antoine Arnauld and Pierre Nicole",
        year = 1662,
        summary = "A program checks a three-line syllogism against the traditional rules about the middle term, distribution and negative premises, and reports whether it is valid.",
        details = "The rules are the traditional ones that logic books repeated, and the Port-Royal Logic of 1662 is among the books that set them out. The example tests four forms, one of them invalid, and prints a verdict for each.",
        instructions = """
            1. Save the example as syllogism.py.
            2. Run it: python3 syllogism.py
        """,
        example = """
            # How each proposition distributes its subject and predicate: A (all S are P), E (no S is P), I (some S is P), O (some S is not P).
            DISTRIBUTES = {"A": (True, False), "E": (True, True), "I": (False, False), "O": (False, True)}

            def valid(major, minor, conclusion):
                # Each proposition is (form, subject, predicate). S is the minor term, P the major term and M the middle term.
                fm, fn, fc = major[0], minor[0], conclusion[0]
                d_major = dict(zip(major[1:], DISTRIBUTES[fm]))
                d_minor = dict(zip(minor[1:], DISTRIBUTES[fn]))
                d_conc = dict(zip(conclusion[1:], DISTRIBUTES[fc]))
                # The middle term must be distributed in at least one premise.
                if not (d_major.get("M") or d_minor.get("M")):
                    return False
                # A term distributed in the conclusion must be distributed in the premise where it appears.
                if d_conc.get("S") and not d_minor["S"]:
                    return False
                if d_conc.get("P") and not d_major["P"]:
                    return False
                # Two negative premises prove nothing, and a negative premise needs a negative conclusion, and the reverse.
                negatives = sum(1 for f in (fm, fn) if f in "EO")
                if negatives == 2 or (negatives == 1) != (fc in "EO"):
                    return False
                # Two particular premises prove nothing, and a particular premise needs a particular conclusion.
                particulars = sum(1 for f in (fm, fn) if f in "IO")
                if particulars == 2 or (particulars == 1 and fc not in "IO"):
                    return False
                return True

            TESTS = [
                ("Barbara", ("A", "M", "P"), ("A", "S", "M"), ("A", "S", "P")),
                ("Celarent", ("E", "M", "P"), ("A", "S", "M"), ("E", "S", "P")),
                ("Darii", ("A", "M", "P"), ("I", "S", "M"), ("I", "S", "P")),
                ("AAA in the second figure", ("A", "P", "M"), ("A", "S", "M"), ("A", "S", "P")),
            ]

            for name, major, minor, conclusion in TESTS:
                verdict = "valid" if valid(major, minor, conclusion) else "invalid"
                print(f"{name}: {verdict}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.PROJECT,
        section = "Statistics",
        title = "Burials by cause from weekly bills",
        originalTitle = "Natural and Political Observations upon the Bills of Mortality",
        author = "John Graunt",
        year = 1662,
        summary = "A program adds up weekly burials under each cause of death, as Graunt did with the London bills, and prints each cause's share of the total.",
        details = "Graunt's book of 1662 analysed the London Bills of Mortality, sorting burials by cause and by age. The counts in the example are invented, and the program totals each cause over three weeks and prints its share as a percentage.",
        instructions = """
            1. Save the example as burial_tally.py.
            2. Run it: python3 burial_tally.py
        """,
        example = """
            # Invented weekly bills of mortality: each week lists the burials counted under each cause.
            BILLS = [
                {"plague": 3, "consumption": 5, "aged": 2},
                {"plague": 0, "consumption": 4, "aged": 3},
                {"plague": 6, "consumption": 2, "aged": 1},
            ]

            totals = {}
            for week in BILLS:
                for cause, count in week.items():
                    totals[cause] = totals.get(cause, 0) + count
            grand = sum(totals.values())
            for cause in sorted(totals):
                share = 100 * totals[cause] / grand
                print(f"{cause}: {totals[cause]} burials, {share:.1f} per cent")
            print("all causes:", grand)
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.PROJECT,
        section = "Shorthand",
        title = "A shorthand abbreviator with a sign table",
        originalTitle = "Tachygraphy",
        author = "Thomas Shelton",
        year = 1660,
        summary = "A program replaces common words with single signs from a sign table and counts the letters saved, the economy that shorthand systems of the period pursued.",
        details = "Shelton's system, which Pepys used for his diary, was a shorthand of the same kind, and this program follows the idea with a made-up table of signs that is not Shelton's. The example prints the abbreviated sentence and the number of letters saved.",
        instructions = """
            1. Save the example as shorthand.py.
            2. Run it: python3 shorthand.py
        """,
        example = """
            # A sign table for common words: each word that has a sign is written as that one mark. The table is invented.
            SIGNS = {"the": "+", "and": "&", "king": "%", "reads": "R", "letter": "L"}

            def abbreviate(text):
                out, saved = [], 0
                for word in text.split():
                    if word in SIGNS:
                        out.append(SIGNS[word])
                        saved += len(word) - len(SIGNS[word])
                    else:
                        out.append(word)
                return " ".join(out), saved

            sentence = "the king reads a letter and the king sleeps"
            short, saved = abbreviate(sentence)
            print("written:", short)
            print("letters saved:", saved)
        """,
    ),

)
