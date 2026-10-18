package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1133: a count of Roman paces turned into miles and the paces left over. */
val db91Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.GUIDE,
        section = "Distances",
        title = "How a count of paces is turned into Roman miles",
        originalTitle = "Mille passus",
        author = "Roman surveyors and medieval travellers",
        year = 1133,
        summary = "A Roman mile was a thousand paces, and a short program turns a count of paces into whole miles with the paces left over.",
        details = "The mile was the thousand paces of the Roman road measure, and medieval itineraries kept the unit for their distances. Each pace counted as a double step, and the example divides a count of paces by a thousand to give miles and the paces left over, so each result can be checked by hand.",
        instructions = """
            1. Save the example as miles.py.
            2. Run it: python3 miles.py
        """,
        example = """
            PACES_PER_MILE = 1000
            for paces in [12450, 3000, 999]:
                miles, left = divmod(paces, PACES_PER_MILE)
                print(f"{paces} paces: {miles} miles and {left} paces")
        """,
    ),

)
