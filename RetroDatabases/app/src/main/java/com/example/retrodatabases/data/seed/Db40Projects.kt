package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1640 to 1650: a mountain height calculator, a daily register, a verse counter and a table-driven tune composer. */
val db40Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.PROJECT,
        section = "Pressure",
        title = "A barometer altitude calculator for a mountain climb",
        originalTitle = "Puy de Dome barometer readings",
        author = "Florin Perier",
        year = 1648,
        summary = "A short program turns the ratio of two barometer readings into a height, using a rounded scale height of 8 km for the air.",
        details = "Perier's climb showed that the air's pressure falls with height, and a later summary of that fall uses an exponential formula with a scale height. The example uses a rounded scale height of 8 kilometres, so its heights are approximate, and it prints the height for two pressure ratios.",
        instructions = """
            1. Save the example as puy_altitude.py.
            2. Run it: python3 puy_altitude.py
        """,
        example = """
            from math import log

            SCALE_HEIGHT = 8000.0  # metres, a rounded value for the air near the ground

            def height_from_ratio(ratio):
                # Pressure falls exponentially with height: p = p0 * exp(-h / H), so h = H * ln(p0 / p).
                return SCALE_HEIGHT * log(1 / ratio)

            for ratio in (0.5, 0.75):
                print(f"pressure ratio {ratio:.2f}: height {height_from_ratio(ratio):.0f} m")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.PROJECT,
        section = "Trade records",
        title = "A daily register that keeps a running balance",
        originalTitle = "Daily register with running totals",
        author = "Dutch East India Company",
        year = 1641,
        summary = "A short program keeps a dated register of goods in and out and prints a running balance after each day, in the manner of a trading post's daily book.",
        details = "The Dutch post at Dejima kept its daily register in this spirit, noting each day's business in order. The example uses invented entries for three days and prints the balance after each one, so the output can be checked by hand.",
        instructions = """
            1. Save the example as daily_register.py.
            2. Run it: python3 daily_register.py
        """,
        example = """
            entries = (("1641-10-01", 4, 1), ("1641-10-02", 0, 2), ("1641-10-03", 5, 3))
            balance = 0
            for date, received, sent in entries:
                balance += received - sent
                print(f"{date}: in {received}, out {sent}, balance {balance}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.PROJECT,
        section = "Verse",
        title = "A verse counter that multiplies word lists by rule",
        originalTitle = "Word lists combined by rule",
        author = "Georg Philipp Harsdoerffer",
        year = 1647,
        summary = "A short program counts the lines a verse template can produce from three word lists and prints the first few, which shows how a rule multiplies a small vocabulary.",
        details = "Harsdoerffer's funnel taught a learner to fill set patterns with words from lists, so that the number of possible lines grows as the product of the list lengths. The example uses invented word lists, with two adjectives, three nouns and two verbs, and prints the count before the first three lines.",
        instructions = """
            1. Save the example as verse_count.py.
            2. Run it: python3 verse_count.py
        """,
        example = """
            adjectives = ("gentle", "bold")
            nouns = ("river", "star", "field")
            verbs = ("sings", "falls")
            lines = [f"the {a} {n} {v}" for a in adjectives for n in nouns for v in verbs]
            print(f"lines the template gives: {len(lines)}")
            for line in lines[:3]:
                print(line)
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.PROJECT,
        section = "Music",
        title = "A table-driven tune composer with fixed choices",
        originalTitle = "Table-driven tune composer",
        author = "Athanasius Kircher",
        year = 1650,
        summary = "A short program builds a tune by reading set phrases from tables with a fixed index pattern, the principle behind combinatorial music machines.",
        details = "Kircher's cabinet combined set phrases by a rule chosen in advance, and the example does the same with three small phrases and a fixed index pattern. Its choices are not random, so the output is the same on every run and can be checked by hand.",
        instructions = """
            1. Save the example as tune_composer.py.
            2. Run it: python3 tune_composer.py
        """,
        example = """
            phrases = [("C", "E", "G"), ("D", "F", "A"), ("E", "G", "B")]
            pattern = [0, 2, 1, 0]
            tune = []
            for i, row in enumerate(pattern):
                phrase = phrases[row]
                tune.append(phrase[i % 3])
            print(" ".join(tune))
            print(f"notes in the tune: {len(tune)}")
        """,
    ),

)
