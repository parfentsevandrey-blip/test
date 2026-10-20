package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 400 to 410: Servius's commentary on Virgil, a grammar teacher's aid to reading the poems. */
val db164Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.TOOL,
        section = "Commentary",
        title = "Servius's commentary on Virgil, a school aid to reading the poems",
        originalTitle = "Commentarii in Vergilium",
        author = "Maurus Servius Honoratus",
        year = 400,
        summary = "Servius wrote a commentary on Virgil that explains the grammar, words and myths of the poems for students, and it is usually dated about 400.",
        details = "Servius was a grammarian, and his notes served as a reading aid in schools for centuries, so the commentary is a tool for readers rather than a book of new ideas. The date about 400 is the usual approximate date for his work, and the sources do not fix the year. Scholars still discuss which notes came from Servius himself and which were added by later hands.",
        rare = true,
    ),

)
