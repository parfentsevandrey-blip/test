package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1245: the days between the three public sittings of the First Council of Lyon. */
val db80Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.EXAMPLE,
        section = "Councils",
        title = "Days between the public sittings of the First Council of Lyon",
        originalTitle = "Public sittings",
        author = "The fathers of the First Council of Lyon",
        year = 1245,
        summary = "The First Council of Lyon held its public sittings on 28 June, 5 July and 17 July 1245, and a short program counts the days between them.",
        details = "The dates are the public sittings as commonly given, and the Python calendar gives each gap so that it can be checked by hand. The total of 19 days runs from the first sitting to the last, the sitting at which the deposition of Frederick II was pronounced.",
        instructions = """
            1. Save the example as sittings.py.
            2. Run it: python3 sittings.py
        """,
        example = """
            from datetime import date

            sittings = [date(1245, 6, 28), date(1245, 7, 5), date(1245, 7, 17)]   # public sittings, as commonly given
            print("from 28 June to 5 July:", (sittings[1] - sittings[0]).days, "days")
            print("from 5 July to 17 July:", (sittings[2] - sittings[1]).days, "days")
            print("first to last sitting:", (sittings[2] - sittings[0]).days, "days")
        """,
    ),

)
