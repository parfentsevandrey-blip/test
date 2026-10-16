package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1450 to 1460: the arrangements of the printer's name, the days of the siege of Constantinople and the days from a papal bull to the founding of Greifswald University. */
val db59Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.FLEX,
        section = "Counting",
        title = "Anagrams of the name Gutenberg, counted with repeated letters",
        originalTitle = "Gutenberg",
        author = "Johann Gutenberg",
        year = 1450,
        summary = "The nine letters of Gutenberg's name, with two G's and two E's, can be arranged in 90720 different orders, and a short program counts them.",
        details = "The count divides the arrangements of nine letters by the arrangements of each repeated letter, which is the usual method for words with repeats. The example counts the letters and prints the number of distinct arrangements, and it uses no data from any source.",
        rare = true,
        instructions = """
            1. Save the example as name_anagrams.py.
            2. Run it: python3 name_anagrams.py
        """,
        example = """
            from math import factorial

            name = "Gutenberg".lower()
            counts = {}
            for letter in name:
                counts[letter] = counts.get(letter, 0) + 1

            arrangements = factorial(len(name))
            for times in counts.values():
                arrangements //= factorial(times)

            print("letters:", len(name))
            print("distinct arrangements:", arrangements)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days of the siege of Constantinople, from 6 April to 29 May 1453",
        originalTitle = "Obsidio Constantinopolitana",
        author = "Sultan Mehmed II and the defenders of the city",
        year = 1453,
        summary = "The siege of Constantinople began on 6 April 1453 and the city fell on 29 May, so a short program counts the 53 days from the start to the fall.",
        details = "Both dates are given in the usual accounts, and the count runs from the start of the siege to the fall of the city. The example prints the count with Python's calendar and uses no other data.",
        instructions = """
            1. Save the example as siege_days.py.
            2. Run it: python3 siege_days.py
        """,
        example = """
            from datetime import date

            siege_began = date(1453, 4, 6)   # the start of the siege, as commonly given
            city_fell = date(1453, 5, 29)    # the fall of the city, as commonly given
            print("days of the siege of Constantinople:", (city_fell - siege_began).days)
        """,
    ),

    entry(
        databaseId = 59,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Days from the bull Dum diversas to the founding of Greifswald University",
        originalTitle = "Dum diversas",
        author = "Pope Nicholas V, and Heinrich Rubenow of Greifswald",
        year = 1456,
        summary = "The bull Dum diversas of 18 June 1452 and the founding of the University of Greifswald on 17 October 1456 are 1582 days apart, and a short program counts the days between them.",
        details = "The founding day is the one given by the university's own history, and an older encyclopedia dates the foundation bull to 29 May 1456, which would give a shorter count. The example uses Python's calendar, and the interval is the same in the Julian and Gregorian reckonings for these years.",
        instructions = """
            1. Save the example as bull_university_days.py.
            2. Run it: python3 bull_university_days.py
        """,
        example = """
            from datetime import date

            dum_diversas = date(1452, 6, 18)   # the bull's date, as commonly given
            greifswald = date(1456, 10, 17)    # the founding day, as the university gives it
            print("days from Dum diversas to Greifswald:", (greifswald - dum_diversas).days)
        """,
    ),

)
