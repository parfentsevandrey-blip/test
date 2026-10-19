package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 760 to 770: how a Latin word list is put in alphabetical order by hand, as the Abrogans is arranged. */
val db128Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.GUIDE,
        section = "Glossaries",
        title = "How a Latin word list is put in alphabetical order by hand, as the Abrogans glossary is arranged",
        originalTitle = "Alphabetical order of a Latin word list",
        author = "Unknown glossary makers",
        year = 765,
        summary = "A glossary such as the Abrogans is put in alphabetical order one word at a time, and a short program repeats that method with hand-written comparisons.",
        details = "The Abrogans is arranged by its Latin headwords in alphabetical order, and the method here compares two words letter by letter, placing each new word ahead of the first word that follows it. The example sorts a small set of Latin words by that method, and the words are a sample chosen for the example, not a copy of the glossary.",
        rare = true,
        instructions = """
            1. Save the example as alphabetize.py.
            2. Run it: python3 alphabetize.py
        """,
        example = """
            # compare two words letter by letter: True when a comes before b
            def before(a, b):
                for x, y in zip(a, b):
                    if x != y:
                        return x < y
                return len(a) < len(b)


            WORDS = ["balneum", "abbas", "acer", "aqua", "bos", "abies", "ara"]
            ordered = []
            for word in WORDS:
                place = len(ordered)
                for i, other in enumerate(ordered):
                    if before(word, other):
                        place = i
                        break
                ordered.insert(place, word)
            for word in ordered:
                print(word)
        """,
    ),

)
