package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 730 to 740: the Kaiyuan Shijiao Lu, Zhisheng's catalogue of Buddhist scriptures compiled in 730. */
val db131Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.TOOL,
        section = "Buddhist",
        title = "The Kaiyuan Shijiao Lu, Zhisheng's catalogue of Buddhist scriptures compiled in 730",
        originalTitle = "Kaiyuan shijiao lu",
        author = "Zhisheng",
        year = 730,
        summary = "The Kaiyuan Shijiao Lu, a catalogue of Buddhist scriptures in twenty fascicles, was compiled by the monk Zhisheng in 730.",
        details = "The catalogue arranges the translated scriptures by translator and by dynasty, and its canon inventory fixes which texts count for the Chinese canon. Later Chinese canons largely followed its framework, so it is the standard bibliographic guide to the translations made in China.",
        rare = true,
    ),

)
