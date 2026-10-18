package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1240 to 1250: days from Innocent IV's accession to Frederick II's deposition, from that deposition to the emperor's death, and from Louis IX's departure to his capture. */
val db80Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the start of Innocent IV's pontificate to Frederick II's deposition",
        originalTitle = "Days between two dated events",
        author = "Pope Innocent IV and the First Council of Lyon",
        year = 1245,
        summary = "Innocent IV's pontificate began on 25 June 1243 and Frederick II was deposed on 17 July 1245, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example uses the Python calendar so the count is exact for those dates. The span includes the leap day of 1244.",
        instructions = """
            1. Save the example as pontificate.py.
            2. Run it: python3 pontificate.py
        """,
        example = """
            from datetime import date

            start = date(1243, 6, 25)        # start of Innocent IV's pontificate, as commonly given
            deposition = date(1245, 7, 17)   # deposition of Frederick II at Lyon, as commonly given
            print("days from the start of the pontificate to the deposition:", (deposition - start).days)
        """,
    ),

    entry(
        databaseId = 80,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the deposition of Frederick II to his death",
        originalTitle = "Days between two dated events",
        author = "Frederick II and the First Council of Lyon",
        year = 1250,
        summary = "Frederick II was deposed on 17 July 1245 and died on 13 December 1250, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the Python calendar gives an exact count for them. The span includes the leap day of 1248.",
        instructions = """
            1. Save the example as frederick_death.py.
            2. Run it: python3 frederick_death.py
        """,
        example = """
            from datetime import date

            deposition = date(1245, 7, 17)   # deposition at the last public sitting of Lyon, as commonly given
            death = date(1250, 12, 13)       # death of Frederick II, as commonly given
            print("days from the deposition to the death:", (death - deposition).days)
        """,
    ),

    entry(
        databaseId = 80,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Louis IX's departure on crusade to his capture",
        originalTitle = "Days between two dated events",
        author = "Louis IX of France",
        year = 1248,
        summary = "Louis IX of France left Aigues-Mortes for crusade on 25 August 1248 and was captured in Egypt on 6 April 1250, and a short program counts the days between the two dates.",
        details = "Both dates are given as commonly stated, and the example counts with the Python calendar, so the result is exact for the dates as written. The span runs from the departure to the capture that followed the retreat from Mansurah.",
        instructions = """
            1. Save the example as louis_capture.py.
            2. Run it: python3 louis_capture.py
        """,
        example = """
            from datetime import date

            departure = date(1248, 8, 25)    # departure for the crusade from Aigues-Mortes, as commonly given
            capture = date(1250, 4, 6)       # capture of the king in Egypt, as commonly given
            print("days from Louis IX's departure to his capture:", (capture - departure).days)
        """,
    ),

)
