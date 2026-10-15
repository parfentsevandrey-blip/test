package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1570 to 1580: the days of Drake's voyage round the world, from the battle of Lepanto to Leiden, and from Tycho's nova to Leiden. */
val db47Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.FLEX,
        section = "Voyages",
        title = "Days in Drake's voyage round the world, 1577 to 1580",
        originalTitle = "The voyage round the world",
        author = "Francis Drake",
        year = 1577,
        summary = "Drake's voyage round the world ran from December 1577 to September 1580, and a short program counts the days and weeks between the dates usually given.",
        details = "Drake sailed from Plymouth in December 1577 and came back in September 1580, and accounts differ on the calendar in which the dates were first written. The example uses the dates as commonly given in the modern calendar, with Python's date arithmetic, so its result never changes.",
        instructions = """
            1. Save the example as drake_days.py.
            2. Run it: python3 drake_days.py
        """,
        example = """
            from datetime import date

            sailed = date(1577, 12, 13)   # as commonly given
            returned = date(1580, 9, 26)  # as commonly given
            days = (returned - sailed).days
            print(f"days from 13 December 1577 to 26 September 1580: {days}")
            print(f"weeks and days: {days // 7} weeks and {days % 7} days")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the battle of Lepanto to Leiden University's founding",
        originalTitle = "The battle of Lepanto",
        author = "Don John of Austria",
        year = 1571,
        summary = "The battle of Lepanto was fought in October 1571 and Leiden University was founded in February 1575, and the example counts the days between the two dates.",
        details = "The battle is usually dated to 7 October 1571 and the university's founding to 8 February 1575. The example uses the modern calendar for both dates, so the count is in that calendar and not in the calendars of the time.",
        instructions = """
            1. Save the example as lepanto_leiden.py.
            2. Run it: python3 lepanto_leiden.py
        """,
        example = """
            from datetime import date

            lepanto = date(1571, 10, 7)   # the battle, as commonly given
            leiden = date(1575, 2, 8)     # the founding, as commonly given
            print(f"days from Lepanto to Leiden University: {(leiden - lepanto).days}")
        """,
    ),

    entry(
        databaseId = 47,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Tycho's first sight of the nova to Leiden University",
        originalTitle = "De nova stella",
        author = "Tycho Brahe",
        year = 1572,
        summary = "Tycho first saw the new star in November 1572, and the example counts the days from that sighting to the founding of Leiden University in 1575.",
        details = "Tycho's first sight of the nova is commonly dated to 11 November 1572, and Leiden's founding to 8 February 1575. The example uses the modern calendar for both dates, so the count is in that calendar.",
        instructions = """
            1. Save the example as nova_leiden.py.
            2. Run it: python3 nova_leiden.py
        """,
        example = """
            from datetime import date

            nova_seen = date(1572, 11, 11)   # Tycho's first sight of the nova, as commonly given
            leiden = date(1575, 2, 8)        # the founding, as commonly given
            print(f"days from the nova to Leiden University: {(leiden - nova_seen).days}")
        """,
    ),

)
