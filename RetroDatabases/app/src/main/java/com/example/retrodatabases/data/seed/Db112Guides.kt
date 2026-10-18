package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide placed at 930, the year Emperor Daigo died: how the re mark of kaeriten reading marks reorders a line of Chinese for Japanese readers. */
val db112Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.GUIDE,
        section = "Japan",
        title = "How the re mark of kaeriten reading marks reorders a line of Chinese",
        originalTitle = "Kaeriten",
        author = "Heian readers of Chinese texts",
        year = 930,
        summary = "A re mark beside a character tells the reader to take the character below it first, so a line of Chinese can be reordered for Japanese reading by a few swaps.",
        details = "The sources do not date this reading method to a single year, so 930, the year Emperor Daigo died, is the year of the anchor and not the date of the method. The program applies the re marks to a stand-in line of five characters, each written as a romanized label, and checks that the reordered line keeps every character.",
        rare = true,
        instructions = """
            1. Save the example as reorder.py.
            2. Run it: python3 reorder.py
        """,
        example = """
            line = ["ken", "fu", "ki", "sho", "ji"]     # stand-in characters, in printed order
            marks = [None, "re", None, "re", None]       # a re mark means read the character below first

            order = []
            swaps = 0
            i = 0
            while i < len(line):
                if marks[i] == "re" and i + 1 < len(line):
                    order += [line[i + 1], line[i]]
                    swaps += 1
                    i += 2
                else:
                    order.append(line[i])
                    i += 1

            print("printed order:", " ".join(line))
            print("reading order:", " ".join(order))
            print("swaps made:", swaps)
            print("same characters:", sorted(order) == sorted(line))
        """,
    ),

)
