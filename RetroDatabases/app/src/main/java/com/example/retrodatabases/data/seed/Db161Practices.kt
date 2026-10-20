package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 434: the first ordination of Buddhist nuns in East Asia, held at Nanjing with visiting nuns from Sri Lanka. */
val db161Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.PRACTICE,
        section = "Ordination",
        title = "The first ordination of Buddhist nuns in East Asia, held at Nanjing in 434",
        originalTitle = "Bhiksuni ordination",
        author = "Visiting nuns from Sri Lanka and the Nanjing community",
        year = 434,
        summary = "In 434 Chinese women took full Buddhist ordination at Nanjing, with visiting Sri Lankan nuns present as the witnesses that the rite required.",
        details = "Accounts give the ordination as 434 at Nanjing, and some date it to 433, so the record uses 434, the year most accounts give. Chinese women could not yet be ordained by a community of nuns alone, so the visiting nuns from Sri Lanka supplied the witnesses that a dual-community ordination needed.",
        rare = true,
    ),

)
