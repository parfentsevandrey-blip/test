package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 420 to 430: Kou Qianzhi's New Code of the Celestial Masters, revealed in 415 and presented at the Northern Wei court in 424. */
val db162Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.PRACTICE,
        section = "Religion",
        title = "Kou Qianzhi's New Code of the Celestial Masters, revealed in 415 and presented at the Northern Wei court in 424",
        originalTitle = "New Code of the Celestial Masters",
        author = "Kou Qianzhi",
        year = 424,
        summary = "Kou Qianzhi presented his New Code of rules for the Celestial Masters, a Daoist community, to the Northern Wei court in 424, after a revelation of the code in 415.",
        details = "Kou said that the code was revealed to him by the sage Laozi, and a scripture of 423, now lost, came next before the writings reached the court in 424. Accounts differ on how far the Northern Wei rulers adopted the code.",
        rare = true,
    ),

)
