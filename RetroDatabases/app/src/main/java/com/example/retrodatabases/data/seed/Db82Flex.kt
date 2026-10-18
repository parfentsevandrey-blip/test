package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1220 to 1228: days between dated events of the decade, from Frederick II's crowning to the death of Genghis Khan. */
val db82Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Kalka to the death of Genghis Khan",
        originalTitle = "Days between two events",
        author = "Mongol and Russian commanders",
        year = 1227,
        summary = "The battle of Kalka was fought on 31 May 1223 and Genghis Khan died on 18 August 1227, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar, which has the same leap years as the Julian reckoning for these years, so the count is the same in either reckoning. Accounts differ on the exact day of Genghis Khan's death in August 1227, so the count is only as firm as the date chosen.",
        instructions = """
            1. Save the example as kalka_genghis.py.
            2. Run it: python3 kalka_genghis.py
        """,
        example = """
            from datetime import date

            kalka = date(1223, 5, 31)      # battle of Kalka, as commonly given
            genghis = date(1227, 8, 18)    # death of Genghis Khan, as commonly given
            print("days from Kalka to the death of Genghis Khan:", (genghis - kalka).days)
        """,
    ),

    entry(
        databaseId = 82,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the Franciscan Rule to the canonization of Francis",
        originalTitle = "Days between two acts of the papacy",
        author = "Popes Honorius III and Gregory IX",
        year = 1228,
        summary = "Pope Honorius III approved the Franciscan Rule on 29 November 1223 and Gregory IX canonized Francis on 16 July 1228, and a short program counts the days between the two.",
        details = "The span includes two leap days, 29 February 1224 and 29 February 1228, and the example counts it in the same reckoning for both dates. The count is 1691 days, and both dates are given as commonly stated.",
        instructions = """
            1. Save the example as rule_canonization.py.
            2. Run it: python3 rule_canonization.py
        """,
        example = """
            from datetime import date

            rule = date(1223, 11, 29)        # Franciscan Rule approved, as commonly given
            canonized = date(1228, 7, 16)    # canonization of Francis, as commonly given
            print("days from the Rule to the canonization:", (canonized - rule).days)
        """,
    ),

    entry(
        databaseId = 82,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Frederick II's crowning to his excommunication",
        originalTitle = "Days between a coronation and an excommunication",
        author = "Frederick II and Gregory IX",
        year = 1227,
        summary = "Frederick II was crowned emperor at Rome on 22 November 1220 and Gregory IX excommunicated him on 29 September 1227, and a short program counts the days between the two dates.",
        details = "The count is 2502 days, and its only leap day is 29 February 1224, since no other February 29 falls before the excommunication. The excommunication followed a delayed crusade, and both dates are given as commonly stated.",
        instructions = """
            1. Save the example as crowning_excommunication.py.
            2. Run it: python3 crowning_excommunication.py
        """,
        example = """
            from datetime import date

            crowning = date(1220, 11, 22)         # coronation as emperor at Rome, as commonly given
            excommunication = date(1227, 9, 29)   # excommunication by Gregory IX, as commonly given
            print("days from the crowning to the excommunication:", (excommunication - crowning).days)
        """,
    ),

)
