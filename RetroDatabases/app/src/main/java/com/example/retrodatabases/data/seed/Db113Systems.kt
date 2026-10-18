package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 910 to 918: the Norman grant, the Southern Han state and the founding of Goryeo. */
val db113Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.SYSTEM,
        section = "France",
        title = "The treaty of Saint-Clair-sur-Epte, which gave Rollo land on the lower Seine in 911",
        originalTitle = "Traite de Saint-Clair-sur-Epte",
        author = "Charles the Simple and Rollo",
        year = 911,
        summary = "Charles the Simple granted Rollo the land around the lower Seine in 911, and that grant became the duchy of Normandy.",
        details = "No text of the agreement survives, so its terms are known only from later chronicles, which describe Rollo's homage to the king. The grant is treated as the starting point of the Norman duchy, and 911 is the usual date given for it.",
    ),

    entry(
        databaseId = 113,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Southern Han state, set up at Guangzhou by Liu Yan in 917",
        originalTitle = "Nan Han",
        author = "Liu Yan",
        year = 917,
        summary = "Liu Yan set up a state at Guangzhou in 917, later called Southern Han, which lasted until its last ruler surrendered to the Song in 971.",
        details = "Accounts give 917 as the year the state was set up, and it belongs to the Ten Kingdoms of south China that grew up after the Tang. Its last ruler, Liu Chang, surrendered to the Song in 971, which ended the state.",
        rare = true,
    ),

    entry(
        databaseId = 113,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "The founding of Goryeo by Wang Geon in 918, with its capital at Songak",
        originalTitle = "Goryeo",
        author = "Wang Geon",
        year = 918,
        summary = "Wang Geon overthrew Gung Ye in 918 and founded the kingdom of Goryeo, with its capital at Songak, later called Kaesong.",
        details = "Gung Ye had built a state in northern Korea, and his own officials and generals turned against him, so Wang Geon came to power in 918 and named the new state Goryeo. Songak is the place later known as Kaesong, and 918 is the usual date given for the founding.",
    ),

)
