package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 470 to 480: the Inariyama sword, an iron blade with gold-inlaid writing from a burial mound in Japan. */
val db157Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.DEVICE,
        section = "Japan",
        title = "The Inariyama sword, an iron blade with gold-inlaid writing from a burial mound in Japan",
        originalTitle = "Inariyama tekken",
        author = "Unknown smiths and scribes",
        year = 471,
        summary = "The Inariyama sword is an iron blade from a burial mound whose gold-inlaid writing names a great king and gives a date usually read as 471.",
        details = "The mound stands in Saitama Prefecture, and the writing runs to about 115 characters on both faces of the blade. It became legible only after X-ray examination showed the inlay under corrosion, and most Japanese scholars read the date as 471. Others propose 531, a sixty-year repeat of the same cycle label, so the year is disputed.",
        rare = true,
    ),

)
