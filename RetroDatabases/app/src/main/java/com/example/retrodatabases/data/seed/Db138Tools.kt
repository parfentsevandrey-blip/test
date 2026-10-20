package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 660 to 670: Li Chunfeng's Linde calendar, issued in 665 for the Tang court. */
val db138Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "The Linde calendar of the Tang, made by Li Chunfeng and issued in 665, which the Dayan calendar later replaced",
        originalTitle = "Linde li",
        author = "Li Chunfeng",
        year = 665,
        summary = "Li Chunfeng's Linde calendar, issued in 665, was a Tang reform that sharpened the forecasts of the sun, moon and planets.",
        details = "Li Chunfeng, who lived from 602 to 670, headed the imperial astronomy bureau, and accounts date the calendar's making to 664 and its issue to 665, so this record uses 665. The calendar was in use until the 720s, when Yi Xing's Dayan calendar replaced it.",
        rare = true,
    ),

)
