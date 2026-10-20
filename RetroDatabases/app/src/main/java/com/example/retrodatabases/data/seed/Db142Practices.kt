package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 620 to 630: Xuanzang's journey to India for Buddhist texts, begun in 629 without an official travel permit. */
val db142Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 142,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "Xuanzang's departure from Chang'an for India in 629, made without an official permit",
        originalTitle = "Xuanzang's departure from Chang'an for India in 629, made without an official permit",
        author = "Xuanzang",
        year = 629,
        summary = "Xuanzang left the Tang capital in 629 to travel to India for Buddhist texts, without an official travel permit.",
        details = "He travelled overland through Central Asia and returned to Chang'an in 645 with a large number of Buddhist texts, which he then translated. The date of 629 is the usual one for the start of the journey.",
    ),

)
