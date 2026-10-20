package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 550 to 560: the days from the first to the last sitting of the Second Council of Constantinople in 553, counted in code. */
val db149Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.EXAMPLE,
        section = "Byzantine",
        title = "The 28 days from the first to the last sitting of the Second Council of Constantinople in 553, counted in code",
        originalTitle = "Concilium Constantinopolitanum II",
        author = "Justinian and Patriarch Eutychius",
        year = 553,
        summary = "Counted from its first sitting on 5 May 553 to its last on 2 June 553, the Second Council of Constantinople spanned 28 days, or 29 if both ends are counted.",
        details = "The council that Justinian convened met to condemn the Three Chapters, the writings of three earlier churchmen, and it issued fourteen anathemas against them and fifteen against Origen and Evagrius. Pope Vigilius refused to attend and only later joined the condemnation, and accounts differ on how many bishops took part. The program measures the span between the two dates and does not count how many sittings were held.",
        rare = true,
        instructions = """
            1. Save the example as council_days.py.
            2. Run it: python3 council_days.py
        """,
        example = """
            from datetime import date

            FIRST = date(553, 5, 5)  # the first sitting, as the usual accounts give it
            LAST = date(553, 6, 2)   # the last sitting, as the usual accounts give it
            days = (LAST - FIRST).days
            print("days from 5 May to 2 June 553:", days)
            print("days counted inclusive of both ends:", days + 1)
        """,
    ),

)
