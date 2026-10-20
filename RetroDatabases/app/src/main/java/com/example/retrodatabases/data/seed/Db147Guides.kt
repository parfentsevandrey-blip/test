package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 570 to 580: how an Olympiad and the year within it turn into a date BC or AD, worked out in a short program. */
val db147Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.GUIDE,
        section = "Greek",
        title = "How an Olympiad and its year turn into a date BC or AD, counted from the first Olympiad of 776 BC",
        originalTitle = "Olympiads, four-year blocks counted from 776 BC",
        author = "Greek chronographers in the tradition of Eusebius",
        year = 570,
        summary = "An Olympiad is a block of four years, and a short program turns the Olympiad and the year within it into a date before or after the Christian era.",
        details = "The count starts with the first Olympiad, which begins in 776 BC, and the chronicles of late antiquity used it; the sources do not date its use in the 570s, so 570 is a placed year, and the example converts that year into an Olympiad. The program follows the rule that there is no year zero, so the year before AD 1 is 1 BC.",
        rare = true,
        instructions = """
            1. Save the example as olympiad_date.py.
            2. Run it: python3 olympiad_date.py
        """,
        example = """
            # The first Olympiad begins in 776 BC; each Olympiad holds four years.
            def olympiad_to_era(n, k):
                years_after_776_bc = 4 * (n - 1) + (k - 1)
                year = 776 - years_after_776_bc
                return f"{year} BC" if year >= 1 else f"{1 - year} AD"

            for n, k in ((1, 1), (194, 4), (195, 1), (337, 2)):
                print(f"Olympiad {n}, year {k}:", olympiad_to_era(n, k))
        """,
    ),

)
