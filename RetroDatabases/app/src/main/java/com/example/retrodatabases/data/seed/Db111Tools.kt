package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 934: a Japanese dictionary sorted by subject, the Wamyo Ruijusho, which lets a reader look up a word under its heading. */
val db111Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.TOOL,
        section = "Japan",
        title = "Wamyo Ruijusho, a Japanese dictionary sorted by subject, with Chinese-character glosses",
        originalTitle = "Wamyo Ruijusho",
        author = "Minamoto no Shitago",
        year = 934,
        summary = "Minamoto no Shitago's Wamyo Ruijusho, a Japanese dictionary sorted into subject headings with Chinese-character glosses, was begun in 934.",
        details = "Accounts differ on the date: a 2019 book chapter gives about 933, a 2018 scholarly article gives about 934 for the start of the work, and the common reference date for it is 938. The start of 934 is used here, since the compilation was begun at the request of a daughter of Emperor Daigo, and Shitago lived from 911 to 983.",
        rare = true,
    ),

)
