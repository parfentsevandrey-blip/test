package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1020 to 1030: how a hill's height and the dip of the horizon give the Earth's radius, as Biruni reckoned it. */
val db102Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.GUIDE,
        section = "Geodesy",
        title = "How a hill's height and the dip of the horizon give the Earth's radius",
        originalTitle = "How a hill's height and the dip of the horizon give the Earth's radius",
        author = "Abu Rayhan al-Biruni",
        year = 1025,
        summary = "Biruni's method takes the height of a hill and the dip of the horizon seen from its top, and gives the Earth's radius, which he worked out near Nandana in the early eleventh century.",
        details = "The hill is given as 652 cubits high and the dip as 34 arcminutes, and the formula R = h cos d / (1 - cos d) gives the radius in cubits, which some accounts convert to about 6,340 km. The sources date the measurement only to the early eleventh century, so the year here is approximate.",
        rare = true,
        instructions = """
            1. Save the example as biruni_radius.py.
            2. Run it: python3 biruni_radius.py
        """,
        example = """
            import math

            height = 652                         # the hill's height in cubits, as commonly given
            dip = math.radians(34 / 60)          # the dip of the horizon, 34 arcminutes
            c = math.cos(dip)
            radius = height * c / (1 - c)        # from cos(dip) = R / (R + height)
            print("radius of the Earth in thousands of cubits:", round(radius / 1000))
        """,
    ),

)
