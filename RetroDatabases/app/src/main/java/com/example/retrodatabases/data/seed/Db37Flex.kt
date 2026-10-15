package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1670 to 1680: leap years of the Julian calendar, a penny-post count and Hooke's anagram letters. */
val db37Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 37,
        kind = Kind.FLEX,
        section = "Calendars",
        title = "Julian leap years in the 1670s",
        originalTitle = "Julian calendar",
        author = "Julius Caesar",
        year = 1676,
        summary = "In the Julian calendar every year divisible by four is a leap year, so the 1670s have two leap years, 1672 and 1676.",
        details = "The Julian rule was used in England until 1752, so the count is the one an English observer of the 1670s would have made. The example lists the leap years from 1670 to 1679 and counts them.",
        instructions = """
            1. Save the example as julian_leap.py.
            2. Run it: python3 julian_leap.py
        """,
        example = """
            def julian_leap(year):
                # The Julian calendar makes every year divisible by four a leap year.
                return year % 4 == 0

            leaps = [year for year in range(1670, 1680) if julian_leap(year)]
            print("leap years 1670 to 1679:", leaps)
            print("count:", len(leaps))
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.FLEX,
        section = "Post",
        title = "Penny letters in a pound",
        originalTitle = "Penny post",
        author = "William Dockwra",
        year = 1680,
        summary = "Before decimal coinage a pound held 240 pence, so 240 one-penny letters came to one pound.",
        details = "The London penny post of 1680 charged a penny a letter, and the pound of the period held 240 pence. The example multiplies pounds by 240 and prints the number of one-penny letters for one pound and for five pounds.",
        instructions = """
            1. Save the example as penny_pounds.py.
            2. Run it: python3 penny_pounds.py
        """,
        example = """
            PENCE_PER_POUND = 240

            def penny_letters_for_pounds(pounds):
                # Before decimal coinage a pound held 240 pence, so that many one-penny letters came to one pound.
                return pounds * PENCE_PER_POUND

            print("one-penny letters in one pound:", penny_letters_for_pounds(1))
            print("in five pounds:", penny_letters_for_pounds(5))
        """,
    ),

    entry(
        databaseId = 37,
        kind = Kind.FLEX,
        section = "Letters",
        title = "Hooke's anagram letter count",
        originalTitle = "ut tensio, sic vis",
        author = "Robert Hooke",
        year = 1676,
        summary = "Hooke's anagram for his law of springs has fourteen letters once the letter v is read as u, and counting them shows how often each letter occurs.",
        details = "Hooke published the anagram in 1676, and reading v as u was the usual practice of the time. The example counts the letters of ut tensio, sic vis, sorts them, and prints the count of u, which is two.",
        instructions = """
            1. Save the example as hooke_anagram.py.
            2. Run it: python3 hooke_anagram.py
        """,
        example = """
            from collections import Counter

            PHRASE = "ut tensio sic vis"   # Hooke's Latin; in the anagram the letter v is read as u

            letters = [ch for ch in PHRASE.replace("v", "u") if ch.isalpha()]
            counts = Counter(letters)
            print("letters:", len(letters))
            print("sorted:", "".join(sorted(letters)))
            print("u count:", counts["u"])
        """,
    ),

)
