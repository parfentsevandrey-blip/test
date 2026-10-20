package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 175: how a Greek word list is grouped under subject headings, after Pollux's Onomasticon. */
val db187Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.GUIDE,
        section = "Lists",
        title = "How a Greek word list is grouped under subject headings, as in Pollux's Onomasticon",
        originalTitle = "Onomastikon",
        author = "Julius Pollux",
        year = 175,
        summary = "Julius Pollux sorted Greek words under subject headings in his Onomasticon, and a short program groups a list of words under subjects in the same way.",
        details = "Pollux wrote the work in ten books addressed to Commodus, and the addresses style Commodus as Caesar, which points to a date before he became co-emperor in 177, so 175 is a round date within that range. A catalogue entry gives Pollux's own dates as 180 to 238, so the dates of the man and of the book are not fully agreed, and the example uses an illustrative list of words rather than Pollux's text.",
        instructions = """
            1. Save the example as subjects.py.
            2. Run it: python3 subjects.py
        """,
        example = """
            words = [
                ("kephale", "body"),
                ("cheir", "body"),
                ("ouranos", "sky"),
                ("helios", "sky"),
                ("chiton", "clothing"),
                ("himation", "clothing"),
            ]
            subjects = {}
            for word, subject in words:
                subjects.setdefault(subject, []).append(word)
            for subject in sorted(subjects):
                print(subject + ":", ", ".join(subjects[subject]))
            print("subjects:", len(subjects))
            print("words:", len(words))
        """,
        rare = true,
    ),

)
