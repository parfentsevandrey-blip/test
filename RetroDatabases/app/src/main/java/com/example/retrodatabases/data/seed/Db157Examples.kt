package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 470 to 480: the years that share the Inariyama sword's cycle label, counted in code. */
val db157Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.EXAMPLE,
        section = "Cycles",
        title = "The years of 400 to 600 that share the Inariyama sword's cycle label",
        originalTitle = "The years of 400 to 600 that share the Inariyama sword's cycle label",
        author = "No single author",
        year = 471,
        summary = "The Inariyama sword's date label repeats every sixty years, and a short program lists the years between 400 and 600 that carry the same label as 471.",
        details = "Most Japanese scholars read the sword's year as 471, and the same label comes round again at 531, the other date proposed for the sword. The program counts the repeats inside two centuries, so the dispute comes down to a choice between two years that are sixty apart.",
        instructions = """
            1. Save the example as label_cycle.py.
            2. Run it: python3 label_cycle.py
        """,
        example = """
            # The Inariyama sword's date is written with a cycle label, and the label repeats every sixty years.
            LABEL_YEAR = 471
            CYCLE = 60
            same_label = [year for year in range(400, 601) if (year - LABEL_YEAR) % CYCLE == 0]
            print("years with the same label, 400 to 600:", same_label)
            print("the two candidate dates 471 and 531 are apart by", 531 - 471, "years")
        """,
        rare = true,
    ),

)
