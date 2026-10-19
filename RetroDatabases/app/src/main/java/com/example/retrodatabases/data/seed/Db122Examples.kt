package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 820: the volumes of the Konin kyakushiki, edicts and regulations, counted in code. */
val db122Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.EXAMPLE,
        section = "Japan",
        title = "The fifty volumes of the Konin kyakushiki, edicts and regulations, counted in code",
        originalTitle = "Konin kyakushiki",
        author = "Compilers at the Heian court",
        year = 820,
        summary = "The Konin kyakushiki is usually given as ten volumes of edicts and forty of regulations, and a short program counts its volumes and the years of law it collects.",
        details = "The compilation was begun about 803 and completed in 820, and it collects the laws issued from 701 to 819, so the program adds the volumes and counts those years inclusively. The volume counts are the usual figures for the work, and the year 830, when the compilation came into force, is not used in the sums.",
        instructions = """
            1. Save the example as konin_volumes.py.
            2. Run it: python3 konin_volumes.py
        """,
        example = """
            EDICT_VOLUMES = 10       # the kyaku, the collected edicts, as commonly given
            REGULATION_VOLUMES = 40  # the shiki, the implementing regulations, as commonly given
            FIRST_YEAR = 701         # the year where the collected laws begin
            LAST_YEAR = 819          # the last year of the laws collected, as commonly given
            total = EDICT_VOLUMES + REGULATION_VOLUMES
            print("volumes in all:", total)
            print("share of volumes that are regulations:", f"{REGULATION_VOLUMES / total:.0%}")
            print("years of law collected, counted inclusively:", LAST_YEAR - FIRST_YEAR + 1)
        """,
    ),

)
