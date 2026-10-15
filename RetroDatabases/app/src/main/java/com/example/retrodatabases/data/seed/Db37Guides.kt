package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1670 to 1680: Hooke's law, Barrow's tangent triangle, Gregory's series, an anchor escapement and a balance spring. */
val db37Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "How Hooke's law gives the stretch of a spring",
        originalTitle = "Lectures de potentia restitutiva",
        author = "Robert Hooke",
        year = 1678,
        summary = "Hooke's law says the stretch of a spring is proportional to the force on it, so the stretch is the force divided by the stiffness.",
        details = "Hooke published the law in 1678 in Lectures de potentia restitutiva, after giving it in 1676 as the anagram ut tensio, sic vis. The example divides three forces by one stiffness and prints each stretch as an exact fraction, so no rounding hides the rule.",
        instructions = """
            1. Save the example as hooke_law.py.
            2. Run it: python3 hooke_law.py
        """,
        example = """
            from fractions import Fraction as F

            def stretch(force, stiffness):
                # Hooke's law: the stretch is the force divided by the stiffness of the spring.
                return F(force) / stiffness

            for force in (4, 9, 12):
                print(f"force {force}: stretch {stretch(force, 3)}")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How Barrow's characteristic triangle gives a slope",
        originalTitle = "Lectiones geometricae",
        author = "Isaac Barrow",
        year = 1670,
        summary = "Barrow's characteristic triangle gives the slope of a curve as the ratio of its small vertical side to its small horizontal side.",
        details = "Barrow's lectures of 1670 set out the triangle, whose sides are small increments of the coordinates. The example applies the rule to the curve y equal to x squared at three points, where the slope is twice x, and the outputs are easy to check by hand.",
        instructions = """
            1. Save the example as barrow_slope.py.
            2. Run it: python3 barrow_slope.py
        """,
        example = """
            def tangent_slope(x, power):
                # Barrow's triangle: for y equal to x to the power n, the ratio of the small sides is n times x to the power n minus 1.
                return power * x ** (power - 1)

            for x in (1, 2, 3):
                print(f"at x = {x}, the slope of y = x squared is {tangent_slope(x, 2)}")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.GUIDE,
        section = "Series",
        title = "How Gregory's arctangent series approximates pi",
        originalTitle = "Letter to John Collins",
        author = "James Gregory",
        year = 1671,
        summary = "Gregory's arctangent series gives pi over four as 1 minus 1 over 3 plus 1 over 5 and so on, so four times the sum approaches pi.",
        details = "Gregory set out the series in a letter of 1671, and Leibniz later derived the same series independently. The example adds the first few terms with exact fractions and prints each partial sum, which swings above and below pi as more terms are added.",
        instructions = """
            1. Save the example as gregory_pi.py.
            2. Run it: python3 gregory_pi.py
        """,
        example = """
            from fractions import Fraction as F

            def gregory_pi(terms):
                # Gregory's arctangent series at 1 gives pi over four as 1 - 1/3 + 1/5 - 1/7 and so on.
                total = F(0)
                for k in range(terms):
                    total += F((-1) ** k, 2 * k + 1)
                return 4 * total

            for terms in (1, 2, 3, 4):
                value = gregory_pi(terms)
                print(f"{terms} terms: {value} = {float(value):.4f}")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.GUIDE,
        section = "Clocks",
        title = "How an anchor escapement counts beats",
        originalTitle = "Anchor escapement",
        author = "William Clement",
        year = 1671,
        summary = "An anchor escapement releases the escape wheel one tooth for each full swing of the pendulum, so each tooth gives two beats.",
        details = "Clement's anchor escapement of 1671 was made for clocks with pendulums, and the count of beats follows from the number of teeth and the turns of the wheel. The example works out the beats per minute for three wheels and converts them to beats per second.",
        instructions = """
            1. Save the example as escapement_beats.py.
            2. Run it: python3 escapement_beats.py
        """,
        example = """
            def beats_per_minute(teeth, turns_per_minute):
                # Each tooth of the escape wheel gives one full swing, which is two beats.
                return 2 * teeth * turns_per_minute

            for teeth, turns in ((30, 1), (30, 2), (20, 1)):
                beats = beats_per_minute(teeth, turns)
                print(f"{teeth} teeth at {turns} turn(s) a minute: {beats} beats a minute, {beats / 60:.1f} beats a second")
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.GUIDE,
        section = "Clocks",
        title = "How a balance spring's period depends on stiffness",
        originalTitle = "Balance spring watch",
        author = "Christiaan Huygens",
        year = 1675,
        summary = "A balance wheel's period falls as the spring grows stiffer, in proportion to one over the square root of the stiffness when the wheel stays the same.",
        details = "Huygens's spring of 1675 made the beat of a watch depend on the spring, and with a fixed balance wheel the period varies as the inverse square root of the stiffness. The example uses stiffness values that are perfect squares, so the period ratios come out as exact fractions.",
        instructions = """
            1. Save the example as balance_period.py.
            2. Run it: python3 balance_period.py
        """,
        example = """
            from math import isqrt
            from fractions import Fraction as F

            def period_ratio(stiffness):
                # With the same balance wheel, the period is proportional to 1 over the square root of the stiffness.
                root = isqrt(stiffness)
                assert root * root == stiffness, "choose a perfect square"
                return F(1, root)

            for stiffness in (1, 4, 9, 16):
                print(f"stiffness {stiffness}: period ratio {period_ratio(stiffness)}")
        """,
    ),

)
