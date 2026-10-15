package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1570 to 1580: Bombelli's cube roots, a stereographic projection, the thirty-two compass points, a triangle's centre of gravity and the angles of a polygon. */
val db47Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.GUIDE,
        section = "Algebra",
        title = "How Bombelli's imaginary cube roots give a real root",
        originalTitle = "L'Algebra",
        author = "Rafael Bombelli",
        year = 1572,
        summary = "Bombelli's Algebra of 1572 used the sum of two imaginary cube roots to reach the real root 4 of an equation that Cardano's rule did not solve directly.",
        details = "Bombelli, an engineer of Bologna, showed in his book of 1572 that the square root of a negative number could hide a real root. The example multiplies pairs of the form a + bi with whole numbers, so that 2 + i and 2 - i cube to 2 + 11i and 2 - 11i, and their sum gives the root 4 of x^3 = 15x + 4.",
        instructions = """
            1. Save the example as bombelli_roots.py.
            2. Run it: python3 bombelli_roots.py
        """,
        example = """
            def mul(a, b):
                # Multiply two numbers written as (real, imaginary) pairs of whole numbers.
                return (a[0] * b[0] - a[1] * b[1], a[0] * b[1] + a[1] * b[0])

            root = (2, 1)          # 2 + i
            conjugate = (2, -1)    # 2 - i
            cube = mul(mul(root, root), root)
            print("(2 + i) cubed:", cube)
            print("(2 - i) cubed:", mul(mul(conjugate, conjugate), conjugate))
            total = (root[0] + conjugate[0], root[1] + conjugate[1])
            print("sum of the two roots:", total)
            print("x = 4 solves x^3 = 15x + 4:", 4 ** 3 == 15 * 4 + 4)
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.GUIDE,
        section = "Planispheres",
        title = "How a stereographic projection sends the sphere to a plane",
        originalTitle = "Planisphaerium",
        author = "Guidobaldo del Monte",
        year = 1579,
        summary = "A stereographic projection from the north pole sends each point of the sphere to a point of the plane, and the example applies that rule to points with fraction coordinates.",
        details = "Del Monte's Planisphaerium of 1579 treats this projection, the rule on which the planisphere is built. The example uses points on the unit sphere whose coordinates are fractions, so each image comes out as an exact fraction, and it checks that the south pole goes to the centre of the plane.",
        instructions = """
            1. Save the example as stereographic.py.
            2. Run it: python3 stereographic.py
        """,
        example = """
            from fractions import Fraction as F

            def project(point):
                # Project from the north pole (0, 0, 1) onto the plane through the equator.
                x, y, z = point
                d = 1 - z
                return (x / d, y / d)

            points = [
                ("south pole", (F(0), F(0), F(-1))),
                ("point (3/5, 0, 4/5)", (F(3, 5), F(0), F(4, 5))),
                ("point (0, 4/5, 3/5)", (F(0), F(4, 5), F(3, 5))),
                ("point (-1, 0, 0)", (F(-1), F(0), F(0))),
            ]
            for name, point in points:
                x, y = project(point)
                print(f"{name} maps to {x}, {y}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How the thirty-two points of the compass divide the circle",
        originalTitle = "A Regiment for the Sea",
        author = "William Bourne",
        year = 1574,
        summary = "The thirty-two points of the compass divide the full circle into equal parts of 11 and a quarter degrees, and the example prints the point names at the main bearings.",
        details = "Bourne's manual of 1574 was written for English seamen, and the names of the thirty-two points, such as N by E and NE by N, were the usual names of the compass rose in the navigation books of the period. The example uses exact fractions, so 360 degrees divided by 32 points gives 45/4 degrees exactly.",
        instructions = """
            1. Save the example as compass_points.py.
            2. Run it: python3 compass_points.py
        """,
        example = """
            from fractions import Fraction as F

            POINTS = ["N", "NbE", "NNE", "NEbN", "NE", "NEbE", "ENE", "EbN",
                      "E", "EbS", "ESE", "SEbE", "SE", "SEbS", "SSE", "SbE",
                      "S", "SbW", "SSW", "SWbS", "SW", "SWbW", "WSW", "WbS",
                      "W", "WbN", "WNW", "NWbW", "NW", "NWbN", "NNW", "NbW"]

            step = F(360, 32)
            for index in (0, 8, 16, 24, 31):
                print(f"{index} {POINTS[index]} at {F(index) * step} degrees")
            print(f"one point spans {step} degrees")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "How the centre of gravity of a triangle sits at its medians",
        originalTitle = "Mechanicorum liber",
        author = "Guidobaldo del Monte",
        year = 1577,
        summary = "The centre of gravity of a triangle lies two thirds of the way along each median, so the three medians meet at one point, and the example checks this for one triangle.",
        details = "Del Monte's Mechanicorum of 1577 finds the centres of gravity of plane figures in the manner of Archimedes, and the rule for a triangle is among its basic results. The example uses one triangle with whole-number corners, so each of the three points comes out exact and they all agree.",
        instructions = """
            1. Save the example as centre_gravity.py.
            2. Run it: python3 centre_gravity.py
        """,
        example = """
            from fractions import Fraction as F

            A = (F(0), F(0))
            B = (F(6), F(0))
            C = (F(0), F(3))

            def midpoint(p, q):
                return ((p[0] + q[0]) / 2, (p[1] + q[1]) / 2)

            def two_thirds_along(vertex, mid):
                # The centre of gravity lies two thirds of the way from a corner to the midpoint of the opposite side.
                return (vertex[0] + F(2, 3) * (mid[0] - vertex[0]), vertex[1] + F(2, 3) * (mid[1] - vertex[1]))

            for name, corner, side in (("A", A, (B, C)), ("B", B, (A, C)), ("C", C, (A, B))):
                point = two_thirds_along(corner, midpoint(*side))
                print(f"median from {name} reaches the centre at {point[0]}, {point[1]}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How a polygon's angles add up, after Euclid's first book",
        originalTitle = "Euclidis Elementorum libri XV",
        author = "Federico Commandino",
        year = 1572,
        summary = "A polygon with n sides has interior angles that add up to 2 times n minus 4 right angles, which is 180 times n minus 2 degrees, and the example prints the sum for four shapes.",
        details = "Euclid's first book proves that the angles of a triangle make two right angles, and the sum for any polygon follows by cutting it into triangles from one corner. Commandino's Latin edition of 1572 carries the same proof, and the example prints the sums for the triangle, the quadrilateral, the pentagon and the hexagon.",
        instructions = """
            1. Save the example as polygon_angles.py.
            2. Run it: python3 polygon_angles.py
        """,
        example = """
            SHAPES = [("triangle", 3), ("quadrilateral", 4), ("pentagon", 5), ("hexagon", 6)]
            for name, sides in SHAPES:
                right_angles = 2 * (sides - 2)
                print(f"{name}: {right_angles} right angles, {180 * (sides - 2)} degrees")
        """,
    ),

)
