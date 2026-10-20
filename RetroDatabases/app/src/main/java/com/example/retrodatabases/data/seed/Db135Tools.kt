package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 690 to 700: the Epinal Glossary, a Latin word list with Old English glosses, whose surviving copy is usually dated to about 700. */
val db135Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.TOOL,
        section = "English",
        title = "The Epinal Glossary, a Latin word list of about 3,300 entries with Old English glosses, copied about 700",
        originalTitle = "Glossarium Epinalense",
        author = "Anglo-Saxon compilers, perhaps at Canterbury",
        year = 700,
        summary = "The Epinal Glossary is a Latin word list of about 3,300 entries, roughly a third of them with Old English glosses, and its surviving copy is usually dated to about 700.",
        details = "One account places the composition in England in the late seventh century, while E. A. Lowe in 1953 leaned to the early eighth century for the manuscript, so accounts differ on its date. The glossary was a reference book for readers of Latin, and this record uses the year of the surviving copy.",
        rare = true,
    ),

)
