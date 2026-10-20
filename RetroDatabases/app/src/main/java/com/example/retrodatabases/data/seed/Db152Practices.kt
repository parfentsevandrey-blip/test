package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 520 to 530: the robe colours that marked court rank in Silla, set with the law code of 520. */
val db152Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.PRACTICE,
        section = "Korean",
        title = "Silla's official robe colours, which marked court rank, set with the law code of 520",
        originalTitle = "gongbok (official robes)",
        author = "King Beopheung's court of Silla",
        year = 520,
        summary = "In 520 Silla's court tied the colour of an official's robe to his rank, so that the robe showed his place in the court.",
        details = "Silla's laws and its rules for official robes were set in the seventh year of King Beopheung, which is 520. Later accounts give the rank colours in the order purple, scarlet, blue and yellow. The date is known from a chronicle written about 1145, more than six centuries later.",
        rare = true,
    ),

)
