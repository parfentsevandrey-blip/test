package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 410 to 418: the years from the sack of Rome to the death of the emperor Honorius, in one line of arithmetic. */
val db163Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the sack of Rome to the death of the emperor Honorius",
        originalTitle = "Years between two dated events",
        author = "Alaric and the emperor Honorius",
        year = 410,
        summary = "The sack of Rome in 410 and the death of the western emperor Honorius in 423 are thirteen years apart, and a one-line program counts them.",
        details = "The sack began in August 410 under Alaric, and Honorius died at Ravenna on 15 August 423. The example counts whole calendar years only, so the exact span in days is left to the record on the days between the same two dates.",
        instructions = """
            1. Save the example as sack_honorius_years.py.
            2. Run it: python3 sack_honorius_years.py
        """,
        example = """
            print("years from the sack of Rome to the death of the emperor Honorius:", 423 - 410)
        """,
    ),

)
