package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1260 to 1270: day counts between the deaths and crownings of the Mongol and Angevin rulers and the date of Peregrinus's letter. */
val db78Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Conradin's execution to Peter Peregrinus's letter on the lodestone",
        originalTitle = "Days between two dated events",
        author = "Conradin of Swabia and Peter Peregrinus",
        year = 1269,
        summary = "Conradin was executed at Naples on 29 October 1268 and Peregrinus dated his letter on the lodestone on 8 August 1269, and a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the example uses fixed dates in the Python calendar, so the count stays the same on every run. The span crosses one year end, and for two dates this close the count is the same in the Julian calendar of the time, because both dates shift together.",
        instructions = """
            1. Save the example as conradin_peregrinus.py.
            2. Run it: python3 conradin_peregrinus.py
        """,
        example = """
            from datetime import date

            conradin = date(1268, 10, 29)   # Conradin's execution at Naples, as commonly given
            letter = date(1269, 8, 8)       # Peregrinus's letter on the lodestone, as commonly given
            print("days from Conradin's execution to Peregrinus's letter:", (letter - conradin).days)
        """,
    ),

    entry(
        databaseId = 78,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Charles of Anjou's coronation at Rome to Conradin's execution",
        originalTitle = "Coronation to execution",
        author = "Charles of Anjou and Conradin of Swabia",
        year = 1268,
        summary = "Charles of Anjou was crowned king of Sicily at Rome on 6 January 1266, and a short program counts the days from that coronation to Conradin's execution at Naples on 29 October 1268.",
        details = "Both dates are given as commonly stated, and the span crosses the leap day of February 1268. The example uses fixed dates in the Python calendar, so the count stays the same on every run, and it tests date arithmetic rather than the politics of the campaign.",
        instructions = """
            1. Save the example as coronation_execution.py.
            2. Run it: python3 coronation_execution.py
        """,
        example = """
            from datetime import date

            coronation = date(1266, 1, 6)   # Charles of Anjou crowned at Rome, as commonly given
            execution = date(1268, 10, 29)  # Conradin executed at Naples, as commonly given
            print("days from the coronation to the execution:", (execution - coronation).days)
        """,
    ),

    entry(
        databaseId = 78,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Hulagu Khan to Charles of Anjou's coronation",
        originalTitle = "Death to coronation",
        author = "Hulagu Khan and Charles of Anjou",
        year = 1266,
        summary = "Hulagu Khan, who backed the Maragha observatory, died on 8 February 1265, and Charles of Anjou was crowned at Rome on 6 January 1266; a short program counts the days between them.",
        details = "Both dates are given as commonly stated, and the span runs through the end of 1265 and into the new year. The example uses fixed dates in the Python calendar so the count is the same on every run, and it tests date arithmetic rather than the history behind either date.",
        instructions = """
            1. Save the example as hulagu_coronation.py.
            2. Run it: python3 hulagu_coronation.py
        """,
        example = """
            from datetime import date

            death = date(1265, 2, 8)        # Hulagu Khan's death, as commonly given
            coronation = date(1266, 1, 6)   # Charles of Anjou crowned at Rome, as commonly given
            print("days from the death to the coronation:", (coronation - death).days)
        """,
    ),

)
