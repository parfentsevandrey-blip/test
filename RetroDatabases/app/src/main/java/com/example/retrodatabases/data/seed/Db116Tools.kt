package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 880 to 890: Photius's Nomocanon, a handbook that set church canons beside the civil law, dated about 883. */
val db116Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.TOOL,
        section = "Byzantium",
        title = "Photius's Nomocanon, a handbook that set church canons beside the civil law",
        originalTitle = "Nomokanon",
        author = "Photius, patriarch of Constantinople",
        year = 883,
        summary = "Photius, patriarch of Constantinople, compiled the Nomocanon about 883 as a handbook that set church canons beside the imperial civil law.",
        details = "The handbook is arranged in fourteen titles, and its date of about 883 is the one usually given. It was made for church judges, who needed the canons and the civil rules side by side when they heard cases. It remained in use for centuries in the Byzantine and Slavic churches.",
        rare = true,
    ),

)
