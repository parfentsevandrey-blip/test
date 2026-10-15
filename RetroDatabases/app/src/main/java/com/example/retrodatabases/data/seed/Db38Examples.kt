package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1660 to 1670: a logarithm series, a cork count, a city fire, trees to the acre, a map scale and a lens magnifier. */
val db38Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Logarithms",
        title = "Mercator's logarithm series for the natural log of two",
        originalTitle = "Logarithmotechnia",
        author = "Nicolaus Mercator",
        year = 1668,
        summary = "Mercator's series gives the natural logarithm of 1 plus x as an alternating sum of powers, and at x equal to 1 its partial sums close in on the log of two.",
        details = "Mercator printed the series in his Logarithmotechnia of 1668, and it converges slowly at x equal to 1. The example adds the first terms, prints each partial sum as an exact fraction and as a decimal, and compares the last one with the natural log of two.",
        instructions = """
            1. Save the example as mercator_series.py.
            2. Run it: python3 mercator_series.py
        """,
        example = """
            from fractions import Fraction as F
            from math import log

            def mercator_partial(terms):
                # Mercator's series for the natural log of 1 + x, here at x = 1: 1 - 1/2 + 1/3 - 1/4 + ...
                total = F(0)
                for k in range(1, terms + 1):
                    total += F((-1) ** (k + 1), k)
                return total

            for n in (2, 4, 8):
                s = mercator_partial(n)
                print(f"{n} terms: {s} = {float(s):.6f}")
            print(f"log of 2: {log(2):.6f}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Microscopy",
        title = "Hooke's cork cells in a cubic inch",
        originalTitle = "Micrographia",
        author = "Robert Hooke",
        year = 1665,
        summary = "Hooke estimated the cells in a cubic inch of cork by counting along one line and cubing the result, reaching about 1,259,712,000 cells.",
        details = "The starting count was about sixty cells along a line one-eighteenth of an inch long, which scales to 1080 cells in an inch. The example repeats that arithmetic, squaring the linear count for a square inch and cubing it for a cubic inch.",
        instructions = """
            1. Save the example as cork_cells.py.
            2. Run it: python3 cork_cells.py
        """,
        example = """
            # Hooke's count: about sixty cells along a line one-eighteenth of an inch long, so 60 x 18 cells fit in an inch.
            CELLS_ON_LINE = 60
            LINES_PER_INCH = 18

            per_inch = CELLS_ON_LINE * LINES_PER_INCH
            per_square_inch = per_inch ** 2
            per_cubic_inch = per_inch ** 3
            print("cells per inch:", per_inch)
            print("cells per square inch:", per_square_inch)
            print("cells per cubic inch:", per_cubic_inch)
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Fire",
        title = "The Great Fire, house by house",
        originalTitle = "The Great Fire of London",
        author = "Samuel Pepys",
        year = 1666,
        summary = "A fire that began in a bakery in Pudding Lane on 2 September 1666 and went through the timber houses of the City of London over several days.",
        details = "Pepys recorded the fire in his diary, and the fire is usually traced to a bakery in Pudding Lane. The example models its spread as a grid of houses in which each burning house sets fire to the houses beside it, with open ground that stops the flames, and the layout is invented.",
        instructions = """
            1. Save the example as great_fire.py.
            2. Run it: python3 great_fire.py
        """,
        example = """
            # A city on a grid: H is a timber house and a dot is open ground that stops the fire. The layout is invented.
            CITY = [
                "HHH.H",
                "HHH.H",
                "HH.HH",
            ]

            def houses(city):
                return {(r, c) for r, row in enumerate(city) for c, ch in enumerate(row) if ch == "H"}

            standing = houses(CITY)
            burning = {(0, 0)}          # the fire starts at the top left, as if in Pudding Lane
            standing.discard((0, 0))
            burned = set()
            step = 0
            while burning:
                step += 1
                # A standing house beside a burning house catches fire; the burning houses then burn out.
                neighbours = {(r + dr, c + dc) for r, c in burning for dr, dc in ((1, 0), (-1, 0), (0, 1), (0, -1))}
                catching = neighbours & standing
                standing -= catching
                burned |= burning
                burning = catching
                print(f"after step {step}: burning {len(burning)}, burned {len(burned)}")
            print(f"houses still standing: {len(standing)}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Forestry",
        title = "Trees to the acre by spacing",
        originalTitle = "Sylva, or a Discourse of Forest-Trees",
        author = "John Evelyn",
        year = 1664,
        summary = "Evelyn's Sylva of 1664 urged the planting of timber trees, and the example works out how many trees fit on an acre at a given spacing.",
        details = "Evelyn wrote the book at the request of the Royal Society, and it argued that England needed more timber for its ships and houses. The spacings in the example are invented, and the program counts whole trees on a square grid of one acre.",
        instructions = """
            1. Save the example as trees_acre.py.
            2. Run it: python3 trees_acre.py
        """,
        example = """
            ACRE_SQUARE_FEET = 43560   # an acre is 43,560 square feet

            for spacing in (6, 8, 10):
                # Trees set on a square grid, spacing feet apart, each take a square of spacing times spacing feet.
                count = ACRE_SQUARE_FEET // (spacing * spacing)
                print(f"spacing {spacing} feet: {count} trees to the acre")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Cartography",
        title = "Blaeu's Atlas Maior map scale",
        originalTitle = "Atlas Maior",
        author = "Joan Blaeu",
        year = 1662,
        summary = "Blaeu's Atlas Maior, begun in 1662, printed maps drawn to scales, and a distance measured on such a map converts to the ground by its scale.",
        details = "The atlas came out in volumes from 1662, and its maps were drawn to scales as maps of the period were. The example converts measured distances at a scale of 1 to 250,000 into kilometres, with the distances invented.",
        instructions = """
            1. Save the example as atlas_scale.py.
            2. Run it: python3 atlas_scale.py
        """,
        example = """
            from fractions import Fraction as F

            # A printed scale of 1 to 250,000: 1 centimetre on the map stands for 250,000 centimetres on the ground.
            SCALE = 250000
            measured_cm = [F(3), F(7, 2), F(12)]   # distances taken with dividers, in centimetres; invented figures

            for cm in measured_cm:
                ground_km = cm * SCALE / 100000     # 100,000 centimetres make one kilometre
                print(f"{float(cm):.1f} cm on the map is {float(ground_km):.2f} km on the ground")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.EXAMPLE,
        section = "Optics",
        title = "Spinoza's lens as a magnifier",
        originalTitle = "Spinoza's lens grinding",
        author = "Baruch Spinoza",
        year = 1665,
        summary = "A simple magnifier of the kind Spinoza ground multiplies the view by the reading distance divided by its focal length.",
        details = "Spinoza ground lenses for microscopes and telescopes in the 1660s, and the grinding of such lenses is one of the best-known parts of his life. The example applies the modern formula, with the conventional reading distance of 25 cm, to three invented focal lengths.",
        instructions = """
            1. Save the example as spinoza_magnifier.py.
            2. Run it: python3 spinoza_magnifier.py
        """,
        example = """
            from fractions import Fraction as F

            NEAR_POINT_CM = 25   # the conventional reading distance of 25 cm, used here as the comparison distance

            def magnifier_power(focal_cm):
                # A simple magnifier shows the view magnified by the reading distance divided by its focal length.
                return F(NEAR_POINT_CM, focal_cm)

            for focal in (2, 5, 10):
                power = float(magnifier_power(focal))
                print(f"focal length {focal} cm: magnifies {power} times")
        """,
    ),

)
