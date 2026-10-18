package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1071 to 1077: day counts between a battle, a papal election and a penance. */
val db97Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.FLEX,
        section = "Battles and elections",
        title = "Days from the Battle of Manzikert to the election of Gregory VII",
        originalTitle = "Days from the Battle of Manzikert to the election of Gregory VII",
        author = "Romanos IV Diogenes and Gregory VII",
        year = 1071,
        summary = "The count runs from the Battle of Manzikert, fought on 26 August 1071, to the election of Gregory VII on 22 April 1073, and comes to 605 days.",
        details = "At Manzikert the army of Alp Arslan defeated the Byzantine army of Romanos IV Diogenes, and the count crosses the leap day of 29 February 1072. Both dates are given in the Julian calendar, and no century year falls between them, so the count is the same in the Gregorian calendar.",
        instructions = """
            1. Save the example as manzikert_gregory.py.
            2. Run it: python3 manzikert_gregory.py
        """,
        example = """
            from datetime import date

            battle = date(1071, 8, 26)     # Manzikert, as commonly given (Julian)
            election = date(1073, 4, 22)   # election of Gregory VII, as commonly given (Julian)
            # no century year lies between the two dates, so the day count is the same in both calendars
            print("days from Manzikert to the election of Gregory VII:", (election - battle).days)
        """,
    ),

    entry(
        databaseId = 97,
        kind = Kind.FLEX,
        section = "Popes and emperors",
        title = "Days from Gregory VII's election to Henry IV's penance at Canossa",
        originalTitle = "Days from Gregory VII's election to Henry IV's penance at Canossa",
        author = "Gregory VII and Henry IV",
        year = 1073,
        summary = "The count runs from the election of Gregory VII on 22 April 1073 to the start of Henry IV's penance at Canossa on 25 January 1077, and comes to 1374 days.",
        details = "The span includes the leap day of 1076, and both dates are given in the Julian calendar. No century year falls within it, so the count is the same in the Gregorian calendar.",
        instructions = """
            1. Save the example as gregory_canossa.py.
            2. Run it: python3 gregory_canossa.py
        """,
        example = """
            from datetime import date

            election = date(1073, 4, 22)   # election of Gregory VII, as commonly given (Julian)
            penance = date(1077, 1, 25)    # start of the penance at Canossa, as commonly given (Julian)
            # no century year lies between the two dates, so the day count is the same in both calendars
            print("days from the election of Gregory VII to Canossa:", (penance - election).days)
        """,
    ),

)
