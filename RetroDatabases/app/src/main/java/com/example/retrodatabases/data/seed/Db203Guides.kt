package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 10 to 20: how Greek numerals counted in myriads give the census total of 14, written out as a short program. */
val db203Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.GUIDE,
        section = "Numerals",
        title = "How Greek numerals counted in myriads give the census total of 14",
        originalTitle = "Myriad numerals",
        author = "The Greek version of the Res Gestae",
        year = 14,
        summary = "Greek numerals count in myriads of ten thousand, and a short program reads the Greek total of the census of 14 as 493 myriads and 7,000, which is 4,937,000.",
        details = "A myriad is ten thousand, so a total in the millions is written as a count of myriads with the rest added on. The Greek numerals of chapter 8 come to 493 myriads and 7,000, and the example turns them back into the census total so that the sum can be checked by hand.",
        instructions = """
            1. Save the example as myriads.py.
            2. Run it: python3 myriads.py
        """,
        example = """
            MYRIAD = 10000
            myriads = 493        # Greek count of myriads in the census total of 14
            rest = 7000          # the remainder written after the myriads
            total = myriads * MYRIAD + rest
            print("myriads:", myriads)
            print("rest:", rest)
            print("total:", total)
        """,
        rare = true,
    ),

)
