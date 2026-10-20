package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 600 to 610: Lu Fayan's Qieyun, the rhyme dictionary of 601 that sorted Chinese characters by sound and tone. */
val db144Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Lu Fayan's Qieyun, a rhyme dictionary of 601 that sorted Chinese characters by sound and tone",
        originalTitle = "Qieyun",
        author = "Lu Fayan and others",
        year = 601,
        summary = "The Qieyun, compiled in 601, set out the sound classes of Chinese characters in rhyme groups and tones for writers of rhymed verse.",
        details = "Lu Fayan and several scholars compiled it under the Sui, drawing on five earlier dictionaries and reconciling their sound distinctions. The complete text is lost, but fragments and later rhyme books that revised it survive, and it is the starting point for reconstructions of older Chinese pronunciation.",
        rare = true,
    ),

)
