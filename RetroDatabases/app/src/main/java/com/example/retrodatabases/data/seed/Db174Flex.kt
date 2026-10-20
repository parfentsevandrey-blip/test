package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 300 to 310: days from the first edict of the Great Persecution to the abdication of Diocletian, and from the abdication to Constantine's acclamation at York. */
val db174Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the first edict of the Great Persecution to Diocletian's abdication",
        originalTitle = "Days between two dated events",
        author = "Diocletian and Galerius",
        year = 303,
        summary = "The first edict of the Great Persecution was issued on 23 February 303 and Diocletian abdicated on 1 May 305, and a short program counts the days between them.",
        details = "Both dates are as commonly given in the Julian calendar of the time. Python's date type counts in the Gregorian reckoning, but for 303 to 305 both reckonings have the same leap years, and the span includes the leap day of February 304.",
        instructions = """
            1. Save the example as first_edict.py.
            2. Run it: python3 first_edict.py
        """,
        example = """
            from datetime import date

            first_edict = date(303, 2, 23)   # the first edict, as commonly dated
            abdication = date(305, 5, 1)     # the abdication of Diocletian, as commonly dated
            print("days from the first edict to the abdication:", (abdication - first_edict).days)
        """,
    ),

    entry(
        databaseId = 174,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from Diocletian's abdication to Constantine's acclamation at York",
        originalTitle = "Days between two dated events",
        author = "Diocletian and Constantine",
        year = 305,
        summary = "Diocletian abdicated on 1 May 305 and Constantine was acclaimed at York on 25 July 306, and a short program counts the days between the two dates.",
        details = "Neither 305 nor 306 is a leap year, so the span holds no leap day, and the total is the length of one full year plus the days from 1 May to 25 July 306. The example uses the same Python calendar, and the total can be checked by hand from the month lengths.",
        instructions = """
            1. Save the example as york_days.py.
            2. Run it: python3 york_days.py
        """,
        example = """
            from datetime import date

            abdication = date(305, 5, 1)    # the abdication of Diocletian, as commonly dated
            york = date(306, 7, 25)         # the acclamation of Constantine at York, as commonly dated
            print("days from the abdication to the acclamation at York:", (york - abdication).days)
        """,
    ),

)
