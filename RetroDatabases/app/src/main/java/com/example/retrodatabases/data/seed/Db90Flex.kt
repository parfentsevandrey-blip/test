package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1140 to 1150: days from a papal crusade bull to the fall of Lisbon, days from a papal death to the siege of Damascus, and years between a treaty of 1143 and a conquest of 1147. */
val db90Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the crusade bull Quantum praedecessores to the fall of Lisbon",
        originalTitle = "Days between two dates",
        author = "Eugenius III and the crusaders of Lisbon",
        year = 1145,
        summary = "The bull Quantum praedecessores was issued on 1 December 1145 and Lisbon fell on 24 October 1147, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar, so the count is exact for the dates as written. The span covers the year 1146, which was not a leap year.",
        instructions = """
            1. Save the example as lisbon_bull.py.
            2. Run it: python3 lisbon_bull.py
        """,
        example = """
            from datetime import date

            bull = date(1145, 12, 1)     # Quantum praedecessores, as commonly given
            lisbon = date(1147, 10, 24)  # the fall of Lisbon, as commonly given
            print("days from the bull to the fall of Lisbon:", (lisbon - bull).days)
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Innocent II to the siege of Damascus",
        originalTitle = "Days between two dates",
        author = "Innocent II and the crusaders of Damascus",
        year = 1143,
        summary = "Pope Innocent II died on 24 September 1143 and the siege of Damascus began on 24 July 1148, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar so the count stays the same on every run. The span crosses the leap day of February 1144 and that of February 1148.",
        instructions = """
            1. Save the example as innocent_damascus.py.
            2. Run it: python3 innocent_damascus.py
        """,
        example = """
            from datetime import date

            innocent = date(1143, 9, 24)   # the death of Innocent II, as commonly given
            damascus = date(1148, 7, 24)   # the start of the siege of Damascus, as commonly given
            print("days from Innocent II to the siege of Damascus:", (damascus - innocent).days)
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.FLEX,
        section = "Sums",
        title = "Years from the Treaty of Zamora to the taking of Marrakesh",
        originalTitle = "Years between a treaty and a conquest",
        author = "Afonso Henriques, Alfonso VII and Abd al-Mu'min",
        year = 1143,
        summary = "The Treaty of Zamora was agreed in 1143 and Marrakesh was taken in 1147, and a short program counts the four years between them.",
        details = "The two dates are the usual years for the treaty and the Almohad conquest. The count is a simple difference of years, so it gives the span between the two events rather than the length of any reign.",
        instructions = """
            1. Save the example as zamora_marrakesh.py.
            2. Run it: python3 zamora_marrakesh.py
        """,
        example = """
            treaty = 1143     # the Treaty of Zamora, as commonly dated
            marrakesh = 1147  # the taking of Marrakesh, as commonly dated
            print("years from Zamora to Marrakesh:", marrakesh - treaty)
        """,
    ),

)
