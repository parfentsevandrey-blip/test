package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 400 to 410: the White Lotus Society of Mount Lu, whose members pledged to be reborn in the Western Paradise. */
val db164Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.PRACTICE,
        section = "Devotion",
        title = "The White Lotus Society of Mount Lu, a pledge to be reborn in the Western Paradise",
        originalTitle = "Bailian she",
        author = "Huiyuan of Mount Lu",
        year = 402,
        summary = "In 402 the monk Huiyuan gathered a group at Donglin monastery on Mount Lu, whose members pledged before an image of Amitabha to be reborn in the Western Paradise.",
        details = "Accounts say that 123 people made the pledge, and their devotion centred on reciting the name of Amitabha. Later tradition honours Huiyuan as the first patriarch of the Pure Land school, but historians dispute whether the society led directly to later Pure Land Buddhism, and the White Lotus name was also used by a later Maitreya movement.",
        rare = true,
    ),

)
