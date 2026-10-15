package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1750 to 1760: a series of pentagonal exponents, the powers of an achromat and the Moon's month in whole days. */
val db29Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.FLEX,
        section = "Partitions",
        title = "Euler's pentagonal number theorem",
        originalTitle = "Letter to Goldbach on the pentagonal numbers",
        author = "Leonhard Euler",
        year = 1750,
        summary = "The product of (1 - x^k) for k from 1 to 12 expands to a series whose exponents are pentagonal numbers, with alternating signs.",
        details = "Euler stated the theorem in a letter to Goldbach in 1750, and the first printed proof appeared in 1760. Accounts differ on which paper carries it, and the program multiplies out the product to degree 12 and prints the coefficients.",
        instructions = """
            1. Save the example as pentagonal.py.
            2. Run it: python3 pentagonal.py
        """,
        example = """
            N = 12
            coeffs = [1] + [0] * N
            for k in range(1, N + 1):
                # multiply the series by (1 - x^k), keeping the terms up to x^N
                for d in range(N, k - 1, -1):
                    coeffs[d] -= coeffs[d - k]
            print(coeffs)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.FLEX,
        section = "Achromatic lenses",
        title = "Dollond's achromat powers from two glasses",
        originalTitle = "Achromatic object glass",
        author = "John Dollond",
        year = 1758,
        summary = "A two-glass lens is made achromatic by giving each glass a power in proportion to its dispersion, so that the colour error cancels.",
        details = "Dollond's lens of 1758 combined crown and flint glass. The example uses illustrative dispersion values of 60 and 36, not measured glass, and computes the two powers for a total power of one tenth.",
        instructions = """
            1. Save the example as achromat.py.
            2. Run it: python3 achromat.py
        """,
        example = """
            from fractions import Fraction as F

            V_crown, V_flint = 60, 36     # dispersion numbers, illustrative values
            P = F(1, 10)                  # wanted total power, a focal length of 10
            P_crown = P * V_crown / (V_crown - V_flint)
            P_flint = -P * V_flint / (V_crown - V_flint)
            print("crown power:", P_crown)
            print("flint power:", P_flint)
            print("total power:", P_crown + P_flint)
            print("colour test P/V summed to zero:", P_crown / V_crown + P_flint / V_flint == 0)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.FLEX,
        section = "Lunar motion",
        title = "Mayer's 360 degrees in whole days",
        originalTitle = "Tabulae motuum solis et lunae",
        author = "Tobias Mayer",
        year = 1753,
        summary = "The Moon's mean motion of 13 degrees 10 minutes 35 seconds a day gives 27 whole days in 360 degrees, with a fraction of a day left over.",
        details = "This is the arithmetic behind the month in Mayer's tables of 1753. The example uses integer seconds of arc, so the answer is exact: 27 whole days, and 3051/9487 of a day is left over.",
        instructions = """
            1. Save the example as mayer_days.py.
            2. Run it: python3 mayer_days.py
        """,
        example = """
            from fractions import Fraction as F

            DAY = 13 * 3600 + 10 * 60 + 35     # mean daily motion of the Moon in seconds of arc: 47435
            CIRCLE = 360 * 3600                 # 360 degrees in seconds of arc: 1296000
            whole = CIRCLE // DAY
            rest = CIRCLE - whole * DAY
            print("whole days in 360 degrees:", whole)
            print("left over in arc seconds:", rest)
            print("left over as a fraction of a day:", F(rest, DAY))
        """,
    ),

)
