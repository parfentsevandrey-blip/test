package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1540 to 1547: a split of 10 into parts with product 40, Glarean's modes, the cube of a sum, a moon wheel, a globe's parallels and a tally of books by language. */
val db50Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Imaginary parts",
        title = "Cardano's split of 10 into parts with product 40",
        originalTitle = "Ars magna",
        author = "Girolamo Cardano",
        year = 1545,
        summary = "Cardano's problem of splitting 10 into two parts with product 40 needs the square root of minus fifteen, and exact pairs show that the sum is 10 and the product 40.",
        details = "Ars magna of 1545 posed this problem and accepted an answer that involves the square root of a negative number, a step that Cardano himself treated with suspicion. The example keeps each part exact as a pair of whole numbers, so no rounding is needed.",
        instructions = """
            1. Save the example as cardano_split.py.
            2. Run it: python3 cardano_split.py
        """,
        example = """
            R = -15   # r stands for the square root of minus fifteen, so r times r is -15

            def mul(x, y):
                # A number is a pair (a, b) that stands for a + b*r.
                a, b = x
                c, d = y
                return (a * c + b * d * R, a * d + b * c)

            def add(x, y):
                return (x[0] + y[0], x[1] + y[1])

            part1 = (5, 1)
            part2 = (5, -1)
            print("parts: 5 + 1r and 5 - 1r, with r squared equal to -15")
            print("sum:", add(part1, part2)[0])
            print("product:", mul(part1, part2)[0])
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "Glarean's six authentic modes as step patterns",
        originalTitle = "Dodecachordon",
        author = "Heinrich Glarean",
        year = 1547,
        summary = "Glarean's Dodecachordon of 1547 argued for twelve modes, and a short program writes six of them out as whole and half steps, each spanning twelve semitones.",
        details = "Glarean's book was printed at Basel in 1547, and it added the Aeolian and Ionian modes to the eight of the church tradition. The example writes the steps of six authentic modes and totals the semitones, which come to twelve in each case.",
        instructions = """
            1. Save the example as glarean_modes.py.
            2. Run it: python3 glarean_modes.py
        """,
        example = """
            MODES = {
                "Ionian": "TTSTTTS",
                "Dorian": "TSTTTST",
                "Phrygian": "STTTSTT",
                "Lydian": "TTTSTTS",
                "Mixolydian": "TTSTTST",
                "Aeolian": "TSTTSTT",
            }
            STEPS = {"T": 2, "S": 1}   # a whole tone is 2 semitones, a semitone is 1

            for name, pattern in MODES.items():
                semitones = [STEPS[step] for step in pattern]
                print(f"{name}: {' '.join(str(s) for s in semitones)}, total {sum(semitones)}")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Algebra",
        title = "The cube of a sum, the identity behind Cardano's cubic",
        originalTitle = "Ars magna",
        author = "Girolamo Cardano",
        year = 1545,
        summary = "The cube of a sum equals the two cubes plus three times the product of the parts and their sum, and a short program checks the identity on three pairs of whole numbers.",
        details = "Cardano's method for the cubic rests on this identity, which Ars magna of 1545 uses to build its solution. The example checks it with exact integers, including one negative part, so the output has no rounding.",
        instructions = """
            1. Save the example as cube_identity.py.
            2. Run it: python3 cube_identity.py
        """,
        example = """
            for a, b in ((2, 3), (1, 4), (5, -2)):
                lhs = (a + b) ** 3
                rhs = a ** 3 + 3 * a * b * (a + b) + b ** 3
                print(f"a = {a}, b = {b}: (a+b)^3 = {lhs}, a^3 + 3ab(a+b) + b^3 = {rhs}, equal: {lhs == rhs}")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Moon",
        title = "The moon's age in days, as a moon wheel reads it",
        originalTitle = "Astronomicum Caesareum",
        author = "Peter Apian",
        year = 1540,
        summary = "A moon wheel reads the moon's age from the days since a new moon, and a short program finds the age with exact fractions for a cycle of 29 and a half days.",
        details = "Apian's book of 1540 printed paper wheels for reading the moon, and the moon's cycle of about 29.5 days sets how far the wheel turns. The example keeps the cycle exact, so the printed ages come out as whole or half days without rounding.",
        instructions = """
            1. Save the example as moon_age.py.
            2. Run it: python3 moon_age.py
        """,
        example = """
            from fractions import Fraction as F

            CYCLE = F(59, 2)   # the lunar cycle as 29 and a half days, kept exact

            def moon_age(days):
                # The age is the days left after the whole cycles are taken away.
                return days - (days // CYCLE) * CYCLE

            for days in (60, 100):
                print(f"{days} days after the new moon: age {moon_age(days)} days")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Globes",
        title = "The length of a parallel on a globe, by its cosine",
        originalTitle = "Globus terrestris",
        author = "Gerard Mercator",
        year = 1541,
        summary = "A parallel of latitude on a globe is shorter than the equator by the cosine of its latitude, and a short program prints that ratio for four latitudes.",
        details = "Mercator's terrestrial globe of 1541 showed the parallels of latitude, and the length of a parallel on a sphere is the cosine of its latitude times the length of the equator. The example prints the ratio for four latitudes, rounded to three places.",
        instructions = """
            1. Save the example as parallels.py.
            2. Run it: python3 parallels.py
        """,
        example = """
            import math

            for latitude in (0, 30, 60, 90):
                ratio = math.cos(math.radians(latitude))
                print(f"latitude {latitude} degrees: parallel {ratio:.3f} of the equator")
        """,
    ),

    entry(
        databaseId = 50,
        kind = Kind.EXAMPLE,
        section = "Bibliography",
        title = "Counting books by language in a sample bibliography",
        originalTitle = "Bibliotheca universalis",
        author = "Conrad Gesner",
        year = 1545,
        summary = "Counting the entries of a bibliography by language is a simple tally, and a short program tallies a sample list of twelve illustrative titles.",
        details = "Gesner's universal bibliography of 1545 listed books in Latin, Greek and Hebrew, so a reader could ask how many works came in each language. The example tallies an illustrative list that is not taken from the book, and it prints the counts in a fixed order.",
        instructions = """
            1. Save the example as language_tally.py.
            2. Run it: python3 language_tally.py
        """,
        example = """
            from collections import Counter

            works = [
                ("first work", "Latin"), ("second work", "Greek"), ("third work", "Latin"),
                ("fourth work", "Hebrew"), ("fifth work", "Latin"), ("sixth work", "Greek"),
                ("seventh work", "Latin"), ("eighth work", "Hebrew"), ("ninth work", "Greek"),
                ("tenth work", "Latin"), ("eleventh work", "Latin"), ("twelfth work", "Greek"),
            ]

            tally = Counter(language for _, language in works)
            print("works in the sample:", len(works))
            for language in ("Latin", "Greek", "Hebrew"):
                print(language, tally[language])
        """,
    ),

)
