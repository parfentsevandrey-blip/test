package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 840 to 850: the six years of the Huichang era counted in code, and the years covered by the Nihon Koki. */
val db120Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.EXAMPLE,
        section = "China",
        title = "The six years of the Huichang era, 841 to 846, and the 845 edict in its fifth year",
        originalTitle = "Huichang era",
        author = "Emperor Wuzong of Tang",
        year = 841,
        summary = "The Huichang era ran from 841 to 846, six years in all, and the edict against Buddhist monasteries of 845 fell in its fifth year, a count that a short program prints.",
        details = "Huichang was the era name of Emperor Wuzong, and the edict of 845 belongs to the campaign against Buddhist monasteries. The example counts the years of the era inclusively, finds the position of the edict and counts the years left after it.",
        instructions = """
            1. Save the example as huichang_years.py.
            2. Run it: python3 huichang_years.py
        """,
        example = """
            ERA_FIRST = 841   # first year of the Huichang era
            ERA_LAST = 846    # last year of the Huichang era
            EDICT = 845       # the edict against Buddhist monasteries
            print("years in the era:", ERA_LAST - ERA_FIRST + 1)
            print("the edict fell in year", EDICT - ERA_FIRST + 1, "of the era")
            print("years from the edict to the end of the era:", ERA_LAST - EDICT)
        """,
    ),

    entry(
        databaseId = 120,
        kind = Kind.EXAMPLE,
        section = "Japanese",
        title = "The years the Nihon Koki covers, from 792 to 833, counted in code",
        originalTitle = "Nihon Koki",
        author = "Court historians of the Heian period",
        year = 840,
        summary = "The Nihon Koki, completed in 840, covers the years 792 to 833, a span of 41 years that a short program counts.",
        details = "The completion year 840 and the span of the history are the usual dates, and the example prints the span both as a difference and inclusively. It also prints the years between the last year covered and the completion.",
        instructions = """
            1. Save the example as nihon_koki_years.py.
            2. Run it: python3 nihon_koki_years.py
        """,
        example = """
            FIRST_YEAR = 792   # first year covered by the Nihon Koki
            LAST_YEAR = 833    # last year covered
            COMPLETED = 840    # year the work was completed
            print("years covered:", LAST_YEAR - FIRST_YEAR)
            print("years counted inclusively:", LAST_YEAR - FIRST_YEAR + 1)
            print("years from the last year covered to the completion:", COMPLETED - LAST_YEAR)
        """,
    ),

)
