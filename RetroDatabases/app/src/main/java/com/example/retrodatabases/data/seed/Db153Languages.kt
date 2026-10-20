package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 510 to 520: the Zabad lintel of 512, a Greek, Syriac and Paleo-Arabic inscription from a Syrian martyrium. */
val db153Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The Zabad lintel of 512, a trilingual inscription in Greek, Syriac and Paleo-Arabic from a martyrium in Syria",
        originalTitle = "Zabad inscription",
        author = "Unnamed donors of the Saint Sergius martyrium at Zabad",
        year = 512,
        summary = "A lintel at a martyrium of Saint Sergius in northern Syria carries one text in Greek, Syriac and Paleo-Arabic, and its Greek part gives the year 512.",
        details = "The Arabic is written in Paleo-Arabic letters, a script that grew out of Nabataean writing, and the date 512 comes from the Greek part alone. A table of Paleo-Arabic texts gives the Hima group dates as 470 and 513, which would weaken any claim that Zabad is the earliest dated Arabic text, so that priority is disputed.",
        rare = true,
    ),

)
