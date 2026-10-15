package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1510 to 1517: Julian leap years of the decade and two day counts between dated events of the Lateran era. */
val db53Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Leonardo's death to Magellan's sailing",
        originalTitle = "Leonardo's death and Magellan's sailing",
        author = "Leonardo da Vinci and Ferdinand Magellan",
        year = 1519,
        summary = "Leonardo's death on 2 May 1519 and the sailing of Magellan's fleet on 20 September 1519 are 141 days apart, a count that a short program makes with fixed dates.",
        details = "Leonardo died near Amboise in May 1519, and Magellan's fleet left Sanlucar in September of the same year, with both dates as commonly given. The example uses Python's date library with fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as magellan_days.py.
            2. Run it: python3 magellan_days.py
        """,
        example = """
            from datetime import date

            leonardo_died = date(1519, 5, 2)     # Leonardo's death near Amboise, as commonly dated
            magellan_sailed = date(1519, 9, 20)  # the fleet's departure from Sanlucar, as commonly dated
            print("days from Leonardo's death to the sailing:", (magellan_sailed - leonardo_died).days)
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Lateran bull to Luther's theses",
        originalTitle = "Inter sollicitudines",
        author = "Fifth Lateran Council and Martin Luther",
        year = 1517,
        summary = "The Lateran bull of 4 May 1515 and Luther's theses of 31 October 1517 are 911 days apart, a count that a short program makes with fixed dates.",
        details = "Both dates are given in the Julian style used in Rome and Wittenberg, and the count is the same in both calendars because the leap years of 1515 to 1517 agree. The example uses Python's date library with fixed dates, so the output never changes.",
        instructions = """
            1. Save the example as theses_days.py.
            2. Run it: python3 theses_days.py
        """,
        example = """
            from datetime import date

            bull = date(1515, 5, 4)      # the Lateran bull, as commonly dated
            theses = date(1517, 10, 31)  # Luther's theses, as traditionally dated
            print("days from the bull to the theses:", (theses - bull).days)
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Flodden to Marignano",
        originalTitle = "Flodden and Marignano",
        author = "Commanders of the armies at Flodden and Marignano",
        year = 1513,
        summary = "The battles of Flodden on 9 September 1513 and Marignano on 13 September 1515 are 734 days apart, a count that a short program makes with fixed dates.",
        details = "Both dates are the ones commonly given, and the count uses fixed calendar dates, so the output never changes. Neither 1514 nor 1515 is a leap year, so the interval has no extra February day.",
        instructions = """
            1. Save the example as flodden_days.py.
            2. Run it: python3 flodden_days.py
        """,
        example = """
            from datetime import date

            flodden = date(1513, 9, 9)     # the battle of Flodden, as commonly dated
            marignano = date(1515, 9, 13)  # the battle of Marignano, as commonly dated
            print("days from Flodden to Marignano:", (marignano - flodden).days)
        """,
    ),

)
