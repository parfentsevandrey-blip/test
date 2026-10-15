package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1580 to 1590: Stevin's decimal places, the rule of three, Stevin's slope, the night watch's bell strokes and the yield ratio of a harvest. */
val db46Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.GUIDE,
        section = "Decimals",
        title = "How Stevin writes a decimal fraction with place numbers",
        originalTitle = "De Thiende",
        author = "Simon Stevin",
        year = 1585,
        summary = "Stevin wrote each decimal digit with a number for its place, and a short program prints a fraction in that form with its exact value.",
        details = "Stevin's De Thiende of 1585 marked each digit with a circled number for its place, 0 for units, 1 for tenths, 2 for hundredths, and so on. The example writes the marks in brackets and checks each value with exact fractions.",
        instructions = """
            1. Save the example as stevin_notation.py.
            2. Run it: python3 stevin_notation.py
        """,
        example = """
            from fractions import Fraction

            def stevin(text):
                # Each digit is written with the place it holds: 0 for units, then 1 for tenths, 2 for hundredths and so on.
                whole, _, fraction = text.partition(".")
                marks = [f"{whole} (0)"]
                for place, digit in enumerate(fraction, start=1):
                    marks.append(f"{digit} ({place})")
                return " ".join(marks)

            for text in ("3.72", "0.5", "7.040"):
                print(stevin(text), "=", Fraction(text))
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.GUIDE,
        section = "Harvests",
        title = "How a harvest's yield ratio is found from seed and crop",
        originalTitle = "How a harvest's yield ratio is found from seed and crop",
        author = "Farm accountants of the period",
        year = 1586,
        summary = "A yield ratio compares the crop with the seed sown, and a short program divides the crop by the seed with exact fractions for three fields.",
        details = "Accounts of the period often gave a harvest as a multiple of the seed sown, which is the ratio the program computes. The figures are illustrative, not taken from any estate record.",
        instructions = """
            1. Save the example as yield_ratio.py.
            2. Run it: python3 yield_ratio.py
        """,
        example = """
            from fractions import Fraction as F

            def yield_ratio(seed, crop):
                # The yield ratio is the crop divided by the seed sown, kept as an exact fraction.
                return F(crop, seed)

            for seed, crop in ((4, 28), (6, 15), (5, 12)):
                print(f"sown {seed} measures, harvested {crop}: yield {yield_ratio(seed, crop)} to one")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.GUIDE,
        section = "Trade",
        title = "How the rule of three finds an unknown price",
        originalTitle = "Epitome arithmeticae practicae",
        author = "Christopher Clavius",
        year = 1583,
        summary = "The rule of three finds the price of an amount from a known amount and its price, by multiplying and dividing in proportion.",
        details = "Clavius's Epitome of 1583 taught the rule of three as a basic method of trade arithmetic. The example applies it with exact fractions to three illustrative pairs of amounts and prices, not to figures from the book.",
        instructions = """
            1. Save the example as rule_of_three.py.
            2. Run it: python3 rule_of_three.py
        """,
        example = """
            from fractions import Fraction as F

            def rule_of_three(amount, price, wanted):
                # The amount is to its price as the wanted amount is to the unknown, so the unknown is price times wanted over amount.
                return F(price * wanted, amount)

            for amount, price, wanted in ((12, 9, 20), (5, 3, 8), (7, 10, 4)):
                print(f"{wanted} units cost {rule_of_three(amount, price, wanted)} when {amount} units cost {price}")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "How Stevin's inclined plane balances a weight",
        originalTitle = "De Beghinselen der Weeghconst",
        author = "Simon Stevin",
        year = 1586,
        summary = "On a smooth slope of height h and length L, a weight W is balanced by a force of W times h over L, and a short program prints that force for three slopes.",
        details = "Stevin's statics of 1586 gave this rule for a weight on a smooth slope, and his chain of beads over a triangle is the argument he used to prove it. The example applies the rule with exact fractions to three illustrative slopes.",
        instructions = """
            1. Save the example as slope_force.py.
            2. Run it: python3 slope_force.py
        """,
        example = """
            from fractions import Fraction as F

            def balancing_force(weight, height, length):
                # On a smooth slope the force along the slope is the weight times the height over the length of the slope.
                return F(weight * height, length)

            for weight, height, length in ((10, 3, 5), (15, 4, 5), (10, 3, 7)):
                print(f"weight {weight}, slope height {height} and length {length}: force {balancing_force(weight, height, length)}")
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.GUIDE,
        section = "Watches",
        title = "How the night watch counts hours by bell strokes",
        originalTitle = "How the night watch counts hours by bell strokes",
        author = "Town watchmen of the period",
        year = 1585,
        summary = "A bell that strikes the hour gives one stroke at one o'clock, two at two and so on, so the total strokes by an hour are a running sum, which a short program prints.",
        details = "A clock or bell that strikes the hour makes the total count of strokes a running sum of the hour counts. The example prints the running totals for four hours, with exact integer sums, and the figures illustrate the method.",
        instructions = """
            1. Save the example as bell_strokes.py.
            2. Run it: python3 bell_strokes.py
        """,
        example = """
            def strokes_by(hour):
                # The bell strikes once at one o'clock, twice at two, and so on, so the total is the sum of the counts up to that hour.
                return sum(range(1, hour + 1))

            for hour in (1, 2, 3, 12):
                print(f"total strokes by {hour} o'clock: {strokes_by(hour)}")
        """,
    ),

)
