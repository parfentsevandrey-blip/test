package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 500 to 510: the years from Liu Xie's Wenxin diaolong of about 501 to the Philoxenian New Testament of 508. */
val db154Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Liu Xie's Wenxin diaolong of 501 to the Philoxenian New Testament of 508",
        originalTitle = "Wenxin diaolong",
        author = "Liu Xie and Polycarp",
        year = 501,
        summary = "The Wenxin diaolong of about 501 and the Philoxenian Syriac New Testament of 508 are seven years apart, and a short program prints the gap.",
        details = "The Wenxin date is approximate, and the Philoxenian date is disputed, with about 505 in one account and 507 or 508 in another, so the gap is only as firm as those dates. The program subtracts the two years and prints the result.",
        instructions = """
            1. Save the example as wenxin_philoxenian.py.
            2. Run it: python3 wenxin_philoxenian.py
        """,
        example = """
            WENXIN = 501       # Liu Xie's Wenxin diaolong, about 501
            PHILOXENIAN = 508  # the Philoxenian Syriac New Testament, dated 508 in this record
            print("years from the Wenxin diaolong of 501 to the Philoxenian New Testament of 508:", PHILOXENIAN - WENXIN)
        """,
    ),

)
