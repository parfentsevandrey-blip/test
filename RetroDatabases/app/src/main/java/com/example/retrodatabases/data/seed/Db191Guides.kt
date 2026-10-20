package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 130 to 140: how an obelus marks a doubtful line that a second copy lacks, placed at 130 for the founding of Antinoopolis. */
val db191Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.GUIDE,
        section = "Editing",
        title = "How an obelus marks a doubtful line that a second copy lacks",
        originalTitle = "Obelos",
        author = "Alexandrian editors",
        year = 130,
        summary = "The Alexandrian editors marked a line they doubted with a short horizontal stroke, the obelus, and a short program marks the lines of one copy that the other copy lacks.",
        details = "The obelus is older than 130, so the year is placed at the founding of Antinoopolis in that year, as an anchor, and it is not the date of the sign. The lines in the example are invented, so the output says nothing about a real text, and the marks are the same on every run.",
        instructions = """
            1. Save the example as obelus.py.
            2. Run it: python3 obelus.py
        """,
        example = """
            # two copies of one short text; the first copy has a line that the second lacks
            copy_a = ["line one", "line two", "line three", "line four"]
            copy_b = ["line one", "line three", "line four"]
            for line in copy_a:
                if line in copy_b:
                    print(line)
                else:
                    print("obelus:", line)
        """,
        rare = true,
    ),

)
