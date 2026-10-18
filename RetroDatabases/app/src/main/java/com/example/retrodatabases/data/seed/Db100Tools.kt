package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1044: the Wujing Zongyao, a Song military compendium with some of the oldest written gunpowder formulas. */
val db100Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.TOOL,
        section = "Military",
        title = "The Wujing Zongyao, a Song military compendium with some of the oldest written gunpowder formulas",
        originalTitle = "Wujing Zongyao",
        author = "Zeng Gongliang, Ding Du and Yang Weide",
        year = 1044,
        summary = "The Wujing Zongyao, or Complete Essentials of the Military Classics, is a Song compendium of military techniques that includes some of the oldest written gunpowder formulas.",
        details = "The compilation is dated to about 1040 to 1044, and its gunpowder formulas appear by 1044. The recipes mix saltpetre, sulphur and charcoal with other materials, and the same book describes a floating iron fish that points south.",
        rare = true,
    ),

)
