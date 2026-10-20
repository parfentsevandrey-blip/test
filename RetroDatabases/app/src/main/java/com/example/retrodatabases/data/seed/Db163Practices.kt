package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 410 to 418: a joint rendering of a monastic code into Chinese at Jiankang, by an Indian monk and a Chinese pilgrim. */
val db163Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.PRACTICE,
        section = "Translation",
        title = "Buddhabhadra and Faxian's joint rendering of a monastic code",
        originalTitle = "Mohesengqi lu",
        author = "Buddhabhadra and Faxian",
        year = 416,
        summary = "The Indian monk Buddhabhadra and the Chinese pilgrim Faxian worked together at Jiankang to put an Indian monastic code into Chinese.",
        details = "The code is the Mahasamghika Vinaya, the monastic rule of one of the early Buddhist schools, and its Chinese version of forty fascicles is credited to the two men working together. The rendering is usually dated to 416 to 418, and this record uses 416 as its year.",
        rare = true,
    ),

)
