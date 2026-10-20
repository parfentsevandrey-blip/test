package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 374: Dao'an's catalogue of Buddhist scriptures, which is lost but survives in part through Sengyou's catalogue of 515. */
val db167Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "Dao'an's catalogue of Buddhist scriptures, made in his Xiangyang years",
        originalTitle = "Zongli zhongjing mulu",
        author = "Dao'an",
        year = 374,
        summary = "Dao'an made a catalogue of Buddhist scriptures in Chinese in about 374, listing the translations with their translators where they were known.",
        details = "The catalogue is counted among Dao'an's major works, and it was made during the years he spent at Xiangyang, which ended in 379 when Former Qin forces took the city. The catalogue itself is lost, and much of its content survives in Sengyou's Chu sanzang jiji of 515.",
        rare = true,
    ),

)
