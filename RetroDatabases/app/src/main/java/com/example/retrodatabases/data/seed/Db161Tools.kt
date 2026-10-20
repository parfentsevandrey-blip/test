package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 433: Prosper of Aquitaine's Epitome of the Chronicle, a Latin world history first drafted in 433 and revised up to 455. */
val db161Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.TOOL,
        section = "Chronology",
        title = "Prosper of Aquitaine's Epitome of the Chronicle, a Latin history first drafted in 433",
        originalTitle = "Epitoma chronicon",
        author = "Prosper of Aquitaine",
        year = 433,
        summary = "Prosper of Aquitaine first drafted his Epitome of the Chronicle in 433, a Latin abridgment of world history that was later revised up to 455.",
        details = "Prosper was a Gallic writer who defended the teaching of Augustine on grace, and his epitome abridges the universal chronicle of Jerome and carries it forward in time. No surviving copy keeps the 433 text alone, so 433 is the date of the first draft and not of any surviving manuscript.",
        rare = true,
    ),

)
