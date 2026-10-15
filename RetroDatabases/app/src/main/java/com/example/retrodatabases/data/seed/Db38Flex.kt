package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1660 to 1670: a wager's break-even chance, a crew rule of the Navigation Act and a column of mercury in pascals. */
val db38Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.FLEX,
        section = "Probability",
        title = "Pascal's wager break-even chance",
        originalTitle = "Pensees",
        author = "Blaise Pascal",
        year = 1670,
        summary = "With finite stakes, Pascal's wager makes believing pay on average only above a break-even chance, and the calculation takes one line.",
        details = "Pascal's argument for belief appeared in the Pensees, first printed in 1670, and its gain is infinite. The example replaces it with invented finite stakes of 10 and 2, to find the break-even chance and check the payoff at two chances.",
        instructions = """
            1. Save the example as pascal_wager.py.
            2. Run it: python3 pascal_wager.py
        """,
        example = """
            from fractions import Fraction as F

            GAIN = 10   # gain if the wager is won; an invented figure
            LOSS = 2    # loss if it is lost; an invented figure

            def expected_payoff(p):
                # Believing pays on average when p times the gain minus (1 - p) times the loss is positive.
                return p * GAIN - (1 - p) * LOSS

            break_even = F(LOSS, GAIN + LOSS)
            print("break-even chance:", break_even)
            print("expected payoff at chance 1/3:", expected_payoff(F(1, 3)))
            print("expected payoff at break-even:", expected_payoff(break_even))
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.FLEX,
        section = "Navigation",
        title = "Navigation Act crew rule of 1660",
        originalTitle = "Navigation Act of 1660",
        author = "The English Parliament",
        year = 1660,
        summary = "The Navigation Acts of this period asked that three quarters of a ship's crew be English, so a crew of 16 needed at least 12 English seamen.",
        details = "The Navigation Act of 1660 is usually cited for this rule, which the example applies to two crew sizes. The program rounds the English share up to a whole number, so a crew of 15 also needs 12 English seamen.",
        instructions = """
            1. Save the example as navigation_crew.py.
            2. Run it: python3 navigation_crew.py
        """,
        example = """
            from math import ceil

            def english_needed(crew):
                # The rule asks that at least three quarters of the crew be English, so the count is rounded up.
                return ceil(3 * crew / 4)

            for crew in (16, 15):
                print(f"crew of {crew}: at least {english_needed(crew)} English")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.FLEX,
        section = "Weather",
        title = "An inch of mercury in pascals",
        originalTitle = "Barometer readings in inches of mercury",
        author = "Robert Hooke",
        year = 1663,
        summary = "A column of mercury one inch high presses about 3386 pascals, the figure a modern reader needs to turn the inches on a barometer dial into a standard unit.",
        details = "Barometers of Hooke's time were read in inches of mercury, and a modern unit needs the density of mercury, gravity and the length of an inch in metres. The example multiplies those three figures and also shows a column of 30 inches in kilopascals, close to the standard atmosphere.",
        instructions = """
            1. Save the example as mercury_pascal.py.
            2. Run it: python3 mercury_pascal.py
        """,
        example = """
            RHO = 13595.1      # density of mercury at 0 degrees C, in kilograms per cubic metre
            G = 9.80665        # standard gravity, in metres per second squared
            INCH = 0.0254      # one inch, in metres

            pascals = RHO * G * INCH   # pressure of a column of mercury: density times gravity times height
            print(f"one inch of mercury: {pascals:.0f} pascals")
            print(f"a column of 30 inches: {30 * pascals / 1000:.1f} kilopascals")
        """,
    ),

)
