package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1830 to 1840: non-Euclidean angles and area, electrolysis, a hot-air cycle, galvanometer readings and the seconds pendulum. */
val db21Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Non-Euclidean geometry",
        title = "Lobachevsky's angle of parallelism",
        originalTitle = "Geometrische Untersuchungen zur Theorie der Parallellinien (1840)",
        author = "Nikolai Ivanovich Lobachevsky",
        year = 1840,
        summary = "The angle of parallelism in hyperbolic geometry falls from a right angle toward zero as the distance from a point to a line grows, following two times the arctangent of e to the minus distance.",
        details = "Lobachevsky developed his non-Euclidean geometry during the 1820s and 1830s, and his German account was printed in Berlin in 1840. The example evaluates the angle of parallelism at distances 0, 1 and 2 and prints the values in radians. The record uses the 1840 date.",
        instructions = """
            1. Save the example as lobachevsky_angle.py.
            2. Run it: python3 lobachevsky_angle.py
        """,
        example = """
            import math

            def angle_of_parallelism(x):
                # the angle of parallelism at distance x, in radians
                return 2 * math.atan(math.exp(-x))

            for x in (0, 1, 2):
                print(f"distance {x}: angle of parallelism {angle_of_parallelism(x):.4f} radians")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Non-Euclidean geometry",
        title = "Bolyai's angle defect and the area of a triangle",
        originalTitle = "Appendix scientiam spatii absolute veram exhibens (1832)",
        author = "Janos Bolyai",
        year = 1832,
        summary = "In the hyperbolic plane the area of a triangle is set by its angle defect, the amount by which its three angles fall short of two right angles.",
        details = "Bolyai's appendix of 1832 to his father's Tentamen presented the absolute science of space, a geometry that does not depend on the parallel postulate. The example takes a triangle with angles 40, 50 and 60 degrees, prints its defect, and prints its area in units of k squared. The record uses the 1832 date.",
        instructions = """
            1. Save the example as bolyai_defect.py.
            2. Run it: python3 bolyai_defect.py
        """,
        example = """
            import math
            from fractions import Fraction

            # the three angles of a triangle, in degrees, as exact fractions
            angles = [Fraction(40), Fraction(50), Fraction(60)]
            defect_deg = 180 - sum(angles)
            print(f"angle sum {sum(angles)} degrees, defect {defect_deg} degrees")
            # the area is the defect in radians, times k squared
            area = float(defect_deg) * math.pi / 180
            print(f"area {area:.4f} times k squared")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Electrolysis",
        title = "Faraday's laws of electrolysis: copper from a steady current",
        originalTitle = "Experimental Researches in Electricity, seventh series (1833)",
        author = "Michael Faraday",
        year = 1833,
        summary = "The mass of metal deposited by a steady current is proportional to the charge that passes and to the metal's equivalent weight, divided by the valency and the Faraday constant.",
        details = "Faraday related the weight of metal deposited in electrolysis to the quantity of electricity that passed, and he published these laws in the Experimental Researches of 1833 and 1834. The example uses modern values for the Faraday constant and the molar mass of copper, which were not known to him in these exact forms. The record uses the 1833 date.",
        instructions = """
            1. Save the example as faraday_electrolysis.py.
            2. Run it: python3 faraday_electrolysis.py
        """,
        example = """
            F = 96485        # coulombs per mole of electrons, a modern value used for the calculation
            M_Cu = 63.546    # molar mass of copper in grams, a modern value
            I = 2            # steady current in amperes
            t = 3600         # one hour in seconds
            z = 2            # electrons per copper ion
            charge = I * t
            mass = charge * M_Cu / (z * F)
            print(f"charge {charge} coulombs deposits {mass:.2f} g of copper")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Hot-air engines",
        title = "Ericsson's caloric cycle with a regenerator",
        originalTitle = "Caloric engine patent (1833)",
        author = "John Ericsson",
        year = 1833,
        summary = "The ideal efficiency of a hot-air engine that moves air between two temperatures through a perfect regenerator equals the Carnot value, one minus the cold temperature over the hot.",
        details = "Ericsson's caloric engines of the 1830s moved air between a hot and a cold space in a cylinder, and the cycle later carried his name. The example prints the ideal efficiency for three pairs of temperatures, exactly as fractions. The record uses the 1833 date of the first patent.",
        instructions = """
            1. Save the example as ericsson_cycle.py.
            2. Run it: python3 ericsson_cycle.py
        """,
        example = """
            from fractions import Fraction

            # with a perfect regenerator the ideal efficiency equals the Carnot value 1 - Tc/Th
            for hot, cold in ((600, 300), (900, 300), (500, 400)):
                eff = 1 - Fraction(cold, hot)
                print(f"hot {hot} K, cold {cold} K: ideal efficiency {eff}")
        """,
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Galvanometer",
        title = "Pouillet's tangent law for galvanometer readings",
        originalTitle = "Pouillet's tangent law for galvanometer readings (1837)",
        author = "Claude Servais Mathias Pouillet",
        year = 1837,
        summary = "In a tangent galvanometer the current is proportional to the tangent of the needle's deflection, so three deflection angles give three readings of the current.",
        details = "The example takes the coil constant as 1 and prints the current, in units of that constant, for deflections of 30, 45 and 60 degrees. The values follow the tangent law that Pouillet used in 1837. The record uses the 1837 date.",
        instructions = """
            1. Save the example as pouillet_reading.py.
            2. Run it: python3 pouillet_reading.py
        """,
        example = """
            import math

            K = 1.0   # the coil constant, set to 1 so that the readings are in units of K
            for deflection in (30, 45, 60):
                current = K * math.tan(math.radians(deflection))
                print(f"deflection {deflection} degrees: current {current:.4f} times K")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.EXAMPLE,
        section = "Pendulum",
        title = "The seconds pendulum from its period",
        originalTitle = "Secundenpendel length from the period, as in Bessel's work (1837)",
        author = "Friedrich Wilhelm Bessel",
        year = 1837,
        summary = "The period of a simple pendulum is two pi times the square root of length over gravity, so the length of a pendulum with a given period gives the value of gravity at that place.",
        details = "Bessel measured the length of the seconds pendulum at one place from 1835 onward, and he printed the results in 1837. The example uses the simple pendulum formula with a rounded value of gravity and prints the length for periods of 2 seconds and 1 second. The record uses the 1837 publication date.",
        instructions = """
            1. Save the example as bessel_pendulum.py.
            2. Run it: python3 bessel_pendulum.py
        """,
        example = """
            import math

            g = 9.81            # gravity in metres per second squared, a rounded value
            for period in (2.0, 1.0):
                length = g * (period / (2 * math.pi)) ** 2
                print(f"period {period} s: length {length:.4f} m")
        """,
    ),
)
