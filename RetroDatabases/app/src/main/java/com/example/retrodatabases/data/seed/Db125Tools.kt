package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 790 to 800: Khalil ibn Ahmad's Kitab al-Ayn, placed at 791, one of the two years given for its compiler's death. */
val db125Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.TOOL,
        section = "Arabic",
        title = "Khalil ibn Ahmad's Kitab al-Ayn, an Arabic dictionary whose word lists follow the place where each letter is spoken",
        originalTitle = "Kitab al-Ayn",
        author = "Khalil ibn Ahmad al-Farahidi",
        year = 791,
        summary = "The Kitab al-Ayn, an Arabic dictionary arranged by where its letters are spoken, is undated, so 791, one of the two years given for its compiler's death, is the anchor.",
        details = "Accounts give 786 or 791 for Khalil's death, and 791 is used here only as the anchor, not as the date of the dictionary. The word lists begin with the letter 'ayn, the deepest throat sound, and move forward toward the lips, and the book takes its name from that first letter.",
        rare = true,
    ),

)
