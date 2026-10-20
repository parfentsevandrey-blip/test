package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 590 to 600: Zhiyi's method of calm and insight in the Mohe zhiguan, taught in lectures of 594 and recorded by Guanding. */
val db145Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "Zhiyi's Mohe zhiguan, the method of calm and insight taught in lectures of 594",
        originalTitle = "Mohe zhiguan",
        author = "Zhiyi, recorded by Guanding",
        year = 594,
        summary = "Zhiyi's Mohe zhiguan sets out a meditation practice that pairs calm (samatha) with insight (vipasyana), taught in lectures that he gave in 594.",
        details = "Zhiyi gave the lectures at the Yuquan temple near Jingzhou in 594, and his disciple Guanding recorded and edited them into the text. Its final form is placed between 606 and 632, so 594 is the year of the teaching and not of the book.",
        rare = true,
    ),

)
