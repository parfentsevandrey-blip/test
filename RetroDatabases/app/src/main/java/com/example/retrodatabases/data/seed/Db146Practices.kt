package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 580 to 590: Shima, later called Zenshin-ni, who took Buddhist vows as a nun in 584 as the Nihon Shoki records it. */
val db146Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "Shima, later called Zenshin-ni, taking Buddhist vows as a nun in 584, as the Nihon Shoki records it",
        originalTitle = "Zenshin-ni",
        author = "Recorded in the Nihon Shoki",
        year = 584,
        summary = "The Nihon Shoki records that in 584 a young girl named Shima took Buddhist vows as a nun, and later accounts count her as the earliest Japanese nun.",
        details = "The account is short, and it places the vows in the thirteenth year of Emperor Bidatsu, which is 584, when the girl was about eleven. Her family had ties to the Soga clan, which championed Buddhism at court. Accounts differ on her age, which some give as twelve, and on the date of her journey to Korea, which some give as 586 and others as 588.",
        rare = true,
    ),

)
