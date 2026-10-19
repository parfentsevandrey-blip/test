package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 840 to 850: Photios's Myriobiblon, a catalogue of about 280 books dated about 850. */
val db120Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.TOOL,
        section = "Byzantine",
        title = "Photios's Myriobiblon, summaries of about 280 books read for his brother Tarasios",
        originalTitle = "Myriobiblon",
        author = "Photios, scholar and later patriarch of Constantinople",
        year = 850,
        summary = "Photios's Myriobiblon is a catalogue of about 280 books, each with a summary and a judgment, written for his brother Tarasios around 850.",
        details = "Tarasios had asked for digests of the books Photios had read, so the work serves as a reader's guide to pagan and Christian writings, many of them now lost. The sources date the work roughly to 850 to 855, and about half of the titles it covers have vanished, so the summaries are often our only evidence for them.",
        rare = true,
    ),

)
