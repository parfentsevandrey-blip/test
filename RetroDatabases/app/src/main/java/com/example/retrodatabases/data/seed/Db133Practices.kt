package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 710 to 720: the copying of a complete Latin Bible at Wearmouth and Jarrow, the Codex Amiatinus, carried toward Rome in 716. */
val db133Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.PRACTICE,
        section = "Latin",
        title = "The copying of a complete Latin Bible at Wearmouth and Jarrow, the Codex Amiatinus, sent toward Rome in 716",
        originalTitle = "Codex Amiatinus",
        author = "The scribes of Wearmouth and Jarrow under Abbot Ceolfrith",
        year = 716,
        summary = "The Codex Amiatinus, a complete Latin Bible copied at Wearmouth and Jarrow, was carried toward Rome in 716 as a gift for the pope, and its abbot Ceolfrith died on the way.",
        details = "Copying a whole Bible into one set of volumes was a costly practice, and the codex carries the Vulgate text in the form Jerome gave it. Ceolfrith died at Langres in 716, and the codex was carried on and is now held in Florence.",
    ),

)
