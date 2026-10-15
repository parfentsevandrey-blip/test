package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1590 to 1600: sorted herb names, Kepler's solid ratios, a staff's heights, a crank's stroke, a solution by weight and a weekday. */
val db45Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Plant names",
        title = "Gerard's plant names sorted by the alphabet",
        originalTitle = "The Herball, or Generall Historie of Plantes",
        author = "John Gerard",
        year = 1597,
        summary = "Sorting herb names by the alphabet is the simplest way to arrange a herbal's index, and a short program numbers six herbs in order.",
        details = "The herbs are common ones that Gerard described, but the list is illustrative and is not taken from the book's index. The program sorts the names without regard to capitals, so the output has one order whatever the case of the letters.",
        instructions = """
            1. Save the example as herb_sort.py.
            2. Run it: python3 herb_sort.py
        """,
        example = """
            herbs = ["Sage", "Borage", "Rosemary", "Fennel", "Mint", "Lovage"]

            for number, name in enumerate(sorted(herbs, key=str.lower), start=1):
                print(number, name)
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Solids",
        title = "Kepler's nested solids, squared radius ratios",
        originalTitle = "Mysterium cosmographicum",
        author = "Johannes Kepler",
        year = 1596,
        summary = "For a cube, a tetrahedron and an octahedron, the square of the outer radius over the square of the inner radius is a whole number, and a program prints the three ratios.",
        details = "The radii are taken for an edge of length one, and their squares are exact fractions, so the ratios come out as whole numbers. Kepler's own figures for the planetary spheres are not reproduced, and the icosahedron and dodecahedron give ratios that involve the golden section.",
        instructions = """
            1. Save the example as nested_solids.py.
            2. Run it: python3 nested_solids.py
        """,
        example = """
            from fractions import Fraction as F

            # Squared outer and inner radii of the cube, tetrahedron and octahedron, for an edge of length one.
            SOLIDS = {
                "cube": (F(3, 4), F(1, 4)),
                "tetrahedron": (F(3, 8), F(1, 24)),
                "octahedron": (F(1, 2), F(1, 6)),
            }
            for name, (outer, inner) in SOLIDS.items():
                print(f"{name}: outer over inner radius squared = {outer / inner}")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Staffs",
        title = "Heights by similar triangles with Blagrave's staff",
        originalTitle = "Baculum familiare",
        author = "John Blagrave",
        year = 1590,
        summary = "A staff of known length held at a known distance from the eye gives a tower's height by similar triangles, and a program prints three cases in exact fractions.",
        details = "The rule follows from the proportion between the staff and its distance from the eye, and the numbers are illustrative rather than Blagrave's own. One case gives a height that is not a whole number of feet, which the exact fraction shows clearly.",
        instructions = """
            1. Save the example as staff_height.py.
            2. Run it: python3 staff_height.py
        """,
        example = """
            from fractions import Fraction as F

            def tower_height(staff, staff_distance, tower_distance):
                # Similar triangles: the tower is to its distance as the staff is to its distance from the eye.
                return F(staff) * F(tower_distance) / F(staff_distance)

            for staff, near, far in ((3, 2, 40), (1, 3, 45), (5, 4, 27)):
                height = tower_height(staff, near, far)
                print(f"staff {staff} ft at {near} ft, tower at {far} ft: height {height} ft")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Sawmills",
        title = "A crank's stroke in the sawmill",
        originalTitle = "Windzaag",
        author = "Cornelis Corneliszoon van Uitgeest",
        year = 1593,
        summary = "One turn of a crank moves a saw from the bottom of its path to the top and back, so the stroke is twice the crank's radius, and a program prints three strokes.",
        details = "The crank radii are illustrative, and the twelve turns a minute used for the hourly count are an assumed speed, not a record of any mill. The program uses exact fractions, so a radius of 9/2 inches gives a stroke of 9 inches.",
        instructions = """
            1. Save the example as crank_stroke.py.
            2. Run it: python3 crank_stroke.py
        """,
        example = """
            from fractions import Fraction as F

            def stroke(radius_inches):
                # One turn lifts the saw from the bottom of its path to the top and back: twice the radius.
                return 2 * F(radius_inches)

            for radius in (4, 6, F(9, 2)):
                print(f"crank radius {radius} inches: saw stroke {stroke(radius)} inches")
            turns_per_minute = 12
            print("strokes per hour:", turns_per_minute * 60)
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Chemistry",
        title = "Mixing a solution by weight in Libavius's way",
        originalTitle = "Alchymia",
        author = "Andreas Libavius",
        year = 1597,
        summary = "The strength of a solution by weight is the salt's share of the whole mass, and a program works out three strengths and the water needed for a weaker one.",
        details = "The salt and water amounts are illustrative and are not recipes from Libavius's book, and the per cent is worked out with exact fractions. The program also finds how much water dilutes 20 grams of salt to five per cent, which is 380 grams of water.",
        instructions = """
            1. Save the example as solution_weight.py.
            2. Run it: python3 solution_weight.py
        """,
        example = """
            from fractions import Fraction as F

            def strength(salt, water):
                # The strength by weight is the salt's share of the whole mass, in per cent.
                return 100 * F(salt, salt + water)

            for salt, water in ((10, 90), (25, 100), (3, 17)):
                print(f"{salt} g salt in {water} g water: {strength(salt, water)} per cent")

            # Water needed to dilute 20 g of salt to a 5 per cent solution.
            salt = 20
            total = F(salt) / F(5, 100)
            print("water needed for 5 per cent:", total - salt, "g")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "The weekday of 1 January 1598",
        originalTitle = "Horloge astronomique de Lyon",
        author = "Lyon cathedral clockmakers",
        year = 1598,
        summary = "A calendar dial needs the weekday of each date, and a short program prints the weekdays of three dates in 1598 and 1600 in the Gregorian calendar.",
        details = "The Gregorian calendar had been adopted in France in 1582, so the dates on the Lyon clock's calendar follow it. The example uses Python's date library, which counts days from fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as weekdays.py.
            2. Run it: python3 weekdays.py
        """,
        example = """
            from datetime import date

            NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            for day in (date(1598, 1, 1), date(1598, 12, 31), date(1600, 1, 1)):
                print(day.isoformat(), NAMES[day.weekday()])
        """,
    ),

)
