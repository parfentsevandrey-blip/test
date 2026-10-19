package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 810 to 820: Li Jifu's Yuanhe Map and Gazetteer of 813, a Tang reference work on the prefectures and counties of the empire. */
val db123Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.TOOL,
        section = "China",
        title = "Li Jifu's Yuanhe Map and Gazetteer of Prefectures and Counties, a Tang reference work",
        originalTitle = "Yuanhe junxian tuzhi",
        author = "Li Jifu",
        year = 813,
        summary = "Li Jifu's Yuanhe Map and Gazetteer of Prefectures and Counties, a reference work on the administrative divisions of the Tang empire, was completed in 813.",
        details = "The work describes the prefectures and counties of the empire during the Yuanhe era, which ran from 806 to 820. The year 813 is the usual date of completion, and the title names the maps that formed part of the original work.",
        rare = true,
    ),

)
