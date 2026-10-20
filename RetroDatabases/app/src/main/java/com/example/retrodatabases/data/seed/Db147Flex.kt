package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One flex piece of 570 to 580: the years between Burzoe's Middle Persian version of the fables and Ibn al-Muqaffa's Arabic version. */
val db147Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from Burzoe's Pahlavi version of about 570 to Ibn al-Muqaffa's Arabic version of about 750",
        originalTitle = "Years between two versions of the Panchatantra fables",
        author = "Burzoe and Ibn al-Muqaffa",
        year = 570,
        summary = "Burzoe's Middle Persian version of about 570 and Ibn al-Muqaffa's Arabic version of about 750 are 180 years apart, and a short program prints the gap.",
        details = "Both dates are approximate, so the 180 years is a rough span between two versions of one text and not a measured interval. The program only subtracts the two dates, which a reader can check against the two entries.",
        rare = true,
        instructions = """
            1. Save the example as burzoe_kalila.py.
            2. Run it: python3 burzoe_kalila.py
        """,
        example = """
            PERSIAN = 570  # Burzoe's Middle Persian version, placed inside the reign of Khosrow I
            ARABIC = 750   # Ibn al-Muqaffa's Arabic version, put at about this date
            print("years from the Persian version to the Arabic one:", ARABIC - PERSIAN)
        """,
    ),

)
