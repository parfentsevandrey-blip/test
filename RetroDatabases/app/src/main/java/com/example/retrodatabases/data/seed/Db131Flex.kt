package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex piece of 730 to 740: the years from the Izumo fudoki of 733 to the Kaiyuan Code of 737, counted by one line of arithmetic. */
val db131Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Izumo fudoki of 733 to the Kaiyuan Code of 737",
        originalTitle = "Years between two provincial and imperial texts",
        author = "Izumo provincial officials",
        year = 733,
        summary = "The Izumo fudoki came in 733 and the Kaiyuan Code in 737, and a short program counts the four years between the two dates.",
        details = "The two texts had no connection, one a provincial gazetteer of Japan and the other a penal code of the Tang, so the span only compares their dates. The program uses the usual dates of the two works, 733 for the gazetteer and 737 for the code.",
        instructions = """
            1. Save the example as izumo_kaiyuan.py.
            2. Run it: python3 izumo_kaiyuan.py
        """,
        example = """
            IZUMO_FUDOKI = 733   # the Izumo gazetteer presented to the court, as commonly dated
            KAIYUAN_CODE = 737   # the Kaiyuan Code, completed in 737
            print("years from the Izumo fudoki to the Kaiyuan Code:", KAIYUAN_CODE - IZUMO_FUDOKI)
        """,
    ),

)
