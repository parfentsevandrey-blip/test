package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 20 to 30: a Roman colony's grid of centuries, placed at 23 by the anchor of Drusus's death. */
val db202Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.GUIDE,
        section = "Land",
        title = "How a colony's grid of centuries is measured in code",
        originalTitle = "Centuriatio",
        author = "Roman land surveyors",
        year = 23,
        summary = "A Roman colony's land was marked out in squares called centuries, and a short program measures one centuria and a grid of them.",
        details = "The sources do not date the grid method, which is older than this anchor, so 23 is the year of the anchor, the death of Drusus Julius Caesar, and not the year a grid was laid out. A centuria was twenty actus, or 2,400 Roman feet, on each side and held 200 iugera, so the program's counts can be checked by hand.",
        instructions = """
            1. Save the example as centuria.py.
            2. Run it: python3 centuria.py
        """,
        example = """
            ACTUS_FEET = 120               # one actus, in Roman feet
            ACTUS_PER_SIDE = 20            # a centuria is twenty actus on each side
            IUGERUM_SQ_FEET = 240 * 120    # one iugerum, 240 by 120 Roman feet, as an area
            side = ACTUS_PER_SIDE * ACTUS_FEET
            area = side * side
            iugera = area // IUGERUM_SQ_FEET
            columns, rows = 3, 4
            print("side of a centuria in feet:", side)
            print("iugera in a centuria:", iugera)
            print("centuries in the grid:", columns * rows)
            print("iugera in the grid:", columns * rows * iugera)
            print("grid width in feet:", columns * side)
            print("grid length in feet:", rows * side)
        """,
        rare = true,
    ),

)
