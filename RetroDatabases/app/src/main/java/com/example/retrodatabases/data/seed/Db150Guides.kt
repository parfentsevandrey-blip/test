package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 540 to 550: how a measure of grain in sheng is written as dou and sheng, a decimal rule of the grain measures, worked out by a short program. */
val db150Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How a measure of grain in sheng is written as dou and sheng, the decimal grain measures of the period, worked out by a program",
        originalTitle = "dou and sheng (grain measures)",
        author = "Compilers of the Qimin yaoshu",
        year = 544,
        summary = "Ten sheng make one dou in the grain measures of the period, so a short program writes any amount of grain in sheng as dou and sheng.",
        details = "The Qimin yaoshu of about 544 gives its seed and grain amounts in sheng and dou, but the decimal rule itself is older than the book, so the year 544 is placed at the book's date under the placement rule. The program divides by ten and keeps the remainder, which a reader can check by multiplying the dou by ten and adding the sheng back.",
        rare = true,
        instructions = """
            1. Save the example as dou_sheng.py.
            2. Run it: python3 dou_sheng.py
        """,
        example = """
            # Ten sheng make one dou in the grain measures of the period; the amounts are illustrations.
            for amount in (37, 100, 6):
                dou, sheng = divmod(amount, 10)
                print(amount, "sheng is", dou, "dou", sheng, "sheng")
        """,
    ),

)
