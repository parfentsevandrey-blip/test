package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1620 to 1630: a slide rule's log sums, a boat's air, a register by subject, a word list, a sector's scaling and a circular rule. */
val db42Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Calculating",
        title = "Oughtred's slide rule multiplies by sliding two scales",
        originalTitle = "Circles of Proportion and the Horizontal Instrument",
        author = "William Oughtred",
        year = 1622,
        summary = "Sliding two logarithmic scales sets their lengths end to end, so a product is read as a sum of two logs, and the example prints the sums for two products.",
        details = "The example adds the logarithms of two numbers, the way the rule adds scale lengths, and reads the product back from the sum. The 1622 date is the one given for Oughtred's rectilinear rule, and its printing in 1633 is noted in the device record.",
        instructions = """
            1. Save the example as oughtred_product.py.
            2. Run it: python3 oughtred_product.py
        """,
        example = """
            import math

            def slide_product(a, b):
                # The rule adds the lengths of two log scales; the sum is read back as a product.
                total = math.log10(a) + math.log10(b)
                return total, 10 ** total

            for a, b in ((2, 3), (4, 25)):
                total, product = slide_product(a, b)
                print(f"{a} x {b}: sum of logs {total:.4f}, product {product:.1f}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Boats",
        title = "Air for the crew of a submerged boat, with illustrative figures",
        originalTitle = "A rowing boat under water, built for King James I",
        author = "Cornelis Drebbel",
        year = 1620,
        summary = "A worked example of how long a sealed boat's air lasts for its crew, using labelled illustrative figures rather than the disputed trial figures.",
        details = "The sources disagree on the air supply in Drebbel's boat, so the example uses invented but labelled figures for the crew size and the air each rower uses. It shows the division that gives the time the air lasts and claims none of Drebbel's own numbers.",
        instructions = """
            1. Save the example as boat_air.py.
            2. Run it: python3 boat_air.py
        """,
        example = """
            CREW = 12                  # illustrative crew size
            AIR_PER_PERSON = 2         # illustrative air used by each rower each minute
            AIR_IN_BOAT = 240          # illustrative air in the sealed boat

            use_per_minute = CREW * AIR_PER_PERSON
            minutes = AIR_IN_BOAT / use_per_minute
            print("air used per minute:", use_per_minute)
            print("minutes the air lasts:", minutes)
            print(f"hours the air lasts: {minutes / 60:.2f}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Information",
        title = "A Bureau register grouped by subject",
        originalTitle = "Bureau d'adresse et de rencontre",
        author = "Theophraste Renaudot",
        year = 1630,
        summary = "A register of requests kept by subject, so that every offer of one kind sits in one group, is sorted and printed by a short program.",
        details = "The example takes a small list of requests in the manner of the Bureau's register, groups them by subject and prints each group in order. The requests are invented for the example and are not entries from the Bureau itself.",
        instructions = """
            1. Save the example as bureau_register.py.
            2. Run it: python3 bureau_register.py
        """,
        example = """
            REQUESTS = [
                ("wanted: a cook", "service"),
                ("for sale: a clock", "goods"),
                ("wanted: a tutor", "service"),
                ("for sale: a horse", "goods"),
                ("wanted: a copyist", "service"),
            ]

            groups = {}
            for text, subject in REQUESTS:
                groups.setdefault(subject, []).append(text)
            print("requests:", len(REQUESTS))
            for subject in sorted(groups):
                print(subject + ":", "; ".join(groups[subject]))
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Dictionaries",
        title = "A hard-word list sorted alphabetically",
        originalTitle = "The English Dictionarie: or, an Interpreter of hard English words",
        author = "Henry Cockeram",
        year = 1623,
        summary = "An alphabetical word list of the form Cockeram used is sorted by a short program, which keeps each word with its explanation.",
        details = "Cockeram's dictionary of 1623 is arranged alphabetically with short explanations for each word. The example sorts a small list of hard words with their glosses in the same way, using words made up for the example.",
        instructions = """
            1. Save the example as hard_words.py.
            2. Run it: python3 hard_words.py
        """,
        example = """
            WORDS = [
                ("zodiac", "a sign of the heavens"),
                ("abacus", "a counting board"),
                ("lexicon", "a word book"),
                ("cipher", "a secret writing"),
            ]

            print("words:", len(WORDS))
            for word, gloss in sorted(WORDS):
                print(f"{word}: {gloss}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "A proportional sector scales a length by a ratio",
        originalTitle = "The Description and Use of the Sector",
        author = "Edmund Gunter",
        year = 1623,
        summary = "A proportional sector scales a length in a chosen ratio, and the example computes the scaled lengths with exact fractions.",
        details = "Gunter's sector of 1623 scales lengths by similar triangles, and its marked lines give the ratio. The example computes the scaled lengths for three ratios with exact fractions, which is the arithmetic the sector does by eye.",
        instructions = """
            1. Save the example as sector_scale.py.
            2. Run it: python3 sector_scale.py
        """,
        example = """
            from fractions import Fraction as F

            def scaled(length, ratio):
                # Similar triangles scale every length by the same ratio.
                return F(length) * ratio

            for num, den in ((2, 3), (3, 4), (5, 2)):
                print(f"length 12 at ratio {num}:{den} gives {scaled(12, F(num, den))}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.EXAMPLE,
        section = "Calculating",
        title = "Delamain's circular rule multiplies by turning its scales",
        originalTitle = "Grammelogia",
        author = "Richard Delamain",
        year = 1630,
        summary = "A circular rule multiplies by turning one scale against another, so that a product is read as the sum of two logarithms, and the example prints the products.",
        details = "Delamain's circular rule was printed in 1630 or 1631, and the printing date is disputed. The example adds two logarithms, the way the circular scales add their lengths, and reads each product back from the sum.",
        instructions = """
            1. Save the example as circular_rule.py.
            2. Run it: python3 circular_rule.py
        """,
        example = """
            import math

            def circular_product(a, b):
                # The circular scales add two log lengths, and the total is read back as a product.
                turn = math.log10(a) + math.log10(b)
                return 10 ** turn

            for a, b in ((5, 8), (9, 9)):
                print(f"{a} x {b} = {circular_product(a, b):.1f}")
        """,
    ),

)
