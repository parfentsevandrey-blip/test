package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 690 to 700: the years from the first palace examination of 690 to the Synod of Birr, and from the Quinisext Council of 692 to the Umayyad dinar of 696. */
val db135Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the first palace examination of 690 to the Synod of Birr of 697",
        originalTitle = "Years between two dated acts",
        author = "Empress Wu Zetian and Adomnan of Iona",
        year = 690,
        summary = "The first palace examination of 690 and the Cain issued at Birr in 697 are seven years apart, and a short program prints the gap.",
        details = "The two acts come from opposite ends of Eurasia, so the span is only arithmetic on the dates given for them. The palace examination is dated to 690 and the Cain to the year of its synod.",
        instructions = """
            1. Save the example as exam_birr.py.
            2. Run it: python3 exam_birr.py
        """,
        example = """
            PALACE_EXAM = 690  # the first palace examination, as commonly dated
            BIRR = 697         # the Cain issued at the Synod of Birr
            print("years from the palace examination of 690 to the Synod of Birr:", BIRR - PALACE_EXAM)
        """,
    ),

    entry(
        databaseId = 135,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Quinisext Council of 692 to the Umayyad gold dinar of 696",
        originalTitle = "Years between a council and a coinage",
        author = "Justinian II and Abd al-Malik ibn Marwan",
        year = 692,
        summary = "The Quinisext Council of 692 and the Umayyad gold dinar of 696 are four years apart, and a short program prints the gap.",
        details = "One is a church council in Constantinople and the other a coinage reform in Damascus, so the span is simple arithmetic on the two dates given for them. The program prints the difference, which a reader can check against the two dates.",
        instructions = """
            1. Save the example as council_dinar.py.
            2. Run it: python3 council_dinar.py
        """,
        example = """
            COUNCIL = 692  # the Quinisext Council, as commonly dated
            DINAR = 696    # the first Arabic-only dinars, as commonly dated
            print("years from the Quinisext Council of 692 to the Umayyad dinar of 696:", DINAR - COUNCIL)
        """,
    ),

)
