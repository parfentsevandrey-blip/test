package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1750 to 1760: solids, charge, a lunar table, a magnetic force law, a chalk's loss of fixed air and a wheel's work. */
val db29Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Solids",
        title = "Euler's polyhedron count: vertices minus edges plus faces is two",
        originalTitle = "Letter to Goldbach on solids bounded by plane faces",
        author = "Leonhard Euler",
        year = 1750,
        summary = "For each of the five regular solids, vertices minus edges plus faces comes to two.",
        details = "Euler stated the count in a letter to Goldbach in 1750, and the printed proof followed in 1758. The example checks the five regular solids, using their vertex, edge and face counts.",
        instructions = """
            1. Save the example as polyhedra.py.
            2. Run it: python3 polyhedra.py
        """,
        example = """
            solids = [
                ("tetrahedron", 4, 6, 4),
                ("cube", 8, 12, 6),
                ("octahedron", 6, 12, 8),
                ("dodecahedron", 20, 30, 12),
                ("icosahedron", 12, 30, 20),
            ]
            for name, v, e, f in solids:
                print(name, v - e + f)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Electricity",
        title = "Franklin's charge shared between two connected bodies",
        originalTitle = "Experiments and Observations on Electricity",
        author = "Benjamin Franklin",
        year = 1751,
        summary = "When two bodies are joined, the total charge stays the same and it divides between them in proportion to their capacities.",
        details = "Franklin held that electricity is one fluid whose total is conserved, so joining two bodies shares the charge out and does not destroy it. The capacities of 2 and 3 are chosen for the example and are not historical values.",
        instructions = """
            1. Save the example as charge_share.py.
            2. Run it: python3 charge_share.py
        """,
        example = """
            from fractions import Fraction as F

            c1, c2 = 2, 3          # capacities of the two bodies, chosen for the example
            q1, q2 = 10, 0         # charges before the bodies are joined by a wire
            total = q1 + q2
            potential = F(total, c1 + c2)   # the common potential after joining
            print("total charge:", total)
            print("common potential:", potential)
            print("charge on body 1:", c1 * potential)
            print("charge on body 2:", c2 * potential)
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Lunar tables",
        title = "Mayer's mean lunar motion table",
        originalTitle = "Tabulae motuum solis et lunae",
        author = "Tobias Mayer",
        year = 1753,
        summary = "A table that adds the Moon's mean motion of 13 degrees 10 minutes 35 seconds for each day, wrapping at 360 degrees.",
        details = "Mayer's tables of 1753 used a mean daily motion of the Moon, and the example adds it for days 1, 2, 10 and 27. The wrap at 360 degrees is done with integer arithmetic in seconds of arc.",
        instructions = """
            1. Save the example as lunar_table.py.
            2. Run it: python3 lunar_table.py
        """,
        example = """
            # mean motion of the Moon per day, in seconds of arc: 13 degrees 10 minutes 35 seconds
            DAY = 13 * 3600 + 10 * 60 + 35
            CIRCLE = 360 * 3600


            def dms(seconds):
                d, rest = divmod(seconds, 3600)
                m, s = divmod(rest, 60)
                return f"{d} {m:02d} {s:02d}"


            for day in (1, 2, 10, 27):
                print("day", day, dms((DAY * day) % CIRCLE))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Magnetism",
        title = "Michell's inverse-square law for magnetic poles",
        originalTitle = "A Treatise of Artificial Magnets",
        author = "John Michell",
        year = 1750,
        summary = "Michell's treatise of 1750 states that the force between magnetic poles falls off as the inverse square of their distance.",
        details = "Michell argued from his magnetic experiments that the attraction between poles obeys an inverse-square law, and the example checks the force ratios at distances 1, 2 and 3. The record gives the 1750 statement of the law, and the Coulomb records of other decades are separate items.",
        rare = true,
        instructions = """
            1. Save the example as michell.py.
            2. Run it: python3 michell.py
        """,
        example = """
            from fractions import Fraction as F


            def force(d):
                # strength of the attraction at distance d, with the force at distance 1 taken as 1
                return F(1, d * d)


            print("force at distance 2 relative to distance 1:", force(2) / force(1))
            print("force at distance 3 relative to distance 1:", force(3) / force(1))
            print("force at distance 3 relative to distance 2:", force(3) / force(2))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Chemistry",
        title = "Black's chalk and fixed air",
        originalTitle = "Experiments upon Magnesia Alba, Quicklime, and Some Other Alcaline Substances",
        author = "Joseph Black",
        year = 1755,
        summary = "Heating chalk drives off a gas that Black called fixed air, and by modern masses chalk loses 11 parts in 25 as that gas.",
        details = "Black's experiments of 1754 and 1755 showed that limestone and magnesia lose weight on heating, and that the part lost is a gas he named fixed air. The example uses rounded modern atomic masses to find the share of chalk that leaves as fixed air.",
        instructions = """
            1. Save the example as chalk_air.py.
            2. Run it: python3 chalk_air.py
        """,
        example = """
            from fractions import Fraction as F

            # rounded modern atomic masses: calcium 40, carbon 12, oxygen 16
            ca, c, o = 40, 12, 16
            chalk = ca + c + 3 * o          # calcium carbonate, CaCO3
            fixed_air = c + 2 * o           # carbon dioxide, CO2, the fixed air that Black named
            print("molar mass of chalk:", chalk)
            print("fixed air in chalk:", fixed_air)
            print("fraction lost as fixed air:", F(fixed_air, chalk))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.EXAMPLE,
        section = "Hydraulics",
        title = "A waterwheel's work from a flow and a drop",
        originalTitle = "An experimental enquiry concerning the natural powers of water and wind to turn mills",
        author = "John Smeaton",
        year = 1759,
        summary = "The work of a wheel is the weight of water that falls each second times the drop, which the example computes for two drops.",
        details = "Smeaton's trials of 1759 compared wheels by the work they did. The example uses an illustrative flow of 3 cubic feet a second and drops of 20 and 10 feet, not Smeaton's data, and gives the work in foot-pounds.",
        instructions = """
            1. Save the example as wheel_work.py.
            2. Run it: python3 wheel_work.py
        """,
        example = """
            from fractions import Fraction as F

            weight = F(125, 2)   # pounds in a cubic foot of water, as a round value
            flow = 3             # cubic feet of water a second, an illustrative flow


            def work(drop):
                return flow * weight * drop   # foot-pounds a second


            for drop in (20, 10):
                per_second = work(drop)
                print("drop", drop, "feet: foot-pounds a second", per_second, "a minute", per_second * 60)
            print("ratio of the work for the two drops:", work(20) / work(10))
        """,
    ),

)
