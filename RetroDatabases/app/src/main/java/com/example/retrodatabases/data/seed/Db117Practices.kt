package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 870 to 880: the Tang jinshi examination, which the Silla scholar Choe Chiwon passed in 874. */
val db117Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.PRACTICE,
        section = "Korea",
        title = "The Tang jinshi examination, which the Silla scholar Choe Chiwon passed in 874",
        originalTitle = "Jinshi ke",
        author = "Tang examiners",
        year = 874,
        summary = "The jinshi examination was the Tang test of literary skill for civil office, and the Silla scholar Choe Chiwon passed it in 874.",
        details = "Choe Chiwon had gone to Chang'an while still young, and the pass let him take Tang posts before he returned to Silla in 885. Accounts differ on his age at the pass, so no age is given here, and the year 874 is the one usually cited.",
        rare = true,
    ),

)
