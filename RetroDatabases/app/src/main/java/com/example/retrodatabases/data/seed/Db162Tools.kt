package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 420 to 430: Augustine's Retractations, his review and correction of his own writings in the last years of his life. */
val db162Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Augustine's Retractations, a review and correction of his own writings, written about 427",
        originalTitle = "Retractationes",
        author = "Augustine of Hippo",
        year = 427,
        summary = "Augustine's Retractations is a review of his own writings, with corrections and comments on each work, written in the last years of his life.",
        details = "The book is in two parts and was written in the years around 426 and 427, and 427 is the year used here. It gives later readers an ordered account of Augustine's works and records where he changed his earlier views.",
        rare = true,
    ),

)
