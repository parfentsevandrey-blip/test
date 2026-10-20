package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 274: the seven days of the Saturnalia, the December feast that ran from 17 to 23 December, counted in code. */
val db177Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.EXAMPLE,
        section = "Festivals",
        title = "The Saturnalia, the December feast from 17 to 23 December, counted in code",
        originalTitle = "Saturnalia",
        author = "Roman households and the state",
        year = 274,
        summary = "The Saturnalia was a December feast that late Roman writers counted as seven days, from 17 to 23 December, and a short program counts the days.",
        details = "The length of the feast changed over the centuries, and accounts differ on the earlier lengths, so the example uses only the late Roman count of seven days. The record is placed at 274, the year Aurelian's festival of 25 December is dated, as an anchor; the sources do not date the feast to that year, so 274 is the year of the anchor and not of the feast.",
        rare = true,
        instructions = """
            1. Save the example as saturnalia.py.
            2. Run it: python3 saturnalia.py
        """,
        example = """
            FIRST_DAY = 17   # the feast begins on 17 December
            LAST_DAY = 23    # and ends on 23 December in the late Roman count
            print("first day:", FIRST_DAY, "December")
            print("last day:", LAST_DAY, "December")
            print("days in the feast:", LAST_DAY - FIRST_DAY + 1)
        """,
    ),

)
