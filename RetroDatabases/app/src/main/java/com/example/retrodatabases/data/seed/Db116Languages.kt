package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 880 to 890: the Sequence of Saint Eulalia of about 880 in Old French, the Ludwigslied of 881 in Old High German, and Choe Chiwon's Gyewon pilgyong of 886 in Classical Chinese. */
val db116Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.LANGUAGE,
        section = "Old French",
        title = "The Sequence of Saint Eulalia, one of the earliest surviving texts in Old French",
        originalTitle = "Sequence de sainte Eulalie",
        author = "Anonymous; the place of writing is disputed",
        year = 880,
        summary = "The Sequence of Saint Eulalia, a short poem in early Old French about a Christian martyr, dates from about 880 and is one of the oldest texts in a Romance language.",
        details = "Its date is usually given as about 880, with a window of 880 to 881, and scholars link it to the years after relics of a saint named Eulalia came to light in 878. It survives in Valenciennes Codex 150, the same book that holds the Ludwigslied, and the place where it was written is disputed between Saint-Amand and Lower Lotharingia. It is a rare early witness to the vernacular of northern France.",
        rare = true,
    ),

    entry(
        databaseId = 116,
        kind = Kind.LANGUAGE,
        section = "Old High German",
        title = "The Ludwigslied, a German song on King Louis III's victory over the Northmen",
        originalTitle = "Ludwigslied",
        author = "An anonymous poet writing in Old High German",
        year = 881,
        summary = "The Ludwigslied, a short poem in Old High German, praises King Louis III for his victory over the Northmen at Saucourt in 881.",
        details = "The poem survives in one manuscript, Valenciennes Codex 150, which came from the abbey of Saint-Amand and also holds the Sequence of Saint Eulalia. It speaks of Louis as still alive, and he died in August 882, so it was written within about a year of the battle. Its hero was a West Frankish king, yet the poem is written in Old High German, one of the earliest long poems in that language.",
        rare = true,
    ),

    entry(
        databaseId = 116,
        kind = Kind.LANGUAGE,
        section = "Silla",
        title = "Choe Chiwon's Gyewon pilgyong, a Silla collection of prose and verse in Classical Chinese",
        originalTitle = "Gyewon pilgyong",
        author = "Choe Chiwon",
        year = 886,
        summary = "Choe Chiwon's Gyewon pilgyong, a collection of his prose and verse in Classical Chinese from his years in the Tang, was assembled in Silla in 886.",
        details = "Choe Chiwon had gone to the Tang as a youth, served the Tang general Gao Pian, and returned to Silla in 885, and the collection of his Tang-period writing, including sixty original poems, was put together in the year after his return. The year 886 is the one usually given for the collection. It is one of the best-known surviving works of Silla literature in Classical Chinese.",
        rare = true,
    ),

)
