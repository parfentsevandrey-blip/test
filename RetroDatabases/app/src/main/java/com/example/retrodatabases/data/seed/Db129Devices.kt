package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 750 to 760: the five-stringed biwa of the Shoso-in, placed at the 756 gift of Emperor Shomu's treasures to Todai-ji. */
val db129Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.DEVICE,
        section = "Nara",
        title = "The five-stringed biwa of the Shoso-in, a Nara court lute, placed at the 756 gift of the treasures",
        originalTitle = "Gogen no biwa",
        author = "Unknown maker",
        year = 756,
        summary = "The five-stringed biwa kept in the Shoso-in treasure house is a Nara court lute, placed here at 756, the year of the gift of Emperor Shomu's objects.",
        details = "The sources do not date this instrument, so 756 is the year of the anchor, the gift to Todai-ji of Shomu's objects, and not the year of the making. Lutes of this kind reached Japan from China, and the record names no maker.",
        rare = true,
    ),

)
