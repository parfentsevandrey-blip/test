package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 440 to 450: the eight market letters of a Roman calendar, placed at 443, the year of the Yuanjia calendar proposal. */
val db160Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How the eight market letters of a Roman calendar name each day, counted in code",
        originalTitle = "Litterae nundinales",
        author = "Roman calendar makers",
        year = 443,
        summary = "The Roman calendar marked each day with one of eight market letters, A to H, and a short program names the letter for a given day of the year.",
        details = "The sources do not date this method; 443 is the year of the anchor, the Yuanjia calendar proposal, and not the year the Roman letters were first used, which is far older. The example assumes an illustrative year that opens with the letter A, so the output follows from the eight-day count alone.",
        rare = true,
        instructions = """
            1. Save the example as nundinal_letters.py.
            2. Run it: python3 nundinal_letters.py
        """,
        example = """
            LETTERS = "ABCDEFGH"   # the eight market letters of the Roman calendar
            START = 0              # illustrative: the year opens with the letter A


            def nundinal_letter(day_of_year):
                return LETTERS[(START + day_of_year - 1) % 8]


            for day in [1, 2, 8, 9, 10, 16, 17, 31, 32, 60, 365]:
                print("day", day, "has letter", nundinal_letter(day))
            print("letter A days in a 365-day year:", sum(1 for d in range(1, 366) if nundinal_letter(d) == "A"))
        """,
    ),

)
