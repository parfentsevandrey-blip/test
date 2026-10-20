package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 100 to 110: the years from the start of Trajan's bridge to Cai Lun's paper report, counted in one line of code. */
val db194Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the start of Trajan's bridge to Cai Lun's paper report",
        originalTitle = "Years between two dated events",
        author = "Apollodorus of Damascus and Cai Lun",
        year = 105,
        summary = "Trajan's bridge work is dated from 103 and Cai Lun's paper was reported in 105, and a short program counts the two years between them.",
        details = "The bridge work is usually given as running from 103 to 105, and the paper report is dated to 105, so the count is 2 by these dates. Accounts differ on the start, since one account puts Trajan's order at 104, and the count would be 1 if that date were taken as the start.",
        instructions = """
            1. Save the example as bridge_paper.py.
            2. Run it: python3 bridge_paper.py
        """,
        example = """
            bridge_start = 103   # the start of the work, as commonly given
            paper_report = 105   # Cai Lun's report to the court
            print("years from the start of the bridge to the paper report:", paper_report - bridge_start)
        """,
        rare = true,
    ),

)
