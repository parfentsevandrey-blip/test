package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 730 to 740: the stirrup in Western cavalry, which the sources do not date and which is placed at 732, the year of Tours. */
val db131Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.DEVICE,
        section = "Frankish",
        title = "The stirrup in Western cavalry, undated in the sources and placed at the year of Tours",
        originalTitle = "Stirrup",
        author = "Unknown makers",
        year = 732,
        summary = "The stirrup entered Western cavalry in the early Middle Ages, and this record is placed at 732, the year of Tours, because the sources do not date its arrival.",
        details = "Lynn White argued in 1962 that the stirrup helped make the armoured horseman of the Frankish wars, and that Charles Martel's campaigns were its setting; other scholars date its arrival earlier or later. The sources do not date this device, so 732 is the year of the anchor, the battle of Tours, and not the year of the stirrup itself.",
        rare = true,
    ),

)
