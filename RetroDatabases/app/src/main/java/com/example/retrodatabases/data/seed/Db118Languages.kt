package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 860 to 870: the Glagolitic alphabet, made for the Slavic mission to Moravia from 863. */
val db118Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 118,
        kind = Kind.LANGUAGE,
        section = "Slavic",
        title = "The Glagolitic alphabet, the letters made for the mission to Moravia",
        originalTitle = "Glagolitsa",
        author = "Constantine (Cyril) and Methodius",
        year = 863,
        summary = "The Glagolitic alphabet, the script of the Slavic mission to Moravia, is dated here to 863, the year the mission began its work there.",
        details = "Accounts credit Constantine, called Cyril, as the script's main creator, with Methodius helping, but its origin is unclear. The dates differ: some accounts put Rastislav's request for teachers in 862, and most give 863 for the start of the Moravian work.",
    ),

)
