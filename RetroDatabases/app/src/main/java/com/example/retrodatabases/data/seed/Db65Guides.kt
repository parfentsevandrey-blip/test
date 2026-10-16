package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1390 to 1400: a rhyme scheme, a mensural count, a recipe scaled for guests, the shares of a field and the turns of a clock drum. */
val db65Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How a rhyme royal stanza is checked for its rhyme scheme",
        originalTitle = "Rhyme royal",
        author = "English poets of the late fourteenth century",
        year = 1390,
        summary = "A rhyme royal stanza of seven lines has the scheme ababbcc, and a short program labels the rhyme sounds of a stanza and checks the pattern.",
        details = "Rhyme royal was in English use by the 1390s, and the example uses rhyme keys written out by hand, so it tests the scheme rather than any poem. Two stanzas are checked, one that fits the pattern and one that does not.",
        instructions = """
            1. Save the example as rhyme_royal.py.
            2. Run it: python3 rhyme_royal.py
        """,
        example = """
            RHYME_ROYAL = "ababbcc"

            def scheme(keys):
                letters = {}
                out = ""
                for key in keys:
                    if key not in letters:
                        letters[key] = "abcdefg"[len(letters)]
                    out += letters[key]
                return out

            tests = [
                ["ay", "ight", "ay", "ight", "ight", "ow", "ow"],
                ["ay", "ight", "ay", "ow", "ight", "ow", "ow"],
            ]
            for keys in tests:
                s = scheme(keys)
                print("scheme:", s)
                print("rhyme royal:", "yes" if s == RHYME_ROYAL else "no")
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.GUIDE,
        section = "Music",
        title = "How a perfect long divides into three breves in mensural time",
        originalTitle = "Mensural notation",
        author = "Composers of the ars subtilior",
        year = 1390,
        summary = "In mensural notation a perfect long holds three breves and an imperfect long two, and a short program counts the breves of a phrase.",
        details = "The rule is the one used for the notation of the late fourteenth century, and the phrase in the example is invented to test the count. The year 1390 is the date usually given for the Chantilly songbook, which uses this notation.",
        instructions = """
            1. Save the example as breves.py.
            2. Run it: python3 breves.py
        """,
        example = """
            BREVES = {"perfect": 3, "imperfect": 2}
            phrase = ["perfect", "imperfect", "perfect", "imperfect", "imperfect"]
            total = 0
            for i, kind in enumerate(phrase, start=1):
                n = BREVES[kind]
                total += n
                print(f"long {i} ({kind}): {n} breves")
            print("longs:", len(phrase))
            print("breves in the phrase:", total)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.GUIDE,
        section = "Cookery",
        title = "How a recipe from the Menagier is scaled from six guests to nine",
        originalTitle = "Scaling a recipe",
        author = "The Menagier de Paris",
        year = 1393,
        summary = "A household recipe for six guests is scaled to nine guests by a fixed factor, and a short program prints the new amounts with fractions kept exact.",
        details = "The ingredients and amounts are invented for the test, and the example keeps fractions exact until the last step. The year 1393 is the date of the Menagier de Paris, the household book that gives recipes of this kind.",
        instructions = """
            1. Save the example as recipe_scale.py.
            2. Run it: python3 recipe_scale.py
        """,
        example = """
            from fractions import Fraction

            recipe = [("eggs", 4, ""), ("flour", 250, "g"), ("butter", 60, "g"), ("wine", Fraction(1, 2), "l")]
            factor = Fraction(9, 6)
            for name, amount, unit in recipe:
                scaled = amount * factor
                shown = str(scaled.numerator) if scaled.denominator == 1 else str(float(scaled))
                print(f"{name} {shown} {unit}".rstrip())
            print("guests: 9")
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.GUIDE,
        section = "Land",
        title = "How the field shares of a gwajeon grant follow the grades of rank",
        originalTitle = "Shares by grade",
        author = "The Goryeo court",
        year = 1391,
        summary = "A field is divided among three grades of rank in proportion to set weights, and a short program prints each grade's share with the total checked.",
        details = "The weights and the field size are illustrative and are not taken from any record of 1391, so the example shows the method rather than the historical figures. The shares add up to the whole field, and the program prints that total as a check.",
        rare = true,
        instructions = """
            1. Save the example as gwajeon_shares.py.
            2. Run it: python3 gwajeon_shares.py
        """,
        example = """
            FIELD = 120
            WEIGHTS = {"grade A": 3, "grade B": 2, "grade C": 1}
            total_weight = sum(WEIGHTS.values())
            shares = {}
            for grade, weight in WEIGHTS.items():
                shares[grade] = FIELD * weight // total_weight
                print(grade, shares[grade])
            print("total", sum(shares.values()))
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.GUIDE,
        section = "Clocks",
        title = "How a weight-driven clock's running time follows from its drum turns",
        originalTitle = "Drum and weight",
        author = "Makers of weight-driven clocks",
        year = 1390,
        summary = "A weight that falls a set distance turns a drum a set number of times, and a short program turns those turns into hours and days.",
        details = "The drum size, the fall and the time per turn are illustrative and chosen so the result is exact. The method suits weight-driven clocks of the 1390s, such as the Wells clock of about 1390.",
        instructions = """
            1. Save the example as drum.py.
            2. Run it: python3 drum.py
        """,
        example = """
            CIRCUMFERENCE_CM = 40
            FALL_CM = 400
            HOURS_PER_TURN = 12
            turns = FALL_CM // CIRCUMFERENCE_CM
            hours = turns * HOURS_PER_TURN
            print("drum turns for the fall:", turns)
            print("hours for the fall:", hours)
            print("days for the fall:", hours // 24)
        """,
    ),

)
