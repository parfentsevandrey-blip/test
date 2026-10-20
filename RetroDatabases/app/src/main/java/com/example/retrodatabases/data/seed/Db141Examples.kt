package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 630 to 640: the 85 chapters of the Book of Sui, completed in 636, counted in code by their parts. */
val db141Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.EXAMPLE,
        section = "Chinese",
        title = "The Book of Sui, completed in 636 with 85 chapters, counted in code by its annals, biographies and treatises",
        originalTitle = "Sui shu",
        author = "Wei Zheng and the other compilers of the Tang court",
        year = 636,
        summary = "The Book of Sui, the official history of the short Sui dynasty, has 85 chapters, and a short program counts them by part.",
        details = "The work is usually divided into five annals of emperors, fifty biographies and thirty treatises, and it is dated to 636. One account says the treatises were the work of a later team and were added in 656, so the figure of 85 is the work as now arranged, while 636 is the date of the main text.",
        rare = true,
        instructions = """
            1. Save the example as sui_chapters.py.
            2. Run it: python3 sui_chapters.py
        """,
        example = """
            ANNALS = 5        # the basic annals of the emperors
            BIOGRAPHIES = 50  # the biographies
            TREATISES = 30    # the treatises on the calendar, astronomy, law and other subjects
            TOTAL = ANNALS + BIOGRAPHIES + TREATISES
            print("chapters in the Book of Sui:", TOTAL)
            print("annals and biographies:", ANNALS + BIOGRAPHIES)
            print("treatises:", TREATISES)
        """,
    ),

)
