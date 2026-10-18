package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1140 to 1150: the books of Justinian's law and the volumes of the Samguk sagi. */
val db90Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.EXAMPLE,
        section = "Law",
        title = "The books of Justinian's three law collections, added up",
        originalTitle = "Corpus iuris civilis",
        author = "Justinian and his compilers",
        year = 1145,
        summary = "A short program adds the books of the Institutes, the Digest and the Code, the three parts of Justinian's law that the Bolognese lawyers of the 1140s studied.",
        details = "The Institutes have four books, the Digest fifty and the Code twelve, and the example sums them to sixty-six. The Novels are left out because their number varies between manuscripts and editions.",
        instructions = """
            1. Save the example as justinian_books.py.
            2. Run it: python3 justinian_books.py
        """,
        example = """
            parts = [("Institutes", 4), ("Digest", 50), ("Code", 12)]
            for name, books in parts:
                print(name, books, "books")
            print("books in the three parts:", sum(books for _, books in parts))
        """,
    ),

    entry(
        databaseId = 90,
        kind = Kind.EXAMPLE,
        section = "Histories",
        title = "The volumes of the Samguk sagi, counted by kind",
        originalTitle = "Samguk sagi",
        author = "Kim Busik",
        year = 1145,
        summary = "A short program counts the fifty volumes of the Samguk sagi by their kinds, annals, treatises, tables and biographies.",
        details = "The usual division is twenty-eight volumes of annals, nine of treatises, three of tables and ten of biographies, which makes fifty. This is the division given in the usual descriptions of the work, and the three tables are the chronological lists at its close.",
        instructions = """
            1. Save the example as samguk_volumes.py.
            2. Run it: python3 samguk_volumes.py
        """,
        example = """
            volumes = {"annals": 28, "treatises": 9, "tables": 3, "biographies": 10}
            for kind, count in volumes.items():
                print(kind, count)
            print("volumes in all:", sum(volumes.values()))
        """,
    ),

)
