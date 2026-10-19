package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Device of 720 to 730: the water-driven armillary sphere of Yi Xing and Liang Lingzan, dated to 725 with a dispute over 723. */
val db132Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.DEVICE,
        section = "Chinese",
        title = "Yi Xing and Liang Lingzan's water-driven armillary sphere of 725, described with an escapement",
        originalTitle = "Shuiyun hunyi",
        author = "Yi Xing and Liang Lingzan",
        year = 725,
        summary = "Yi Xing and Liang Lingzan built a water-driven armillary sphere, dated here to 725 and described with an escapement, and the original no longer survives.",
        details = "Accounts describe a water wheel driving the sphere and its globe, and they cite its escapement as one of the earliest known. Some accounts date the sphere to 723 and others to 725, and some credit Zhang Heng of the Han with the first use of water power on an armillary sphere, so the credit is disputed.",
        rare = true,
    ),

)
