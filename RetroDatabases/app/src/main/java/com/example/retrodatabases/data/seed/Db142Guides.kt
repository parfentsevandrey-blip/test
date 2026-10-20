package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 620 to 630: Bhaskara I's rational formula for the sine, written out as code and checked against the true sine. */
val db142Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.GUIDE,
        section = "Sanskrit",
        title = "How Bhaskara I's rational formula gives the sine of an angle, checked against the true sine by a program",
        originalTitle = "jya",
        author = "Bhaskara I",
        year = 629,
        summary = "Bhaskara I's rational formula gives the sine of an angle from a few products and a division, and a short program prints it beside the true sine.",
        details = "Bhaskara I states the rule under Aryabhata's name in his Mahabhaskariya, and some accounts tie it to his commentary of 629 instead, so 629 is the date of the commentary and not a settled date for the rule. The example takes pi as 180 degrees, so each value is an exact fraction that a program can print.",
        rare = true,
        instructions = """
            1. Save the example as bhaskara_sine.py.
            2. Run it: python3 bhaskara_sine.py
        """,
        example = """
            from fractions import Fraction
            import math

            def bhaskara_sine(degrees):
                # Bhaskara's rule with pi taken as 180 degrees
                product = degrees * (180 - degrees)
                return Fraction(16 * product, 5 * 180 * 180 - 4 * product)

            for degrees in (30, 45, 60, 90):
                approx = bhaskara_sine(degrees)
                exact = math.sin(math.radians(degrees))
                print(f"{degrees} degrees: {approx} = {float(approx):.4f}, sine {exact:.4f}")
        """,
    ),

)
