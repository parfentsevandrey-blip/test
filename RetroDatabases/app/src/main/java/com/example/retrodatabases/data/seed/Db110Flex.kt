package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line of arithmetic each: the years from the Old Book of Tang to the New Book of Tang, and from the Buyid entry into Baghdad to the Khitan takeover of Kaifeng. */
val db110Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Old Book of Tang to the New Book of Tang",
        originalTitle = "Years from the Old Book of Tang to the New Book of Tang",
        author = "Liu Xu and Ouyang Xiu",
        year = 945,
        summary = "A one-line calculation counts the 115 years from the presentation of the Old Book of Tang in 945 to the New Book of Tang of 1060.",
        details = "The Old Book of Tang is dated 945 and the New Book of Tang 1060, as commonly given, and the calculation counts 115 years between the two. The run tests the arithmetic rather than the books.",
        instructions = """
            1. Save the example as tang_years.py.
            2. Run it: python3 tang_years.py
        """,
        example = """
            old_presented = 945
            new_presented = 1060
            print("years from the Old Book of Tang to the New Book of Tang:", new_presented - old_presented)
        """,
    ),

    entry(
        databaseId = 110,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Buyid entry into Baghdad to the Khitan takeover of Kaifeng",
        originalTitle = "Years from the Buyid entry into Baghdad to the Khitan takeover of Kaifeng",
        author = "Ahmad ibn Buya and Yelu Deguang",
        year = 947,
        summary = "A one-line calculation counts the two years from the Buyid entry into Baghdad in 945 to the Khitan takeover of Kaifeng in 947.",
        details = "Both dates are given as commonly stated, and the calculation counts 2 years between them, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as buyid_kaifeng.py.
            2. Run it: python3 buyid_kaifeng.py
        """,
        example = """
            buyid_baghdad = 945
            khitan_kaifeng = 947
            print("years from the Buyid entry into Baghdad to the Khitan takeover of Kaifeng:", khitan_kaifeng - buyid_baghdad)
        """,
    ),

)
