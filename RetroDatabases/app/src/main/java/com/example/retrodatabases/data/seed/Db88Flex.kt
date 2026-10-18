package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1160 to 1170: the days between the coronation of Henry II and Becket's death, and two other spans between deaths of the decade. */
val db88Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Henry II's coronation to Becket's death",
        originalTitle = "Days between two dates",
        author = "Henry II and Thomas Becket",
        year = 1170,
        summary = "Henry II was crowned on 19 December 1154 and Becket was killed on 29 December 1170, and a short program counts the days between the two dates.",
        details = "Both dates are given as usually stated in the Julian style of England, and the Python calendar gives the same count, because the leap years from 1154 to 1170 fall on the same years in both calendars. The span crosses four leap days, in 1156, 1160, 1164 and 1168.",
        instructions = """
            1. Save the example as coronation_becket.py.
            2. Run it: python3 coronation_becket.py
        """,
        example = """
            from datetime import date

            start = date(1154, 12, 19)   # Henry II's coronation, as usually stated
            end = date(1170, 12, 29)     # Becket's death, as usually stated
            print("days from Henry II's coronation to Becket's death:", (end - start).days)
        """,
    ),

    entry(
        databaseId = 88,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Victor IV to the death of Empress Matilda",
        originalTitle = "Days between two dates",
        author = "Victor IV and Empress Matilda",
        year = 1167,
        summary = "The antipope Victor IV died on 20 April 1164 and Empress Matilda on 10 September 1167, and a short program counts the days between their deaths.",
        details = "Both dates are given as usually stated, and the Python calendar gives the same count, since no century year falls between them. The span holds no leap day, because the next 29 February after 1164 falls in 1168.",
        instructions = """
            1. Save the example as victor_matilda.py.
            2. Run it: python3 victor_matilda.py
        """,
        example = """
            from datetime import date

            start = date(1164, 4, 20)    # Victor IV's death, as usually stated
            end = date(1167, 9, 10)      # Empress Matilda's death, as usually stated
            print("days from Victor IV's death to Empress Matilda's death:", (end - start).days)
        """,
    ),

    entry(
        databaseId = 88,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the death of Paschal III to the death of Becket",
        originalTitle = "Days between two dates",
        author = "Paschal III and Thomas Becket",
        year = 1170,
        summary = "The antipope Paschal III died on 20 September 1168, and a short program counts the days from his death to Becket's murder on 29 December 1170.",
        details = "Both dates are given as usually stated, and the span holds no leap day, since neither 1169 nor 1170 is a leap year and February 1168 comes before the start. The count is therefore two whole years of 365 days plus the days from 20 September to 29 December.",
        instructions = """
            1. Save the example as paschal_becket.py.
            2. Run it: python3 paschal_becket.py
        """,
        example = """
            from datetime import date

            start = date(1168, 9, 20)    # Paschal III's death, as usually stated
            end = date(1170, 12, 29)     # Becket's death, as usually stated
            print("days from Paschal III's death to Becket's death:", (end - start).days)
        """,
    ),

)
