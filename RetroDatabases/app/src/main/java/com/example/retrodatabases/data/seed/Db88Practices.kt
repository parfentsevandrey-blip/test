package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1160 to 1170: the hand-copied Heike sutra set dedicated to the Itsukushima shrine in 1164. */
val db88Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.PRACTICE,
        section = "Devotion",
        title = "The Heike nokyo, a set of sutras copied out and dedicated in 1164",
        originalTitle = "Heike nokyo",
        author = "Taira no Kiyomori and his family",
        year = 1164,
        summary = "In 1164 the Taira family dedicated a set of Buddhist sutras, copied out by hand, to the Itsukushima shrine.",
        details = "Copying a sutra by hand was a devotional act, and the set was made as an offering rather than as a book for study. The dedication is usually dated to 1164, and the set takes its name from the Taira, who were called the Heike.",
    ),

)
