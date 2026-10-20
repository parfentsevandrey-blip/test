package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 590 to 600: the Irish hand-bell, which the sources do not date and which is placed at 597, the year Columba died on Iona. */
val db145Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.DEVICE,
        section = "Monastic",
        title = "The Irish hand-bell rung by hand at services, undated in the sources and placed at 597, the year Columba died on Iona",
        originalTitle = "clog, the Irish hand-bell",
        author = "Irish monastic makers",
        year = 597,
        summary = "The Irish hand-bell was a small metal bell rung by hand in early Irish churches and monasteries, and this record is placed at 597 because the sources do not date the device.",
        details = "The sources do not date this device, so 597 is the year of the anchor, Columba's death on Iona, and not the year of the making. No particular bell of this kind is tied to any date in the sources.",
        rare = true,
    ),

)
