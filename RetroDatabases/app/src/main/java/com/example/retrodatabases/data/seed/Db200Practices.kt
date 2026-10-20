package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 40 to 50: the imperial offices for accounts, letters and petitions that Claudius's freedmen ran, placed at 41. */
val db200Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.PRACTICE,
        section = "Offices",
        title = "The imperial offices for accounts, letters and petitions, run by Claudius's freedmen",
        originalTitle = "a rationibus, ab epistulis, a libellis",
        author = "Claudius's freedmen",
        year = 41,
        summary = "Claudius's freedmen ran the imperial offices for accounts, letters and petitions, which kept the emperor's paperwork in separate hands, and 41 is the placed year of his accession.",
        details = "Pallas headed the office for accounts, Narcissus the office for correspondence, and in some accounts Callistus the office for petitions. The sources do not date the start of these offices, so 41 is the year of the anchor, Claudius's accession, not the year the offices were founded.",
        rare = true,
    ),

)
