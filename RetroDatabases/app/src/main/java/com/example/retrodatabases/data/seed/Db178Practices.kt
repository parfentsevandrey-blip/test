package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 267: Pei Xiu's six principles of mapmaking, set out with a grid of squares for the Tribute of Yu map. */
val db178Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.PRACTICE,
        section = "Maps",
        title = "Pei Xiu's six principles of mapmaking, built on a scaled grid",
        originalTitle = "Yugong diyutu",
        author = "Pei Xiu",
        year = 267,
        summary = "Pei Xiu, appointed Minister of Works in 267, set out six principles of mapmaking: scale, direction, road distances, heights, slanted distances and detours.",
        details = "His preface to the Yugong diyutu, the map of the Tribute of Yu, survives in volume 35 of the Book of Jin, while the eighteen silk sheets of the map itself have not survived. The year 267 is the year of his appointment, and the principles are dated here to about that time. Pei claimed that a grid of squares kept distance ratios, angles and curved routes accurate against straight ones.",
        rare = true,
    ),

)
