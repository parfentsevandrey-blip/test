package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 290 to 300: Zhang Hua's Bowuzhi, a Chinese miscellany of marvels and learned notes, dated about 290. */
val db175Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Zhang Hua's Bowuzhi, a Chinese book of marvels, natural things and learned notes",
        originalTitle = "Bowuzhi",
        author = "Zhang Hua",
        year = 290,
        summary = "Zhang Hua's Bowuzhi is a Chinese miscellany of strange lands, creatures, plants and learned notes, dated about 290.",
        details = "The reference works give its date only as about 290. The surviving text comes from Song and Ming copies in ten chapters, so it may differ from the book Zhang Hua first wrote.",
        rare = true,
    ),

)
