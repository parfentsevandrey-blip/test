package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1363: the English Statute of Apparel, which ruled dress by rank. */
val db68Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.PRACTICE,
        section = "Law",
        title = "The Statute of Apparel, dress limited by rank in England",
        originalTitle = "Statute of Apparel",
        author = "The English parliament",
        year = 1363,
        summary = "The Statute of Apparel of 1363 tried to regulate what clothing each rank of English society could wear.",
        details = "The statute set rules by rank and income for cloth, furs and jewellery, and it was one of several English sumptuary laws of the period. Its enforcement is hard to trace, so its effect on dress is uncertain.",
    ),

)
