package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 178: Roman grain tokens of the imperial period, a placed record whose year is the sale of offices under Emperor Ling. */
val db187Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.TOOL,
        section = "Grain",
        title = "Roman grain tokens, tesserae that let a holder claim a share of the state grain",
        originalTitle = "tesserae frumentariae",
        author = "The Roman officials who issued them",
        year = 178,
        summary = "Roman officials issued grain tokens that let a holder collect a fixed measure of grain or a sum of cash at a distribution, and tokens of this kind belong to the imperial period.",
        details = "This is a placed record: no single date is given for tokens of this type, so 178 is the year of the anchor, the sale of offices, and not the year any token was made. The tokens were made of several materials, and scholars still disagree about their form, so the record names no one material.",
        rare = true,
    ),

)
