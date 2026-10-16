package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1460 to 1470: a sine rule for a triangle, a recipe scaled for guests, a 3-4-5 right angle, a round seating count and a silver mark split into ounces. */
val db58Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.GUIDE,
        section = "Trigonometry",
        title = "How the sine rule finds a side of a plane triangle",
        originalTitle = "De triangulis omnimodis",
        author = "Johannes Regiomontanus",
        year = 1464,
        summary = "The sine rule says that each side of a plane triangle is proportional to the sine of its opposite angle, so one known side gives another.",
        details = "Regiomontanus set out this rule in his book on triangles, written about 1464. The example uses illustrative sine values written as exact fractions, not the sines of any triangle in that book, so the answers stay exact.",
        instructions = """
            1. Save the example as sine_rule.py.
            2. Run it: python3 sine_rule.py
        """,
        example = """
            from fractions import Fraction as F

            def side_from_sine_rule(known_side, sin_known_angle, sin_wanted_angle):
                # Sine rule: a / sin A = b / sin B, so the wanted side is b = a * sin B / sin A.
                return known_side * sin_wanted_angle / sin_known_angle

            cases = [
                ("side 10 opposite sine 1/2, wanted sine 3/5", 10, F(1, 2), F(3, 5)),
                ("side 12 opposite sine 3/4, wanted sine 1/3", 12, F(3, 4), F(1, 3)),
                ("side 8 opposite sine 2/5, wanted sine 4/5", 8, F(2, 5), F(4, 5)),
            ]
            for label, side, sin_known, sin_wanted in cases:
                print(f"{label}: wanted side {side_from_sine_rule(side, sin_known, sin_wanted)}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.GUIDE,
        section = "Cookery",
        title = "How a recipe scales for a larger table",
        originalTitle = "De honesta voluptate et valetudine",
        author = "Bartolomeo Platina",
        year = 1465,
        summary = "A recipe written for four guests scales to a larger table by multiplying each measure by the ratio of the new guest count to four.",
        details = "Platina's cookbook of about 1465 is the setting, but the recipe here is illustrative and not taken from the book. The example uses exact fractions, so each scaled measure prints as a clean fraction.",
        instructions = """
            1. Save the example as recipe_scale.py.
            2. Run it: python3 recipe_scale.py
        """,
        example = """
            from fractions import Fraction as F

            BASE_GUESTS = 4
            recipe = [("flour", F(3, 4)), ("honey", F(1, 3)), ("almonds", F(2))]

            def scale(amount, guests):
                # Each measure is multiplied by the ratio of the new guest count to the base count.
                return amount * F(guests, BASE_GUESTS)

            for guests in (6, 10):
                parts = [f"{name} {scale(amount, guests)}" for name, amount in recipe]
                print(f"for {guests} guests: " + ", ".join(parts))
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.GUIDE,
        section = "Building",
        title = "How a rope's 3-4-5 knots mark a right angle",
        originalTitle = "Trattato di architettura",
        author = "Antonio Averlino, called Filarete",
        year = 1464,
        summary = "A rope with knots three, four and five units apart makes a right angle, because the squares of the two short sides add up to the square of the long side.",
        details = "Cords with knots were a common way to lay out right angles, and the example checks several knotted ropes with integer arithmetic. The triples 3-4-5, 5-12-13 and 8-15-17 pass the test, while 4-5-6 fails.",
        instructions = """
            1. Save the example as rope_knots.py.
            2. Run it: python3 rope_knots.py
        """,
        example = """
            def is_right_angle(a, b, c):
                # A triangle with sides a, b and c has its right angle opposite c when a*a + b*b equals c*c.
                return a * a + b * b == c * c

            knots = [(3, 4, 5), (5, 12, 13), (4, 5, 6), (8, 15, 17)]
            for a, b, c in knots:
                verdict = "right angle" if is_right_angle(a, b, c) else "no right angle"
                print(f"rope {a}-{b}-{c}: {verdict}")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.GUIDE,
        section = "Dialogue",
        title = "How guests seat themselves round a table without repeats",
        originalTitle = "Symposium",
        author = "Marsilio Ficino's circle at Careggi",
        year = 1462,
        summary = "Guests at a round table can sit in (n minus 1) factorial different orders when turning the table does not count as a new seating.",
        details = "The example counts the seatings of four, five and six guests at a round table, where turning the table gives no new seating. The Platonic circle at Careggi is the setting, but the count is a plain factorial and does not come from any text.",
        instructions = """
            1. Save the example as round_seating.py.
            2. Run it: python3 round_seating.py
        """,
        example = """
            from math import factorial

            for guests in (4, 5, 6):
                seatings = factorial(guests - 1)
                print(f"{guests} guests at a round table: {seatings} seatings when turning the table gives no new seating")
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.GUIDE,
        section = "Weights",
        title = "How a silver mark splits into ounces and grains",
        originalTitle = "Cologne silver weights, illustrative",
        author = "German silver trade, illustrative weights",
        year = 1466,
        summary = "A silver mark split into eight ounces of twenty-four grains each gives every piece as an exact fraction of the whole mark.",
        details = "Silver was weighed in marks and ounces in many German towns, and the example uses an illustrative mark of eight ounces with 24 grains to the ounce. The fractions are exact, so each part is printed without rounding.",
        instructions = """
            1. Save the example as silver_mark.py.
            2. Run it: python3 silver_mark.py
        """,
        example = """
            from fractions import Fraction as F

            MARK_OUNCES = 8      # an illustrative mark of eight ounces
            OUNCE_GRAINS = 24    # an illustrative ounce of twenty-four grains

            def part_of_mark(ounces, grains):
                # The piece is its grains over all the grains in the mark, kept as an exact fraction.
                total = ounces * OUNCE_GRAINS + grains
                return F(total, MARK_OUNCES * OUNCE_GRAINS)

            for ounces, grains in ((2, 0), (3, 12), (5, 18)):
                print(f"{ounces} ounces {grains} grains is {part_of_mark(ounces, grains)} of a mark")
        """,
    ),

)
