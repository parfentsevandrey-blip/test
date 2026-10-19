package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 700 to 710: how a pound of gold was cut into 72 solidi, the Byzantine standard, worked out by a program. */
val db134Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.GUIDE,
        section = "Byzantine",
        title = "How a pound of gold was cut into 72 solidi, the Byzantine standard, worked out by a program",
        originalTitle = "solidus",
        author = "The Byzantine imperial mint",
        year = 705,
        summary = "A Roman pound of gold was cut into 72 solidi, the Byzantine gold coin kept at that standard, and a short program works out how many coins a weight of gold makes.",
        details = "The standard of 72 solidi to the pound dates from the 4th century, so 705, the year Justinian II returned to the throne, is only the anchor used here for coins of that reign, not the year the standard was set. Coins of later reigns were not always struck at this weight, so the program uses the standard alone.",
        rare = true,
        instructions = """
            1. Save the example as solidus_pounds.py.
            2. Run it: python3 solidus_pounds.py
        """,
        example = """
            SOLIDI_PER_POUND = 72

            def solidi(pounds):
                return pounds * SOLIDI_PER_POUND

            for pounds in (1, 5, 10):
                print("pounds:", pounds, "solidi:", solidi(pounds))
            print("fraction of a pound per solidus:", f"1/{SOLIDI_PER_POUND}")
        """,
    ),

)
