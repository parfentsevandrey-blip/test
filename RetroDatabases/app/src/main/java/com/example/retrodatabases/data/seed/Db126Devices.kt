package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Device of 780 to 790: the Persian wheel of the Abbasid lands, placed at 785 because its first use there is not dated. */
val db126Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.DEVICE,
        section = "Abbasid",
        title = "The Persian wheel, a bucket wheel that lifted irrigation water in the Abbasid lands, whose first use there is undated",
        originalTitle = "Na'ura",
        author = "Unknown builders of the Abbasid irrigation works",
        year = 785,
        summary = "The Persian wheel, a bucket wheel that lifted water for irrigation in the Abbasid lands, is undated in the sources, so 785 is only the year of an anchor event.",
        details = "The sources do not date this device; 785 is the year of the anchor, the accession of the caliph al-Hadi, not of the making. Such wheels were common in Mesopotamia and Iran, but this record cannot fix the date of any particular wheel.",
        rare = true,
    ),

)
