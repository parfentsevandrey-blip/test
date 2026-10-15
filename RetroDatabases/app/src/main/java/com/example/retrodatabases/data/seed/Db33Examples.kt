package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Worked examples of 1710 to 1720: a degree of longitude, a Newcomen stroke, Kepler's law in whole numbers, Fahrenheit's fixed points, a mint ratio and three dice. */
val db33Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Navigation",
        title = "A degree of longitude at each latitude",
        originalTitle = "A degree of longitude at each latitude",
        author = "Parliament of Great Britain",
        year = 1714,
        summary = "A degree of longitude is about sixty nautical miles at the equator and shrinks with the cosine of the latitude, which the program shows exactly at three latitudes.",
        details = "The Longitude Act of 1714 judged proposals by their error in degrees and miles, so the length of a degree of longitude mattered. The example uses the exact cosines of 0, 60 and 90 degrees to print the length of one degree of longitude at each latitude, in nautical miles.",
        instructions = """
            1. Save the example as longitude_degrees.py.
            2. Run it: python3 longitude_degrees.py
        """,
        example = """
            from fractions import Fraction as F

            EQUATOR_MILES = 60   # one degree of longitude at the equator, in nautical miles

            # Exact cosines of three latitudes: cos 0 = 1, cos 60 = 1/2 and cos 90 = 0.
            cosines = {0: F(1), 60: F(1, 2), 90: F(0)}

            for latitude, cos in cosines.items():
                miles = EQUATOR_MILES * cos
                print(f"latitude {latitude} degrees: one degree of longitude is {miles} nautical miles")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Power",
        title = "The atmosphere's force on a Newcomen piston",
        originalTitle = "Newcomen engine",
        author = "Thomas Newcomen",
        year = 1712,
        summary = "Atmospheric pressure of about fifteen pounds a square inch pushes the piston down, and the program finds the force and work of one stroke.",
        details = "The Dudley Castle engine of 1712 relied on the air above its piston, which pushed the piston down once the cylinder below had been emptied of steam. The example uses round illustrative values of 15 pounds a square inch, a piston of 100 square inches and a stroke of 60 inches, and prints the force, the work of one stroke and the power at twelve strokes a minute.",
        instructions = """
            1. Save the example as newcomen_stroke.py.
            2. Run it: python3 newcomen_stroke.py
        """,
        example = """
            from fractions import Fraction as F

            PRESSURE = 15      # pounds on each square inch, a round figure close to one atmosphere
            AREA = 100         # square inches, an illustrative piston
            STROKE = 60        # inches

            force = PRESSURE * AREA                  # pounds
            work_inch_pounds = force * STROKE        # inch-pounds in one stroke
            work = F(work_inch_pounds, 12)           # foot-pounds in one stroke
            print("force on the piston:", force, "pounds")
            print("work in one stroke:", work, "foot-pounds")
            per_minute = work * 12                   # twelve strokes a minute
            print("at twelve strokes a minute:", per_minute, "foot-pounds a minute")
            print("horsepower:", per_minute / 33000)
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Kepler's third law in whole numbers",
        originalTitle = "Philosophiae naturalis principia mathematica",
        author = "Isaac Newton",
        year = 1713,
        summary = "Under the inverse square law the square of a period is the cube of the distance, and the program checks this for three orbits in whole numbers.",
        details = "Newton derived Kepler's third law from the inverse square law in the Principia, and the second edition of 1713 keeps the result. The example takes orbits at distances 1, 4 and 9 and finds the periods 1, 8 and 27, since the square of each period equals the cube of its distance.",
        instructions = """
            1. Save the example as kepler_third.py.
            2. Run it: python3 kepler_third.py
        """,
        example = """
            from math import isqrt

            # Under the inverse square law the square of the period equals the cube of the distance.
            for distance in (1, 4, 9):
                cube = distance ** 3
                period = isqrt(cube)
                print(f"distance {distance}: period {period}, squares agree: {period * period == cube}")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Thermometry",
        title = "Fahrenheit's fixed points in Celsius",
        originalTitle = "Thermometer",
        author = "Daniel Gabriel Fahrenheit",
        year = 1714,
        summary = "The fixed points of Fahrenheit's scale, 0, 32, 96 and 212 degrees, convert to Celsius as exact fractions, which the program prints.",
        details = "Fahrenheit's mercury thermometer of 1714 was graded on a scale published in 1724, with points at brine, freezing water, body heat and boiling water. The example converts those four points with the rule that Celsius equals five ninths of Fahrenheit minus 32, and it keeps the results exact.",
        instructions = """
            1. Save the example as fahrenheit_fixed.py.
            2. Run it: python3 fahrenheit_fixed.py
        """,
        example = """
            from fractions import Fraction as F

            # Celsius from Fahrenheit: C = 5/9 (F - 32).
            for fahrenheit in (0, 32, 96, 212):
                celsius = F(5, 9) * (fahrenheit - 32)
                print(f"{fahrenheit} F is {celsius} C")
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Coinage",
        title = "Newton's mint ratio of gold to silver",
        originalTitle = "Newton's guinea at twenty-one shillings",
        author = "Isaac Newton, Master of the Mint",
        year = 1717,
        summary = "The mint prices of standard gold and silver, corrected for their fineness, give a gold to silver ratio of about 15.21 to 1, which the program computes exactly.",
        details = "Newton's report of 21 September 1717 gave 44 and a half guineas from a troy pound of standard gold and 62 shillings from a troy pound of standard silver. Standard gold is 22 carat, or 11 parts in 12 fine, and sterling silver is 37 parts in 40 fine, so the example divides each price by its fine weight before taking the ratio.",
        instructions = """
            1. Save the example as mint_ratio.py.
            2. Run it: python3 mint_ratio.py
        """,
        example = """
            from fractions import Fraction as F

            guinea = 21                          # shillings, as fixed for the guinea in 1717
            gold_price = F(89, 2) * guinea       # 44 and a half guineas from a troy pound of standard gold, in shillings
            gold_fineness = F(11, 12)            # standard gold is 22 carat, 11 parts in 12 fine
            silver_price = F(62)                 # shillings from a troy pound of standard silver
            silver_fineness = F(37, 40)          # sterling silver is 37 parts in 40 fine

            gold_per_pure = gold_price / gold_fineness
            silver_per_pure = silver_price / silver_fineness
            ratio = gold_per_pure / silver_per_pure
            print("price of pure gold per pound, in shillings:", gold_per_pure)
            print("price of pure silver per pound, in shillings:", silver_per_pure)
            print("mint ratio of gold to silver:", ratio)
            print("rounded to two places:", round(float(ratio), 2))
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.EXAMPLE,
        section = "Probability",
        title = "Throws of a die for an even chance of a six",
        originalTitle = "The Doctrine of Chances",
        author = "Abraham de Moivre",
        year = 1718,
        summary = "The chance of at least one six in n throws is one minus five sixths to the power n, and the program finds the first n that gives more than an even chance.",
        details = "De Moivre's rules of 1718 apply to any number of independent throws, and the chance of at least one six is a simple case of them. The example prints the chance for one to five throws in exact fractions, and finds the first number of throws that gives more than an even chance.",
        instructions = """
            1. Save the example as six_throws.py.
            2. Run it: python3 six_throws.py
        """,
        example = """
            from fractions import Fraction as F

            # The chance of no six in one throw is 5/6, and throws are independent.
            for n in range(1, 6):
                chance = 1 - F(5, 6) ** n
                print(f"n = {n}: chance of at least one six {chance}")
            first = next(n for n in range(1, 100) if 1 - F(5, 6) ** n > F(1, 2))
            print("first number of throws above an even chance:", first)
        """,
    ),
)
