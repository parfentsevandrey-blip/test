package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 120 to 130: Zhang Heng's armillary sphere, undated in the sources and placed at 120 for the date of his Lingxian. */
val db192Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Zhang Heng's armillary sphere, undated in the sources and placed at 120",
        originalTitle = "Hunyi",
        author = "Zhang Heng",
        year = 120,
        summary = "Zhang Heng is credited with a hunyi, an armillary sphere that later writers describe as turned by water, and the year 120 is the anchor, not the making.",
        details = "The sources do not date this device; 120 is the year of the anchor, the approximate completion of Zhang Heng's Lingxian, not of the making. Zhang Heng's commentary on the sphere is put at about 117, outside this decade, so the record does not use that date.",
        rare = true,
    ),

)
