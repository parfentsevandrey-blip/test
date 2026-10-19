package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 710 to 720: the books of the Latin Vulgate counted in code, placed at 716 for the journey of the Codex Amiatinus toward Rome. */
val db133Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.EXAMPLE,
        section = "Latin",
        title = "The 46 Old Testament books and 27 New Testament books of the Latin Vulgate, counted in code, placed at 716",
        originalTitle = "Vulgate canon",
        author = "Jerome, for the Vulgate",
        year = 716,
        summary = "A short program counts the Latin Vulgate as 46 Old Testament books and 27 New Testament books, 73 in all, and sets its Old Testament beside the 39 books of the Protestant canon.",
        details = "Jerome's Latin Bible was made about 390 to 405, so 716 is not the date of the book list; it is the year of the anchor, the journey of the Codex Amiatinus toward Rome, which carries the Vulgate text. The seven books that the Catholic Old Testament adds to the Protestant one are Tobit, Judith, Wisdom, Sirach, Baruch and the two books of Maccabees.",
        rare = true,
        instructions = """
            1. Save the example as vulgate_books.py.
            2. Run it: python3 vulgate_books.py
        """,
        example = """
            OLD_TESTAMENT = 46   # books of the Catholic Old Testament in the Latin Vulgate
            NEW_TESTAMENT = 27   # books of the New Testament
            PROTESTANT_OLD = 39  # books of the Protestant Old Testament
            print("Old Testament books:", OLD_TESTAMENT)
            print("New Testament books:", NEW_TESTAMENT)
            print("total books:", OLD_TESTAMENT + NEW_TESTAMENT)
            print("Protestant canon:", PROTESTANT_OLD + NEW_TESTAMENT)
            print("extra Old Testament books:", OLD_TESTAMENT - PROTESTANT_OLD)
        """,
    ),

)
