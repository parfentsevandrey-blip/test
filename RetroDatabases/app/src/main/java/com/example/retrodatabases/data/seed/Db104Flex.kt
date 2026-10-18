package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line of arithmetic: the days from the St Brice's Day massacre of 1002 to the death of Pope Sylvester II in 1003. */
val db104Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from St Brice's Day to the death of Sylvester II",
        originalTitle = "Days from St Brice's Day to the death of Sylvester II",
        author = "Aethelred II and Pope Sylvester II",
        year = 1003,
        summary = "A one-line calculation counts the days from the St Brice's Day massacre on 13 November 1002 to the death of Pope Sylvester II on 12 May 1003.",
        details = "The killing of Danes in England on St Brice's Day, 13 November 1002, and the death of Gerbert of Aurillac as Pope Sylvester II on 12 May 1003 are both given as commonly stated. The calculation counts 180 days between them, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as st_brice.py.
            2. Run it: python3 st_brice.py
        """,
        example = """
            from datetime import date

            brice = date(1002, 11, 13)              # St Brice's Day massacre, as commonly stated
            sylvester_death = date(1003, 5, 12)     # death of Pope Sylvester II, as commonly stated
            print("days from St Brice's Day to the death of Sylvester II:", (sylvester_death - brice).days)
        """,
    ),

)
