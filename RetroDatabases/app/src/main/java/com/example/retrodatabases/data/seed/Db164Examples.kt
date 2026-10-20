package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 400 to 410: the twelve books of Virgil's Aeneid, with their lines counted and added up in code. */
val db164Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The twelve books of Virgil's Aeneid, with their lines added up",
        originalTitle = "Aeneid",
        author = "Virgil",
        year = 400,
        summary = "A short program adds up the lines of the twelve books of Virgil's Aeneid, the poem that Servius's commentary explained to students about 400.",
        details = "The line counts are the usual ones for each book, and they add up to the familiar total of 9896 lines. The year 400 is the anchor of this placed record, the date of Servius's commentary, and not the date of the poem, which Virgil wrote in the decade before his death in 19 BC.",
        instructions = """
            1. Save the example as aeneid_lines.py.
            2. Run it: python3 aeneid_lines.py
        """,
        example = """
            BOOKS = [("I", 756), ("II", 804), ("III", 718), ("IV", 705), ("V", 871), ("VI", 901),
                     ("VII", 817), ("VIII", 731), ("IX", 818), ("X", 908), ("XI", 915), ("XII", 952)]

            print("books:", len(BOOKS))
            print("total lines:", sum(count for _, count in BOOKS))
            longest = max(BOOKS, key=lambda book: book[1])
            shortest = min(BOOKS, key=lambda book: book[1])
            print("longest book:", longest[0], longest[1])
            print("shortest book:", shortest[0], shortest[1])
        """,
    ),

)
