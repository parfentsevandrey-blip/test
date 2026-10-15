package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1680 to 1684: Leibniz's maxima rule, Tschirnhaus's squared roots, Borelli's muscle levers, La Hire's sundial line and a canal lock. */
val db36Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.GUIDE,
        section = "Calculus",
        title = "How Leibniz's 1684 rule finds the largest value of a curve",
        originalTitle = "Nova methodus pro maximis et minimis",
        author = "Gottfried Wilhelm Leibniz",
        year = 1684,
        summary = "Leibniz's rule of 1684 finds the highest or lowest point of a curve by setting its slope to zero, and the example applies it to one simple curve.",
        details = "The rule was printed in the Acta Eruditorum in October 1684, in a paper on maxima, minima and tangents. The example takes the curve y = x^2 (10 - x), finds the places where its slope is zero, at 0 and 20/3, and checks the slope on either side of the peak.",
        instructions = """
            1. Save the example as leibniz_maxima.py.
            2. Run it: python3 leibniz_maxima.py
        """,
        example = """
            from fractions import Fraction as F

            def height(x):
                # The curve is y = x^2 (10 - x), computed with exact fractions.
                return x * x * (10 - x)

            def slope(x):
                # Leibniz's rule: the height stops rising where the slope is zero; here the slope is 20x - 3x^2.
                return 20 * x - 3 * x * x

            # Setting the slope to zero gives 3x^2 - 20x = 0, with roots 0 and 20/3.
            top = F(20, 3)
            print("stationary points: 0 and", top)
            print("height at 20/3:", height(top))
            print(f"slope at 6: {slope(6)}, slope at 7: {slope(7)}")
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Tschirnhaus's squaring of the roots gives a new equation",
        originalTitle = "Methodus auferendi omnes terminos intermedios ex data aequatione",
        author = "Ehrenfried Walther von Tschirnhaus",
        year = 1683,
        summary = "Squaring every root of a cubic gives a new cubic whose coefficients follow from the old ones, and the example checks the new coefficients against the squared roots.",
        details = "Tschirnhaus published his method in the Acta Eruditorum in 1683, under a title that promised to remove every intermediate term from an equation. The example squares the roots 2, 3 and 4 of x^3 - 9x^2 + 26x - 24 and gets the new equation from the old coefficients alone, then checks it.",
        instructions = """
            1. Save the example as tschirnhaus_squares.py.
            2. Run it: python3 tschirnhaus_squares.py
        """,
        example = """
            roots = [2, 3, 4]
            e1, e2, e3 = 9, 26, 24   # sums of the roots taken one, two and three at a time

            # Squaring each root gives the new sums from the old ones alone.
            f1 = e1 * e1 - 2 * e2
            f2 = e2 * e2 - 2 * e1 * e3
            f3 = e3 * e3
            print("new equation: y^3 -", f1, "y^2 +", f2, "y -", f3, "= 0")

            squares = [r * r for r in roots]
            print("squares of the roots:", squares)
            s1 = sum(squares)
            s2 = squares[0] * squares[1] + squares[0] * squares[2] + squares[1] * squares[2]
            s3 = squares[0] * squares[1] * squares[2]
            print("direct check:", s1 == f1, s2 == f2, s3 == f3)
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.GUIDE,
        section = "Physiology",
        title = "How Borelli's levers show that a muscle pulls many times its load",
        originalTitle = "De motu animalium",
        author = "Giovanni Alfonso Borelli",
        year = 1680,
        summary = "Borelli treated the arm as a lever, so a muscle attached close to the joint must pull many times harder than the weight it holds.",
        details = "Borelli's De motu animalium of 1680 argued that bones act as levers worked by muscles, and that the short arm of a muscle forces it to pull hard. The example balances a load held in the hand against a muscle pull, with illustrative measurements chosen so that the answers are whole numbers.",
        instructions = """
            1. Save the example as borelli_levers.py.
            2. Run it: python3 borelli_levers.py
        """,
        example = """
            from fractions import Fraction as F

            def muscle_pull(load_kg, load_arm_cm, muscle_arm_cm):
                # Balance of turning moments about the joint: pull times the short arm equals load times the long arm.
                return F(load_kg * load_arm_cm, muscle_arm_cm)

            for load, long_arm, short_arm in ((5, 30, 3), (2, 25, 5)):
                pull = muscle_pull(load, long_arm, short_arm)
                print(f"load {load} kg at {long_arm} cm, muscle at {short_arm} cm: pull {pull} kg")
            print("ratio of pull to load in the first case:", muscle_pull(5, 30, 3) / 5)
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.GUIDE,
        section = "Dialling",
        title = "How La Hire's rule sets the 3 pm line of a horizontal sundial",
        originalTitle = "La gnomonique",
        author = "Philippe de La Hire",
        year = 1682,
        summary = "On a horizontal dial each hour line leaves the noon line at an angle whose tangent is the sine of the latitude times the tangent of the hour angle, and the example sets out the 3 pm line for two latitudes.",
        details = "La Hire's treatise on sundials was heard by the Royal Academy of Sciences in May 1682 and printed in Paris that year. The example uses two illustrative latitudes, given by sines of 3/5 and 4/5, so the tangents are exact fractions until the last step, which turns each angle into degrees. The 3 pm line has an hour angle of 45 degrees, where the tangent is 1.",
        instructions = """
            1. Save the example as sundial_hour_line.py.
            2. Run it: python3 sundial_hour_line.py
        """,
        example = """
            import math
            from fractions import Fraction as F

            # On a horizontal dial the hour line for hour angle h leaves the noon line at angle t, with tan t = sin(latitude) * tan h.
            # The 3 pm line has hour angle 45 degrees, where tan h is 1, so tan t is simply the sine of the latitude.
            TAN_3PM = 1
            for sin_lat in (F(3, 5), F(4, 5)):
                tan_t = sin_lat * TAN_3PM
                angle = math.degrees(math.atan(float(tan_t)))
                print(f"sine of latitude {sin_lat}: tangent of the 3 pm line {tan_t}, angle {angle:.2f} degrees")
        """,
    ),

    entry(
        databaseId = 36,
        kind = Kind.GUIDE,
        section = "Engineering",
        title = "How a canal lock fills: the water needed for one lockage",
        originalTitle = "Canal royal de Languedoc",
        author = "Pierre-Paul Riquet",
        year = 1681,
        summary = "A lock lifts a boat by filling its chamber, so the water used at each lockage is the chamber's length times its width times the lift.",
        details = "Riquet's canal of 1681 climbed the hills by flights of locks, fed from reservoirs above. The example works out the water for one lock and for a flight of three, with illustrative chamber dimensions that are not Riquet's figures.",
        instructions = """
            1. Save the example as canal_lock.py.
            2. Run it: python3 canal_lock.py
        """,
        example = """
            def water_per_lockage(length_m, width_m, lift_m):
                # One lockage fills the chamber up to the new level: the water used is length times width times lift.
                return length_m * width_m * lift_m

            print("one lock, 30 m by 5 m, lift 3 m: water", water_per_lockage(30, 5, 3), "cubic metres")
            lifts = [2, 2, 3]
            volumes = [water_per_lockage(30, 5, lift) for lift in lifts]
            total_lift = sum(lifts)
            print("flight of three locks, lifts 2, 2 and 3 m: water", sum(volumes), "cubic metres")
            print("water per metre of total lift:", sum(volumes) // total_lift)
        """,
    ),

)
