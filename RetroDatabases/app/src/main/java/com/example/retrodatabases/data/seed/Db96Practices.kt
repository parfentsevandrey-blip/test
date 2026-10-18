package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1080 to 1090: the notes in which Sima Guang set out the disagreements among his sources. */
val db96Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.PRACTICE,
        section = "Historiography",
        title = "Sima Guang's kaoyi notes, which weigh the conflicting accounts of one event",
        originalTitle = "kaoyi",
        author = "Sima Guang",
        year = 1084,
        summary = "Sima Guang kept notes called kaoyi that set out where his sources disagreed about an event and gave his reasons for the account he followed.",
        details = "The notes were compiled alongside the chronicle, so that readers could see where the accounts differ and why one was chosen. Recording the disagreement itself, rather than only the account that was chosen, was the practice.",
        rare = true,
    ),

)
