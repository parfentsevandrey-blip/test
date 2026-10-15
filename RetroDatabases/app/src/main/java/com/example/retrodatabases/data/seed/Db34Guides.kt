package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1700 to 1710: compass variation, a seed drill's spacing, a plant key, a pump's lift and Newton's identities. */
val db34Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How compass variation turns a magnetic course into a true course",
        originalTitle = "Chart of the variation of the compass",
        author = "Edmond Halley",
        year = 1701,
        summary = "A ship's magnetic course is corrected by the local variation of the compass, which Halley's chart of 1701 showed, to give the true course.",
        details = "Halley's chart gave the variation of the compass across the Atlantic, and a navigator added or subtracted it to turn a magnetic course into a true one. The example adds a variation to a magnetic course and keeps the result between 0 and 359 degrees, which is the step that is easy to get wrong.",
        instructions = """
            1. Save the example as variation.py.
            2. Run it: python3 variation.py
        """,
        example = """
            def true_course(magnetic, east_variation):
                # The east variation is added to the magnetic course; the result is kept between 0 and 359 degrees.
                return (magnetic + east_variation) % 360

            for magnetic, variation in ((350, 15), (20, -30), (90, 0)):
                print(f"magnetic {magnetic}, variation {variation:+d}: true course {true_course(magnetic, variation)}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.GUIDE,
        section = "Agriculture",
        title = "How a seed drill's gears set the row spacing",
        originalTitle = "Horse-Hoeing Husbandry",
        author = "Jethro Tull",
        year = 1701,
        summary = "The spacing of seeds in a row follows from the wheel's circumference and from the gearing that drives the seed shaft.",
        details = "Tull's drill of 1701 dropped seed from a box as a wheel turned, and its gears decided how many drops came in each length of travel. The example uses illustrative figures, not Tull's own, to show that the spacing is the circumference divided by the number of drops per turn.",
        instructions = """
            1. Save the example as seed_drill.py.
            2. Run it: python3 seed_drill.py
        """,
        example = """
            from fractions import Fraction as F

            def row_spacing(wheel_circumference, driving_teeth, driven_teeth, holes):
                # Illustrative figures only: the wheel turns the gear on the seed shaft, and each hole drops one seed per shaft turn.
                shaft_turns = F(driving_teeth, driven_teeth)
                seeds_per_wheel_turn = holes * shaft_turns
                return F(wheel_circumference) / seeds_per_wheel_turn

            for args in ((90, 12, 4, 1), (90, 12, 6, 1), (90, 12, 4, 2), (60, 8, 4, 3)):
                print("spacing in cm:", row_spacing(*args))
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.GUIDE,
        section = "Botany",
        title = "How Tournefort's key sorts plants by flower and fruit",
        originalTitle = "Institutiones rei herbariae",
        author = "Joseph Pitton de Tournefort",
        year = 1700,
        summary = "A branching key puts each plant into a group by first its flower shape and then its fruit, the order Tournefort used for his genera.",
        details = "Tournefort's system of 1700 treated the flower and the fruit as the main marks of a genus, and a key of this kind asks the questions in a fixed order. The example is a toy key with four plants and four groups, not Tournefort's own table.",
        instructions = """
            1. Save the example as tournefort_key.py.
            2. Run it: python3 tournefort_key.py
        """,
        example = """
            PLANTS = [
                ("plant 1", "bell", "capsule"),
                ("plant 2", "bell", "berry"),
                ("plant 3", "tube", "berry"),
                ("plant 4", "open", "capsule"),
            ]

            def group(corolla, fruit):
                # The flower is tested first, then the fruit; this key is a toy written in that spirit.
                if corolla == "tube":
                    return "tube group"
                if corolla == "bell" and fruit == "capsule":
                    return "bell capsule group"
                if corolla == "bell":
                    return "bell berry group"
                return "open group"

            for name, corolla, fruit in PLANTS:
                print(f"{name}: {group(corolla, fruit)}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.GUIDE,
        section = "Pumping",
        title = "How air pressure limits the lift of a suction pump",
        originalTitle = "The Miner's Friend",
        author = "Thomas Savery",
        year = 1702,
        summary = "A pump can raise water only until the water column weighs as much as the air pressing on the surface, which sets a lift of about ten metres.",
        details = "Savery's steam device of 1702 worked within the same limit, since water is pushed up a pipe only by the pressure of the air on the surface. The example uses a standard atmosphere and gives the lift in metres and in feet, the figure of about 34 feet being the one usually quoted.",
        instructions = """
            1. Save the example as suction_lift.py.
            2. Run it: python3 suction_lift.py
        """,
        example = """
            AIR_PRESSURE = 101325.0   # pascals, a standard atmosphere
            WATER_DENSITY = 1000.0    # kilograms per cubic metre
            GRAVITY = 9.81            # metres per second squared

            def lift_metres(pressure, density, g):
                # The column can rise only until its weight equals the air pressure that pushes it up.
                return pressure / (density * g)

            h = lift_metres(AIR_PRESSURE, WATER_DENSITY, GRAVITY)
            print(f"lift in metres: {h:.2f}")
            print(f"lift in feet: {h / 0.3048:.2f}")
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Newton's identities give the power sums of roots",
        originalTitle = "Arithmetica universalis, sive de compositione et resolutione arithmetica liber",
        author = "Isaac Newton",
        year = 1707,
        summary = "Newton's identities give each power sum of a polynomial's roots from its coefficients, without finding the roots themselves.",
        details = "Newton stated these relations in his Arithmetica universalis of 1707, though earlier algebraists had stated similar relations. The example computes the first four power sums of the roots 1, 2 and 3 from the coefficients, and checks them by adding the powers directly.",
        instructions = """
            1. Save the example as newton_identities.py.
            2. Run it: python3 newton_identities.py
        """,
        example = """
            def power_sums(e, top):
                # e maps 1, 2 and 3 to the elementary symmetric sums of three roots.
                # Newton's identities give each power sum from the earlier ones.
                p = {}
                for k in range(1, top + 1):
                    total = 0
                    for i in range(1, k):
                        if i in e:
                            total += (-1) ** (i - 1) * e[i] * p[k - i]
                    if k in e:
                        total += (-1) ** (k - 1) * k * e[k]
                    p[k] = total
                return [p[k] for k in range(1, top + 1)]

            e = {1: 6, 2: 11, 3: 6}   # sums of one, two and three roots of x^3 - 6x^2 + 11x - 6
            print("power sums p1 to p4:", " ".join(str(x) for x in power_sums(e, 4)))
            print("direct check:", [sum(r ** k for r in (1, 2, 3)) for k in range(1, 5)])
        """,
    ),

)
