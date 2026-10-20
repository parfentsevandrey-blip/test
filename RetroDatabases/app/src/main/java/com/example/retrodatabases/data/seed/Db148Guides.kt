package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 560 to 570: how a post-consular year counted from the consulship of Basilius in 541 becomes a calendar year. */
val db148Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.GUIDE,
        section = "Byzantine",
        title = "How a post-consular year counted from the consulship of Basilius in 541 becomes a calendar year, under the two counts in use",
        originalTitle = "post consulatum Basilii",
        author = "Byzantine documentary scribes",
        year = 561,
        summary = "After the last consulship of Basilius in 541, documents counted years from it, and a short program turns a post-consular year into a calendar year under either count.",
        details = "The count after 541 could start with 541 or with 542 as the first year, and papyri of the period use both, so one post-consular year can fall in two calendar years. A papyrus giving post-consular year 21 falls in early 561 on one count and in 562 on the other, and 561 is the year used here.",
        rare = true,
        instructions = """
            1. Save the example as post_consular.py.
            2. Run it: python3 post_consular.py
        """,
        example = """
            # Post-consular year 21 under the two counts used in the papyri after 541.
            POST_CONSULAR = 21   # the post-consular year to convert
            FIRST_YEAR_A = 541   # count A: 541 is post-consular year 1
            FIRST_YEAR_B = 542   # count B: 542 is post-consular year 1

            print("count A (541 is year 1):", FIRST_YEAR_A + POST_CONSULAR - 1)
            print("count B (542 is year 1):", FIRST_YEAR_B + POST_CONSULAR - 1)
        """,
    ),

)
