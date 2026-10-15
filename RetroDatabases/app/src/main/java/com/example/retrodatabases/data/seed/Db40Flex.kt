package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1640 to 1650: the mercury in a barometer tube, the digit count of Mersenne's numbers and the readings of a six-wheel machine. */
val db40Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 40,
        kind = Kind.FLEX,
        section = "Pressure",
        title = "How much mercury fills a barometer tube of one square centimetre",
        originalTitle = "Mercury column of 76 centimetres",
        author = "Evangelista Torricelli",
        year = 1643,
        summary = "A barometer tube of one square centimetre, filled to 76 centimetres, holds about 1.03 kilograms of mercury, and one line of code computes the mass.",
        details = "Torricelli's tube held a column of mercury whose weight balanced the air, and the mass of that column is its density times its volume. The example uses the density of mercury, 13595.1 kilograms per cubic metre, and the 76 centimetre column, and prints the mass in kilograms.",
        instructions = """
            1. Save the example as mercury_mass.py.
            2. Run it: python3 mercury_mass.py
        """,
        example = """
            rho = 13595.1   # kilograms per cubic metre, mercury
            area = 1e-4     # one square centimetre, in square metres
            height = 0.76   # metres
            print(f"mercury in the tube: {rho * area * height:.2f} kg")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.FLEX,
        section = "Number theory",
        title = "How many digits Mersenne's numbers have, from the exponent alone",
        originalTitle = "Digits of Mersenne numbers",
        author = "Marin Mersenne",
        year = 1644,
        summary = "The number 2 to the power p minus 1 has a fixed number of digits that follows from the exponent p, which one line of arithmetic gives directly.",
        details = "The digit count follows from the base-ten logarithm of two, and the example checks the formula for Mersenne's exponents 67, 127 and 257 against the length of the number written out in full. The counts are 21, 39 and 78 digits.",
        instructions = """
            1. Save the example as mersenne_digits.py.
            2. Run it: python3 mersenne_digits.py
        """,
        example = """
            from math import floor, log10

            for p in (67, 127, 257):
                digits = floor(p * log10(2)) + 1
                print(f"2^{p} - 1 has {digits} digits, check {len(str(2 ** p - 1))}")
        """,
    ),

    entry(
        databaseId = 40,
        kind = Kind.FLEX,
        section = "Calculating machines",
        title = "How many readings a six-wheel machine can show",
        originalTitle = "Six-wheel counting train",
        author = "Blaise Pascal",
        year = 1642,
        summary = "A row of six decimal wheels can show 10 to the sixth power different readings, from 000000 to 999999, and one line of code counts them.",
        details = "Pascal's arithmetic machine used rows of decimal wheels, and each wheel has ten positions, so the readings of a row multiply. The example counts the readings for six wheels and prints the first and last reading in the wheel form that the machine shows.",
        instructions = """
            1. Save the example as wheel_readings.py.
            2. Run it: python3 wheel_readings.py
        """,
        example = """
            wheels = 6
            readings = 10 ** wheels
            print(f"{readings} readings, from {0:0{wheels}d} to {readings - 1:0{wheels}d}")
        """,
    ),

)
