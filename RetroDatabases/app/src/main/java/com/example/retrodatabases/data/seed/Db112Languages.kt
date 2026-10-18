package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 920 to 924: the Khitan large script and the Khitan small script, two writing systems of the Khitan court. */
val db112Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.LANGUAGE,
        section = "Khitan",
        title = "The Khitan large script, a writing system devised under the Khitan ruler Abaoji",
        originalTitle = "Qidan dazi",
        author = "Abaoji and the Khitan court",
        year = 920,
        summary = "The Khitan large script, built from Chinese characters, is dated by most accounts to 920, when the Khitan ruler Abaoji had a writing system made for the Khitan language.",
        details = "Most accounts place its origin in 920 under Abaoji, and a recent study argues that it grew out of earlier writing and may predate that year. Its characters were built from Chinese components, and a second script followed it in the mid 920s.",
        rare = true,
    ),

    entry(
        databaseId = 112,
        kind = Kind.LANGUAGE,
        section = "Khitan",
        title = "The Khitan small script, devised after the large script in the mid 920s",
        originalTitle = "Qidan xiaozi",
        author = "A younger son of Abaoji, credited with its devising",
        year = 924,
        summary = "The Khitan small script, a second writing system for the Khitan language, is dated to 924 or 925, a few years after the large script.",
        details = "Accounts give 924 or 925 for its devising and credit it to a younger son of Abaoji. It uses a smaller set of characters than the large script.",
        rare = true,
    ),

)
