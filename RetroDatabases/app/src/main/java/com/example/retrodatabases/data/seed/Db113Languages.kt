package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 910 to 920: one universal history written in Arabic, arranged year by year. */
val db113Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.LANGUAGE,
        section = "Iraq",
        title = "Al-Tabari's History of the Prophets and Kings, a universal chronicle in Arabic",
        originalTitle = "Ta'rikh al-rusul wa'l-muluk",
        author = "Muhammad ibn Jarir al-Tabari",
        year = 915,
        summary = "Al-Tabari finished his universal history of prophets and kings in Arabic at about 915, with the events of each year set under that year.",
        details = "The history runs from the Creation to the year 302 AH, which falls in about 915, and Tabari arranged it year by year as annals. The usual date given for its end is 915, and Tabari died in 923.",
    ),

)
