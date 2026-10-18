package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of about 970 and 971: the Shu edition of the Buddhist canon carved at Chengdu, and the Regularis concordia of Winchester. */
val db107Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.PRACTICE,
        section = "Sichuan",
        title = "The Shu canon, a Buddhist canon printed from woodblocks carved at Chengdu from 971 to 983",
        originalTitle = "Shu edition of the Chinese Buddhist canon",
        author = "The Song court and its Chengdu workshops",
        year = 971,
        summary = "The Chinese Buddhist canon was carved in woodblocks at Chengdu from 971, and the carving is usually dated to finish in 983.",
        details = "Most accounts put the start at 971 and the finish at 983, while one gives 972 as the start. The original printing no longer survives, and its contents are reconstructed from the Tang catalogue of Buddhist texts that it followed, the Kaiyuan Shijiao Lu.",
        rare = true,
    ),

    entry(
        databaseId = 107,
        kind = Kind.PRACTICE,
        section = "Anglo-Saxon England",
        title = "The Regularis concordia, the monastic customary drawn up at Winchester about 970",
        originalTitle = "Regularis concordia",
        author = "Aethelwold of Winchester",
        year = 970,
        summary = "The Regularis concordia, a Latin customary for English monasteries drawn up at Winchester about 970, set out the daily and yearly rules of the monks.",
        details = "It includes the Easter dialogue Quem quaeritis, a short scripted exchange at the sepulchre that is among the earliest texts of liturgical drama. The year is an approximate date for the customary as a whole.",
    ),

)
