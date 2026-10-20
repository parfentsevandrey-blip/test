package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 300 to 310: the seven books of Arnobius's Against the Pagans, counted in code. */
val db174Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.EXAMPLE,
        section = "Books",
        title = "The seven books of Arnobius's Against the Pagans, counted in code",
        originalTitle = "Adversus nationes",
        author = "Arnobius of Sicca",
        year = 303,
        summary = "Arnobius of Sicca's Against the Pagans is a Latin defence of Christianity in seven books, and a short program lists the books and counts them.",
        details = "The work is usually dated about 303, although proposals run from about 297 to about 327, and the year 303 is used here. The example prints the books in order, so the count of seven can be checked by hand.",
        instructions = """
            1. Save the example as arnobius_books.py.
            2. Run it: python3 arnobius_books.py
        """,
        example = """
            BOOKS = ["Book I", "Book II", "Book III", "Book IV", "Book V", "Book VI", "Book VII"]
            for name in BOOKS:
                print(name)
            print("books:", len(BOOKS))
        """,
        rare = true,
    ),

)
