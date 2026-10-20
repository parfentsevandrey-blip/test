package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 560 to 570: Corippus's Latin panegyric in hexameters for Justin II, who came to the throne in 565. */
val db148Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Corippus's In praise of Justin, a Latin panegyric in four books of hexameter verse for the court of Justin II",
        originalTitle = "In laudem Iustini Augusti minoris, libri IV",
        author = "Corippus",
        year = 566,
        summary = "Corippus wrote this Latin panegyric in hexameter verse for the court of Justin II, who came to the throne in 565.",
        details = "The poem covers Justin II's accession and the first days of his rule, and part of his consular investiture, which fell in 566, so 566 is the year used here. A catalogue gives the poem a date range from 550 to 567, and the text is unfinished.",
        rare = true,
    ),

)
