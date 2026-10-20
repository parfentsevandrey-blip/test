package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 60 to 69: a far tower's breadth found from the width of a sight-rod, a textbook sum for the dioptra's sighting. */
val db198Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.GUIDE,
        section = "Surveying",
        title = "How a far tower's breadth is found from the width of a sight-rod",
        originalTitle = "Sight-rod method",
        author = "Surveyors of the Greek and Roman world",
        year = 62,
        summary = "A rod of known width, held at a known distance from the eye and seen against a tower of known breadth, gives the distance to the tower, and a short program works one such sum.",
        details = "Hero's book describes the dioptra's use in surveying, but this rod method is a textbook sum of the kind the instrument made possible, not a worked example from that book. Its year is the placed year of the dioptra, 62, which is an anchor and not the date of the method, and the figures in the example are illustrative.",
        instructions = """
            1. Save the example as sight_rod.py.
            2. Run it: python3 sight_rod.py
        """,
        example = """
            tower_breadth = 60   # feet, illustrative breadth of the tower
            rod_width = 1        # feet, width of the sight-rod
            rod_distance = 4     # feet from the eye to the sight-rod
            distance = tower_breadth * rod_distance // rod_width
            print("distance to the tower in feet:", distance)
            print("distance in paces of 5 feet:", distance // 5)
        """,
        rare = true,
    ),

)
