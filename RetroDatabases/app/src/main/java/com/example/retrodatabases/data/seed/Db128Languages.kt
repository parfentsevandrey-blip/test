package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 760 to 770: the Abrogans, a Latin word list glossed in Old High German, dated about 765. */
val db128Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.LANGUAGE,
        section = "Old High German",
        title = "The Abrogans, a Latin word list glossed in Old High German, one of the earliest surviving texts in that language",
        originalTitle = "Abrogans",
        author = "Unknown compiler, probably at Freising",
        year = 765,
        summary = "The Abrogans is an alphabetical Latin word list with a Latin restatement and an Old High German gloss for each headword, dated between about 750 and 775.",
        details = "The surviving copy is a manuscript now at St Gall, and the glossary is usually linked with Freising. Accounts differ on its date, which runs from about 750 to about 775, and this record uses 765.",
        rare = true,
    ),

)
