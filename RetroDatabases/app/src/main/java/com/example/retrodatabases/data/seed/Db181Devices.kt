package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 230 to 240: Zhuge Liang's wooden oxen and gliding horses, supply carriers dated to 231 in this record. */
val db181Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.DEVICE,
        section = "Supply",
        title = "Zhuge Liang's wooden oxen and gliding horses, carriers for army supplies",
        originalTitle = "Muniu liuma",
        author = "Zhuge Liang",
        year = 231,
        summary = "Zhuge Liang's wooden oxen and gliding horses were supply carriers that Chinese accounts credit to him for moving grain to the front in the early 230s.",
        details = "The accounts give 230 or 231 for the device, and this record uses 231, the year of his Qishan campaign. Later descriptions disagree about how the carriers worked, and some accounts may have exaggerated the original design, so no working model is given here.",
        rare = true,
    ),

)
