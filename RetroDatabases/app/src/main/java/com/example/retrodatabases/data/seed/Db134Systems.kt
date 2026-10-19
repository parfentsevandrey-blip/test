package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 700 to 710: the household registers dated 702, the earliest surviving ones, made for Mino and three Kyushu provinces. */
val db134Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The household registers of 702, the earliest surviving ones, made for Mino and three Kyushu provinces",
        originalTitle = "Koseki",
        author = "The provincial offices under the Taiho code",
        year = 702,
        summary = "The household registers dated 702 are the earliest surviving ones, recording families in Mino and in three Kyushu provinces under the Taiho code.",
        details = "The Taiho code is dated 701 in some accounts, while others give 702 or 703, so accounts differ on when the code and its registers took effect. The three Kyushu registers for Chikuzen, Buzen and Bungo form a group called the Saikaido koseki, and the Mino register follows a different layout although it was made in the same year.",
        rare = true,
    ),

)
