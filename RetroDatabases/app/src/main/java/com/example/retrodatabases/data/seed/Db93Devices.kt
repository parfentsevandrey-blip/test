package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1110 to 1120: a magnetized needle for sea navigation, noted in a Chinese book of 1119. */
val db93Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Zhu Yu's magnetized needle at sea, noted in the Pingzhou Ketan",
        originalTitle = "Pingzhou Ketan",
        author = "Zhu Yu",
        year = 1119,
        summary = "Zhu Yu's Pingzhou Ketan of 1119 describes a sailor who consults a magnetized needle in dark weather, when the stars and sun cannot guide the ship.",
        details = "The book was composed in the years 1111 to 1117 and published in 1119, although some accounts give 1117 as its date. Its passage on the needle is often cited as the earliest text on the needle in sea navigation, a claim of priority that needs checking against the Chinese text.",
        rare = true,
    ),

)
