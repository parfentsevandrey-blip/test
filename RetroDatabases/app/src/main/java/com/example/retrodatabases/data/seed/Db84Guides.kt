package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1200 to 1210: how the Mongol script runs in columns, written out as a short program. */
val db84Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How the Mongol script is laid out in vertical columns",
        originalTitle = "Mongol bichig, vertical writing",
        author = "Mongol and Uyghur scribes",
        year = 1204,
        summary = "The Mongol script runs down each column from top to bottom, and the columns follow from left to right, so a short program can lay out a word in that order.",
        details = "The example sets two short words in columns, one beside the other, and prints the rows. It uses Latin letters for the words, so it shows the order of writing and not the forms of the script's letters.",
        instructions = """
            1. Save the example as mongol_columns.py.
            2. Run it: python3 mongol_columns.py
        """,
        example = """
            words = ["mongol", "bichig"]
            height = max(len(word) for word in words)
            columns = [list(word) + [" "] * (height - len(word)) for word in words]
            for row in range(height):
                print("  ".join(column[row] for column in columns))
            print("columns:", len(columns))
        """,
        rare = true,
    ),

)
