package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1092: Su Song's water-driven astronomical clock tower at Kaifeng, with its armillary sphere and striking figures. */
val db95Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Su Song's water-driven clock tower at Kaifeng, completed in 1092",
        originalTitle = "Shuiyun yixiang tai",
        author = "Su Song and the engineer Han Gonglian",
        year = 1092,
        summary = "Su Song's clock tower at Kaifeng was completed in 1092, and a water wheel drove its armillary sphere, its celestial globe and figures that struck bells to mark the hours.",
        details = "The tower was built in the Northern Song capital over several years, and it used an escapement to keep its drive steady, with the work usually credited to Su Song and the engineer Han Gonglian. The tower did not survive the Jurchen conquest of Kaifeng, and it is known now mainly from the book describing its parts.",
        rare = true,
    ),

)
