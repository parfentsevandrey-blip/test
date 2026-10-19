package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 890 to 900: the years from the Ruiju kokushi to Michizane's exile, and the years from the founding of the Zaydi state to its founder's death at Sa'dah. */
val db115Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Ruiju kokushi to Michizane's exile",
        originalTitle = "Years between two dates",
        author = "Sugawara no Michizane",
        year = 892,
        summary = "The Ruiju kokushi was compiled in 892 and Sugawara no Michizane was sent into exile in 901, and a short program counts the nine years between the two dates.",
        details = "Both dates are the usual ones: 892 for the compilation, and 901 for Michizane's removal to Dazaifu in Kyushu, where he died in 903. The example prints the difference and does not claim that the compilation caused the exile.",
        instructions = """
            1. Save the example as michizane_years.py.
            2. Run it: python3 michizane_years.py
        """,
        example = """
            RUIJU = 892    # the compilation of the Ruiju kokushi, as usually given
            EXILE = 901    # Sugawara no Michizane sent to Dazaifu
            print("years from the Ruiju kokushi to Michizane's exile:", EXILE - RUIJU)
        """,
    ),

    entry(
        databaseId = 115,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the founding of the Zaydi state to its founder's death at Sa'dah",
        originalTitle = "Years between two dates",
        author = "Yahya al-Hadi ila al-Haqq",
        year = 897,
        summary = "The Zaydi state was founded in Yemen in 897 and its founder died at Sa'dah in 911, and a short program counts the 14 years between the two dates.",
        details = "Both dates are the ones usually given for Yahya al-Hadi, and the example prints the span between them. The span covers his rule from 897 to 911, and it does not measure how long the imamate lasted after him.",
        instructions = """
            1. Save the example as sadah_years.py.
            2. Run it: python3 sadah_years.py
        """,
        example = """
            FOUNDED = 897    # Yahya al-Hadi begins the Zaydi state in Yemen
            DIED = 911       # his death at Sa'dah
            print("years from the founding to the death at Sa'dah:", DIED - FOUNDED)
        """,
    ),

)
