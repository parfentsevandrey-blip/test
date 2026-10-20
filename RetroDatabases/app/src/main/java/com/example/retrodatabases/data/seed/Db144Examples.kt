package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 600 to 610: the years from the Sui examination of 605 to its abolition in 1905, counted in code. */
val db144Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.EXAMPLE,
        section = "Chinese",
        title = "The Sui examination of 605 and its abolition in 1905, a span of 1300 years counted in code",
        originalTitle = "keju",
        author = "Sui court under Emperor Yang",
        year = 605,
        summary = "Counted by its usual dates, the Chinese examination system that began under the Sui in 605 ended in 1905, a span of 1300 years.",
        details = "Accounts of its start differ, with 605 under Emperor Yang the usual date, while some accounts give 587 under Emperor Wen and others 606, so the span uses 605. The examinations were held in some years only and were interrupted for long periods, so the figure counts the years between two dates and not the years of examinations.",
        instructions = """
            1. Save the example as keju_span.py.
            2. Run it: python3 keju_span.py
        """,
        example = """
            START = 605  # the Sui examination, as usually dated
            END = 1905   # the examination system abolished in 1905
            print("years from the usual start to the abolition:", END - START)
        """,
    ),

)
