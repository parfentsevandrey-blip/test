package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1030: the Maya Long Count, a method devised centuries before the year given, which turns a date into a count of days. */
val db101Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How the Maya Long Count turns a date into a count of days and back",
        originalTitle = "Long Count",
        author = "Maya scribes who carved Long Count dates",
        year = 1030,
        summary = "A short program turns the Long Count date 9.12.11.16.4 into a count of days from its base date, converts the count back, and prints a check.",
        details = "The Long Count is older than this record: it was devised in the Preclassic period, and Maya inscriptions counted days from a fixed base with it for centuries before 1030. The year 1030 is a placement, not the date of any inscription, and in the Postclassic kingdoms of Yucatan the Short Count had replaced it.",
        instructions = """
            1. Save the example as long_count.py.
            2. Run it: python3 long_count.py
        """,
        example = """
            PLACES = [144000, 7200, 360, 20, 1]    # days in a baktun, katun, tun, winal and kin
            long_count = [9, 12, 11, 16, 4]        # the date 9.12.11.16.4

            days = sum(digit * place for digit, place in zip(long_count, PLACES))
            print("days since 0.0.0.0.0:", days)

            back = []
            rest = days
            for place in PLACES:
                back.append(rest // place)
                rest = rest % place
            print("written back:", ".".join(str(digit) for digit in back))
            print("check:", "ok" if back == long_count else "wrong")
        """,
        rare = true,
    ),

)
