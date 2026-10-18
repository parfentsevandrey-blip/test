package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1041 and 1044: Bi Sheng's movable type of baked clay in Qingli China and the south-pointing fish of the Wujing Zongyao. */
val db100Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Bi Sheng's movable type of baked clay, described by Shen Kuo",
        originalTitle = "Bi Sheng huozi",
        author = "Bi Sheng",
        year = 1041,
        summary = "Bi Sheng made movable type of baked clay, carved one character to a piece, hardened it by firing and set it in an iron frame for printing.",
        details = "Shen Kuo's Dream Pool Essays is the main account, and it places the invention in the Qingli years of 1041 to 1048, while other accounts give 1039 or about 1040 as the start. Shen noted that clay type was slow for a few copies but very fast for runs of hundreds or thousands.",
    ),

    entry(
        databaseId = 100,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The south-pointing fish of the Wujing Zongyao, an iron fish that turned south in water",
        originalTitle = "zhinan yu",
        author = "Zeng Gongliang, Ding Du and Yang Weide",
        year = 1044,
        summary = "The Wujing Zongyao describes an iron fish that floated in water and lined up with the south, one of the earliest written descriptions of a magnetic direction-finder in China.",
        details = "The compendium was composed about 1040 to 1044, and the fish appears in the same book as its gunpowder formulas. The fish is more than two centuries older than the pivoted magnetic compass that Peter Peregrinus described in 1269.",
        rare = true,
    ),

)
