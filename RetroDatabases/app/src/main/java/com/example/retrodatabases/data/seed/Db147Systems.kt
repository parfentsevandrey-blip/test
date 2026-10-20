package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 570 to 580: the Northern Zhou campaign against Buddhism and Leovigild's revised code of Visigothic law. */
val db147Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 147,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "The Northern Zhou campaign against Buddhism, decreed in 574 and renewed in 577",
        originalTitle = "The Northern Zhou campaign against Buddhism, decreed in 574 and renewed in 577",
        author = "Emperor Wu of the Northern Zhou",
        year = 574,
        summary = "Emperor Wu of the Northern Zhou closed Buddhist and Daoist temples and sent their clergy back to lay life in 574, and renewed the order in 577.",
        details = "Religious images were wrecked and temple land was given to soldiers, and the 577 renewal followed the conquest of Northern Qi and reached its former lands. The campaign ended with Emperor Wu's death in 578.",
    ),

    entry(
        databaseId = 147,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "Leovigild's revised code of Visigothic law, reported by Isidore of Seville and now lost",
        originalTitle = "Leovigild's revised code of Visigothic law, reported by Isidore of Seville and now lost",
        author = "Leovigild, king of the Visigoths",
        year = 573,
        summary = "Leovigild, king of the Visigoths, is reported by Isidore of Seville to have revised the law of his people in a new code, a text that is now lost.",
        details = "Some scholars doubt Isidore's report, and accounts differ on the date, between the late 500s and 586, with 573 given in one modern reference and a span of 572 to 586 in another; 573 is the year used here. No text of the code survives, so the record rests on Isidore's account alone.",
        rare = true,
    ),

)
