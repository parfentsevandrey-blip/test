package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 480 to 490: the signet ring of the Frankish king Childeric, placed at 481, the year of his death. */
val db156Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.DEVICE,
        section = "Seals",
        title = "Childeric's signet ring, inscribed CHILDIRICI REGIS, found in his grave at Tournai in 1653",
        originalTitle = "CHILDIRICI REGIS",
        author = "Childeric I of the Franks, the ring's owner",
        year = 481,
        summary = "A gold signet ring inscribed with the name of the Frankish king Childeric was found in his grave at Tournai in 1653, and it is now kept in Paris.",
        details = "The sources do not date this ring; 481 is the year of the anchor, the death of Childeric, not of the making. The inscription names the king in the Latin genitive, so the ring is read as his seal.",
        rare = true,
    ),

)
