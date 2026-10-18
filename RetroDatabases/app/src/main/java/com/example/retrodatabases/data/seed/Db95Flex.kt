package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1092 and 1095: days between the murder of Nizam al-Mulk and the death of Malik-Shah, and between the death of Ladislas I and Urban II's call at Clermont. */
val db95Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the murder of Nizam al-Mulk to the death of Malik-Shah",
        originalTitle = "Days between two fixed dates",
        author = "Nizam al-Mulk and Malik-Shah I",
        year = 1092,
        summary = "Nizam al-Mulk was murdered on 14 October 1092 and Malik-Shah I died on 19 November 1092, and a short program counts the 36 days between the two dates.",
        details = "Both dates are taken as commonly stated, in one calendar, and the span stays inside one year. The count tests date arithmetic only and says nothing about the causes of either death.",
        instructions = """
            1. Save the example as nizam_malikshah.py.
            2. Run it: python3 nizam_malikshah.py
        """,
        example = """
            from datetime import date

            murder = date(1092, 10, 14)    # Nizam al-Mulk's murder, as commonly given
            death = date(1092, 11, 19)     # Malik-Shah's death, as commonly given
            print("days from the murder to the death:", (death - murder).days)
        """,
    ),

    entry(
        databaseId = 95,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Ladislas I of Hungary to Urban II's call at Clermont",
        originalTitle = "Days between two fixed dates",
        author = "Ladislas I of Hungary and Urban II",
        year = 1095,
        summary = "Ladislas I of Hungary died on 29 July 1095 and Urban II made his call at Clermont on 27 November 1095, and a short program counts the 121 days between the two dates.",
        details = "Both dates are taken as commonly stated, in one calendar, and the Clermont date is the usual day of the call rather than the opening of the council. The span stays inside one year, so the count tests date arithmetic only.",
        rare = true,
        instructions = """
            1. Save the example as ladislas_clermont.py.
            2. Run it: python3 ladislas_clermont.py
        """,
        example = """
            from datetime import date

            ladislas = date(1095, 7, 29)     # Ladislas I's death, as commonly given
            clermont = date(1095, 11, 27)    # Urban II's call at Clermont, as commonly given
            print("days from Ladislas's death to the call at Clermont:", (clermont - ladislas).days)
        """,
    ),

)
