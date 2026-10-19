package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 810 to 820: the esoteric initiation that Kukai gave Saicho at Takaosan-ji in 812, and the quarrel over its final rite. */
val db123Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "The esoteric initiation that Kukai gave Saicho at Takaosan-ji in 812",
        originalTitle = "Kanjo at Takaosan-ji",
        author = "Kukai and Saicho",
        year = 812,
        summary = "In 812 the monk Kukai gave the monk Saicho esoteric initiation, or kanjo, at Takaosan-ji near Kyoto, but he withheld the final rite.",
        details = "The introductory rite and a second level of initiation were given at the temple, and Kukai withheld the final rite, which would have made Saicho a master, because Saicho's studies were not complete. Accounts differ on whether Saicho had received an earlier initiation at the same temple in 805, and the quarrel between the two monks was never resolved.",
        rare = true,
    ),

)
