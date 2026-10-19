package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 760 to 770: the years from the founding of Baghdad in 762 to the completion of the Hyakumanto pagodas in 770. */
val db128Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the founding of Baghdad in 762 to the completion of the Hyakumanto pagodas in 770",
        originalTitle = "Years between two 760s works",
        author = "Al-Mansur and Empress Shotoku",
        year = 762,
        summary = "al-Mansur began Baghdad in 762 and the Hyakumanto pagodas were finished around 770, and a short program counts the eight years between the two dates.",
        details = "The span uses the usual dates of the two works, and the completion of the pagodas is dated only to about 770. The program prints the difference between the two years and claims no link between the founding and the pagodas.",
        instructions = """
            1. Save the example as baghdad_pagodas.py.
            2. Run it: python3 baghdad_pagodas.py
        """,
        example = """
            BAGHDAD = 762  # al-Mansur begins the Round City, as commonly dated
            PAGODAS = 770  # the million pagodas are finished, as commonly dated
            print("years from the founding of Baghdad to the completion of the pagodas:", PAGODAS - BAGHDAD)
        """,
    ),

)
