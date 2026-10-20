package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 680 to 690: the chapters of the four Gospels in the modern division, 89 in all, counted in code. */
val db136Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.EXAMPLE,
        section = "Gospels",
        title = "The chapters of the four Gospels, 89 in all in the divisions of modern Bibles, counted in code and placed at 680 for the Book of Durrow",
        originalTitle = "Evangelia quattuor",
        author = "Stephen Langton, credited with the chapter divisions",
        year = 680,
        summary = "A short program counts the chapters of Matthew, Mark, Luke and John in the division printed in modern Bibles, and the four Gospels come to 89 chapters.",
        details = "The year 680 is the date usually given to the Book of Durrow, a copy of these four Gospels, and it is used here as an anchor; the chapter numbers themselves came into use in the thirteenth century. Gospel books of the 680s used other chapter lists, so the count describes the modern division only.",
        rare = true,
        instructions = """
            1. Save the example as gospel_chapters.py.
            2. Run it: python3 gospel_chapters.py
        """,
        example = """
            CHAPTERS = [
                ("Matthew", 28),
                ("Mark", 16),
                ("Luke", 24),
                ("John", 21),
            ]
            for book, count in CHAPTERS:
                print(book, count, "chapters")
            print("total:", sum(count for _, count in CHAPTERS), "chapters")
        """,
    ),

)
