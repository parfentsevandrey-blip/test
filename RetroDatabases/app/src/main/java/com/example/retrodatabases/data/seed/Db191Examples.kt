package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 130: a line written without spaces, as early copies were, cut into the names Antinoos and Antinoopolis. */
val db191Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.EXAMPLE,
        section = "Writing",
        title = "A line written without spaces, cut into the names Antinoos and Antinoopolis",
        originalTitle = "Scriptio continua",
        author = "No single author",
        year = 130,
        summary = "Greek and Latin copies were written without spaces between words, and a short program cuts a continuous line back into the names Antinoos and Antinoopolis.",
        details = "Antinoopolis was founded in Egypt in 130 and named for Antinous, so the example is dated by its names, while the practice of writing without spaces is older and is not dated here. The word list of the program is a modern aid, and the cut is exact only for the two names listed.",
        instructions = """
            1. Save the example as cut.py.
            2. Run it: python3 cut.py
        """,
        example = """
            # a line written without spaces, as early copies were
            text = "antinoosantinoopolis"
            WORDS = ["antinoos", "antinoopolis"]
            found = []
            pos = 0
            while pos < len(text):
                best = ""
                for word in WORDS:
                    if text.startswith(word, pos) and len(word) > len(best):
                        best = word
                if best == "":
                    best = text[pos]
                found.append(best)
                pos += len(best)
            print(" ".join(found))
            print("words:", len(found))
        """,
        rare = true,
    ),

)
