package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A practice of 1008: the Masoretic points and accents of the Hebrew Bible, as fixed in the Leningrad Codex. */
val db104Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.PRACTICE,
        section = "Scripture",
        title = "Masoretic points and accents, fixed in the Leningrad Codex of 1008",
        originalTitle = "Codex Leningradensis",
        author = "The Masoretes of the Ben Asher school",
        year = 1008,
        summary = "The Masoretes' vowel points and accent marks for the Hebrew Bible are written into the Leningrad Codex, whose colophon dates it to 1008.",
        details = "The points and accents tell the reader how each word is vowelled and how each verse is divided and phrased, and they were added to the consonantal text over a long tradition of scholars. The Leningrad Codex is the oldest complete manuscript of the Hebrew Bible with these marks, and its date comes from the colophon, the note the scribe wrote at its end.",
    ),

)
