package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 210 to 220: how a Roman iugerum of land is turned into square feet and square metres, placed at 216 for the start of the Parthian war. */
val db183Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.GUIDE,
        section = "Measures",
        title = "How an iugerum of Roman land, 240 by 120 feet, is turned into square metres",
        originalTitle = "Iugerum",
        author = "Roman land surveyors",
        year = 216,
        summary = "A Roman iugerum was a plot of 240 by 120 Roman feet, and a short program turns its size into square feet and square metres.",
        details = "The sources do not date this land unit to the decade; 216 is the year of the anchor, the start of Caracalla's Parthian war, not the date of the unit. The program uses a Roman foot of about 29.6 centimetres, a standard figure, and prints the area in square feet and in square metres, rounded to one decimal place.",
        instructions = """
            1. Save the example as iugerum.py.
            2. Run it: python3 iugerum.py
        """,
        example = """
            FOOT_MM = 296                   # a Roman foot of about 29.6 centimetres, as a standard figure
            LENGTH_FEET = 240               # one iugerum is 240 Roman feet long
            WIDTH_FEET = 120                # and 120 Roman feet wide
            square_feet = LENGTH_FEET * WIDTH_FEET
            square_mm = square_feet * FOOT_MM * FOOT_MM
            print("square feet in one iugerum:", square_feet)
            print("square metres in one iugerum:", round(square_mm / 1_000_000, 1))
        """,
        rare = true,
    ),

)
