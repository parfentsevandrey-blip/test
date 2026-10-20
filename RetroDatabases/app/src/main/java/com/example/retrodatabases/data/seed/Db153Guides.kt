package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 510 to 520: how the Gupta year 191 on the Eran pillar becomes the Common Era year 510, worked out by a short program. */
val db153Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.GUIDE,
        section = "Indian",
        title = "How the Gupta year 191 on the Eran pillar becomes the Common Era year 510, worked out in code",
        originalTitle = "Gupta samvat",
        author = "Scribes of the Eran pillar inscription",
        year = 510,
        summary = "The Eran pillar inscription is dated Gupta era year 191, and adding the usual offset of 319 turns it into the Common Era year 510.",
        details = "The Gupta era is usually converted by adding 319, though some scholars add 320, so the Eran date falls in 510 or 511 depending on the offset. The program uses the usual offset of 319 and prints the other reading beside it, because the starting year of the era is itself disputed.",
        rare = true,
        instructions = """
            1. Save the example as gupta_eran.py.
            2. Run it: python3 gupta_eran.py
        """,
        example = """
            GUPTA_OFFSET = 319     # added to a Gupta era year, the usual offset
            ERAN_GUPTA_YEAR = 191  # the Gupta year written on the Eran pillar
            print("Gupta year", ERAN_GUPTA_YEAR, "is Common Era year", ERAN_GUPTA_YEAR + GUPTA_OFFSET)
            print("with the other offset, 320:", ERAN_GUPTA_YEAR + 320)
        """,
    ),

)
