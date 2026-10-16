package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1410 to 1420: keeping a merchant house's letters and account books at Prato. */
val db63Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.PRACTICE,
        section = "Records",
        title = "Keeping a merchant house's papers at Prato",
        originalTitle = "Archivio Datini",
        author = "Francesco di Marco Datini",
        year = 1410,
        summary = "Datini's merchant house kept its letters and account books, and the papers he left at his death in 1410 are now held at the Prato State Archive.",
        details = "Datini died at Prato on 16 August 1410, and his business papers, dated from 1363 to 1410, are kept at the Prato State Archive. Accounts differ on the count: one gives about 150,000 letters, another about 120,000, and the ledgers and account books are said to number more than 500.",
    ),

)
