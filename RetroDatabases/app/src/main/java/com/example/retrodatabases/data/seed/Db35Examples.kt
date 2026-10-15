package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1690 to 1700: the hanging chain of 1691 and the figure-eight curve of 1694, with the code that checks each claim. */
val db35Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "Bernoulli, Huygens and Leibniz on the catenary",
        originalTitle = "Catenaria",
        author = "Johann Bernoulli, Christiaan Huygens and Gottfried Wilhelm Leibniz",
        year = 1691,
        summary = "In 1691 three mathematicians answered Jakob Bernoulli's question and showed that a hanging chain takes the shape of a cosh curve, not a parabola.",
        details = "Galileo had suggested in 1638 that a hanging chain was a parabola, and Jakob Bernoulli posed the question in the Acta Eruditorum in 1690. The 1691 answers gave the curve y equals a cosh(x / a), and the example compares it with a parabola that has the same vertex and curvature.",
        instructions = """
            1. Save the example as catenary.py.
            2. Run it: python3 catenary.py
        """,
        example = """
            import math

            for x in (0.0, 1.0, 2.0):
                print(f"x={x:.0f}: catenary {math.cosh(x):.3f}")
            print(f"parabola with the same vertex and curvature at x=2: {1 + 2.0 ** 2 / 2:.3f}")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "The lemniscate of Bernoulli and its area",
        originalTitle = "Curvatura laminae elasticae",
        author = "Jakob Bernoulli",
        year = 1694,
        summary = "Jakob Bernoulli's figure-eight curve, the lemniscate, has a total area equal to the square of its size constant a.",
        details = "He described the curve in 1694 in the Acta Eruditorum, in a paper on the curvature of elastic strips. Its polar equation is r squared equals a squared times cos 2 theta, and the example adds up the area numerically and compares it with a squared.",
        instructions = """
            1. Save the example as lemniscate.py.
            2. Run it: python3 lemniscate.py
        """,
        example = """
            import math

            n = 2000
            a = 1.0
            h = (math.pi / 4) / n
            total = 0.0
            for i in range(n):
                theta = (i + 0.5) * h
                r2 = a * a * math.cos(2 * theta)
                total += 0.5 * r2 * h
            area = 4 * total
            print(f"lemniscate area with a = 1: {area:.3f}")
            print(f"exact value a squared: {a * a:.3f}")
            print("matches a squared:", abs(area - a * a) < 1e-6)
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Mechanics",
        title = "Amontons's law of friction, 1699",
        originalTitle = "De la resistance causee dans les machines",
        author = "Guillaume Amontons",
        year = 1699,
        summary = "Amontons told the Paris Academy in December 1699 that friction grows with the load pressing two surfaces together, not with their apparent contact area.",
        details = "His rules tie the resistance to the normal load, and the apparent area of contact plays no part in them. The example fixes one coefficient to keep the numbers simple, and shows that the friction does not change when the contact area changes.",
        instructions = """
            1. Save the example as amontons_friction.py.
            2. Run it: python3 amontons_friction.py
        """,
        example = """
            MU = 1 / 3

            def friction(load, area):
                return MU * load

            for load, area in ((30, 2), (30, 6), (60, 2)):
                print(f"load {load}, contact area {area}: friction {friction(load, area):.3f}")
            print("doubling the load doubles the friction:", friction(60, 2) == 2 * friction(30, 2))
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Hydrostatics",
        title = "Pressure at the foot of a fire-engine water column",
        originalTitle = "Pressure at the foot of a fire-engine water column",
        author = "Hose-engine makers of the 1690s",
        year = 1690,
        summary = "The pressure at the foot of a standing column of water depends only on its height and the density of the water.",
        details = "A standing column of water presses on its base with a pressure fixed by its height. The example uses fixed constants for water and gravity to compute the pressure for columns of 10, 20 and 30 metres.",
        instructions = """
            1. Save the example as water_column.py.
            2. Run it: python3 water_column.py
        """,
        example = """
            rho = 1000.0
            g = 9.81
            for height in (10.0, 20.0, 30.0):
                pressure = rho * g * height
                print(f"column {height:.0f} m: pressure {pressure / 1000:.1f} kPa")
            print(f"each metre of water adds {rho * g / 1000:.2f} kPa")
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Acoustics",
        title = "Sauveur's beats between two pipes in a 25 to 24 ratio",
        originalTitle = "Sur la determination d'un son fixe",
        author = "Joseph Sauveur",
        year = 1700,
        summary = "Sounds at 100 and 96 vibrations a second, a 25 to 24 ratio, beat four times a second, and Sauveur used beats of this kind to pin down small differences in pitch.",
        details = "His memoir of 1700 on fixing the frequency of a sound used beats to pin down frequency differences. The example takes the frequencies 100 and 96 and checks that their ratio reduces to 25 to 24.",
        instructions = """
            1. Save the example as beats.py.
            2. Run it: python3 beats.py
        """,
        example = """
            from fractions import Fraction

            f1 = 100
            f2 = 96
            print(f"beats per second: {abs(f1 - f2)}")
            print("ratio reduces to 25 to 24:", Fraction(f1, f2) == Fraction(25, 24))
        """,
    ),

    entry(
        databaseId = 35,
        kind = Kind.EXAMPLE,
        section = "Trade and empire",
        title = "The Darien subscription's shortfall of 1696",
        originalTitle = "Company of Scotland Trading to Africa and the Indies",
        author = "Company of Scotland directors",
        year = 1696,
        summary = "The Darien subscription sought 600,000 pounds and reached 400,000, so the shortfall was a third of the planned capital.",
        details = "The statute of June 1695 created the company, and the planners aimed for 600,000 pounds. Investors were invited in February 1696 to commit 400,000 pounds, a goal reached about six months later, and the example works out the sum still missing from the planned capital and its share of it.",
        instructions = """
            1. Save the example as darien.py.
            2. Run it: python3 darien.py
        """,
        example = """
            target = 600000
            raised = 400000
            shortfall = target - raised
            print(f"target {target} pounds, raised {raised} pounds: shortfall {shortfall}")
            print(f"shortfall as a fraction of the target: {shortfall / target:.3f}")
        """,
    ),

)
