package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 770 to 780: the 1260 days of Revelation, reckoned as forty-two months of thirty days and checked by a short program. */
val db127Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.GUIDE,
        section = "Time reckoning",
        title = "How the 1260 days of Revelation are reckoned as forty-two months of thirty days, checked by a program",
        originalTitle = "diebus mille ducentis sexaginta",
        author = "John of Patmos, the traditional author of Revelation",
        year = 776,
        summary = "The 1260 days of Revelation are the same as forty-two months of thirty days and three and a half years of 360 days, and a short program checks the three figures.",
        details = "The reckoning is much older than the commentary, so 776 is the year of the anchor, not of the reckoning itself. The period is given in Revelation 11 and 12, and as a time, and times, and half a time in Revelation 12, with a similar phrase in Daniel 7; Beatus of Liebana's commentary of 776 works through those chapters.",
        rare = true,
        instructions = """
            1. Save the example as apocalypse_days.py.
            2. Run it: python3 apocalypse_days.py
        """,
        example = """
            DAYS = 1260     # the period of Revelation 11:3 and 12:6, in days
            MONTH = 30      # the reckoning uses months of thirty days
            YEAR = 360      # and years of twelve such months
            months = DAYS // MONTH
            years = DAYS / YEAR
            print("1260 days as months of 30 days:", months)
            print("1260 days as years of 360 days:", years)
            print("forty-two months make 3.5 years:", months == 42 and years == 3.5)
        """,
    ),

)
