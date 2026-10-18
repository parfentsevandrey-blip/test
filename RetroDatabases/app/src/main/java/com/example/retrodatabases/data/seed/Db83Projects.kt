package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1210 to 1220: the gaps between three general councils of the Latin Church, and the area of a surveyed field cut into triangles. */
val db83Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.PROJECT,
        section = "Councils",
        title = "Gaps between three general councils of the Latin Church",
        originalTitle = "Gaps between councils",
        author = "The councils of the Latin Church",
        year = 1215,
        summary = "The Third Lateran Council of 1179, the Fourth Lateran Council of 1215 and the First Council of Lyon of 1245 were general councils, and a short program measures the gaps between them.",
        details = "The gaps are counted in whole years from one council to the next, using the years of the three councils as commonly given. The span from the first council to the last is printed as well.",
        instructions = """
            1. Save the example as councils.py.
            2. Run it: python3 councils.py
        """,
        example = """
            councils = [("Lateran III", 1179), ("Lateran IV", 1215), ("Lyon I", 1245)]
            for (name_a, year_a), (name_b, year_b) in zip(councils, councils[1:]):
                print(name_a, "to", name_b, year_b - year_a, "years")
            print("span:", councils[-1][1] - councils[0][1], "years")
        """,
    ),

    entry(
        databaseId = 83,
        kind = Kind.PROJECT,
        section = "Land",
        title = "Area of a surveyed field of five corners, by triangles",
        originalTitle = "Fan of triangles",
        author = "Land measurers of the period",
        year = 1220,
        summary = "A field with five corners is split into three triangles from one corner, and a short program adds their areas to give the whole field in square rods and acres.",
        details = "The corners are given in rods on a plan, and each triangle's doubled area is a cross product of two sides. Cutting a field into triangles was a standard surveying method of the period, and Fibonacci's Practica geometriae of about 1220 is a handbook of the same craft.",
        instructions = """
            1. Save the example as field.py.
            2. Run it: python3 field.py
        """,
        example = """
            corners = [(0, 0), (30, 0), (50, 20), (30, 40), (0, 30)]   # corners in rods, illustrative
            x0, y0 = corners[0]
            twice_area = 0
            triangles = 0
            for i in range(1, len(corners) - 1):
                x1, y1 = corners[i]
                x2, y2 = corners[i + 1]
                twice_area += (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0)
                triangles += 1
            print("triangles:", triangles)
            print("area in square rods:", twice_area // 2)
            print("acres:", twice_area / 320)
        """,
    ),

)
