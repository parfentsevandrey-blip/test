package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 310 to 320: the days from the battle of the Milvian Bridge to the posting of the Milan letter. */
val db173Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of the Milvian Bridge to the Milan letter at Nicomedia",
        originalTitle = "Days between two dated events",
        author = "Constantine and Licinius",
        year = 313,
        summary = "The battle of the Milvian Bridge was fought on 28 October 312 and the Milan letter was posted at Nicomedia on 13 June 313, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar, which gives the same day count as the Julian calendar for these two dates because no century year falls between them. The meeting that preceded the letter is disputed, so the count uses the date of posting only.",
        instructions = """
            1. Save the example as milvian_milan.py.
            2. Run it: python3 milvian_milan.py
        """,
        example = """
            from datetime import date

            milvian = date(312, 10, 28)   # the battle of the Milvian Bridge, as commonly given
            milan = date(313, 6, 13)      # the Milan letter posted at Nicomedia, as commonly given
            print("days from the Milvian Bridge to the Milan letter:", (milan - milvian).days)
        """,
    ),

)
