package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 520 to 530: how a year counted from the Incarnation becomes a year of Diocletian, the count of Dionysius's table of 525. */
val db152Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How a year counted from the Incarnation becomes a year of Diocletian by subtracting 284",
        originalTitle = "Anno Diocletiani",
        author = "Dionysius Exiguus",
        year = 525,
        summary = "A year counted from the Incarnation becomes a year of Diocletian's era by subtracting 284, which turns AD 532, the first year of Dionysius's table, into year 248.",
        details = "Diocletian's era begins in 284, so an Incarnation year minus 284 gives the matching year of Diocletian, and AD 532 gives 248. The program applies the rule to 520, 525, 530 and 532, the first year of the table among them.",
        rare = true,
        instructions = """
            1. Save the example as diocletian_year.py.
            2. Run it: python3 diocletian_year.py
        """,
        example = """
            def diocletian_year(ad_year):
                return ad_year - 284


            for year in [520, 525, 530, 532]:
                print(f"AD {year} is year {diocletian_year(year)} of Diocletian")
        """,
    ),

)
