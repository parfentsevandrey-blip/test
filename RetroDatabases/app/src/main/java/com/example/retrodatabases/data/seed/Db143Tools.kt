package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 610 to 620: the Wuyin calendar of Fu Renjun, the calendar of the early Tang dynasty. */
val db143Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "The Wuyin calendar of Fu Renjun, the calendar adopted in the early Tang, with eclipse forecasts that drew criticism",
        originalTitle = "Wuyin yuanli",
        author = "Fu Renjun",
        year = 618,
        summary = "The Wuyin calendar, compiled by Fu Renjun, was adopted by the early Tang dynasty in 618.",
        details = "Accounts give 618, the first year of the Tang's Wude era, as the date of adoption, while one reference dates its rollout to 619. Li Chunfeng criticised its eclipse forecasts, and his own Linde calendar came out in 665.",
        rare = true,
    ),

)
