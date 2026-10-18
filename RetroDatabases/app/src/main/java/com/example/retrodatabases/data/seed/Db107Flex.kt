package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Two lines of arithmetic: the years from al-Azhar's opening to the Taiping Guangji, and from Zhang Sixun's armillary sphere to Su Song's clock tower. */
val db107Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the opening of al-Azhar in 972 to the Taiping Guangji in 978",
        originalTitle = "Years from the opening of al-Azhar in 972 to the Taiping Guangji in 978",
        author = "Jawhar al-Siqilli and Li Fang",
        year = 978,
        summary = "A one-line calculation counts the six years from the first Friday congregation at al-Azhar in Ramadan 972 to the Taiping Guangji of 978.",
        details = "The Ramadan of 972 and the year 978 are both given as commonly stated, so the run tests the arithmetic rather than the events. The calculation counts 6 years between them.",
        instructions = """
            1. Save the example as azhar_taiping.py.
            2. Run it: python3 azhar_taiping.py
        """,
        example = """
            azhar_opening = 972     # first Friday congregation at al-Azhar in Ramadan 972, as commonly stated
            taiping_guangji = 978   # completion of the Taiping Guangji, as commonly stated
            print("years from the opening of al-Azhar to the Taiping Guangji:", taiping_guangji - azhar_opening)
        """,
    ),

    entry(
        databaseId = 107,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Zhang Sixun's armillary sphere of 977 to Su Song's clock tower of 1092",
        originalTitle = "Years from Zhang Sixun's armillary sphere of 977 to Su Song's clock tower of 1092",
        author = "Zhang Sixun and Su Song",
        year = 977,
        summary = "A one-line calculation counts the 115 years from Zhang Sixun's armillary sphere of 977 to Su Song's clock tower of 1092.",
        details = "Accounts give 977 or 979 for Zhang Sixun's instrument, and the run uses 977, the earlier of the two dates. Su Song's clock tower at Kaifeng is given as completed in 1092, and the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as zhang_su.py.
            2. Run it: python3 zhang_su.py
        """,
        example = """
            zhang_sixun = 977       # earlier of the two dates given for the armillary sphere
            su_song = 1092          # completion of Su Song's clock tower, as commonly stated
            print("years from Zhang Sixun's armillary sphere to Su Song's clock tower:", su_song - zhang_sixun)
        """,
    ),

)
