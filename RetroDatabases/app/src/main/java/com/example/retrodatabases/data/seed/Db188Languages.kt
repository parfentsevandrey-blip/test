package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 160 to 170: Gaius's Institutes, a Latin textbook of Roman law in four books, written about 161. */
val db188Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gaius's Institutes, a Latin textbook of Roman law in four books",
        originalTitle = "Institutiones",
        author = "Gaius",
        year = 161,
        summary = "Gaius wrote the Institutes, a Latin textbook of Roman law in four books, about the year 161, and the text lay under Jerome's letters until a palimpsest turned up in 1816.",
        details = "Sources give the composition as about 160 to 162, so 161 is a round date and not a fixed one. The main witness is a palimpsest in the chapter library at Verona, which Barthold Georg Niebuhr found in 1816, and papyrus fragments add to the surviving text.",
        rare = true,
    ),

)
