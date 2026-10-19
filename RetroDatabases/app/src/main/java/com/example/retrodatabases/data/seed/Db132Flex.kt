package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 720 to 730: the years from Bede's De temporum ratione of 725 to Yi Xing's Dayan calendar of 729. */
val db132Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Bede's De temporum ratione of 725 to Yi Xing's Dayan calendar of 729",
        originalTitle = "Years between two reckoning books",
        author = "Bede and Yi Xing",
        year = 725,
        summary = "Bede's De temporum ratione of 725 and Yi Xing's Dayan calendar of 729 are four years apart, and a short program prints the difference.",
        details = "The two works are a Latin handbook of time reckoning and a Chinese calendar, and the span only compares their usual dates. The 729 date is the imperial issue of the Dayan calendar, and accounts differ on its completion, so the figure reads the dates one way.",
        instructions = """
            1. Save the example as bede_dayan.py.
            2. Run it: python3 bede_dayan.py
        """,
        example = """
            BEDE = 725   # De temporum ratione, as commonly dated
            DAYAN = 729  # the Dayan calendar's imperial issue, as commonly dated
            print("years from Bede's book to the Dayan calendar:", DAYAN - BEDE)
        """,
    ),

)
