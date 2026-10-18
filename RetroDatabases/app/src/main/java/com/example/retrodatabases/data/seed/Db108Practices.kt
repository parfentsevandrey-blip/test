package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 960 to 970: the Confucian classics carved on stone at Chengdu, a placed record. */
val db108Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.PRACTICE,
        section = "Sichuan",
        title = "The Guangzheng stone classics, Confucian texts carved on stone at Chengdu for the Later Shu court",
        originalTitle = "Guangzheng shijing",
        author = "Wu Zhaoyi and Zhang Dezhao",
        year = 965,
        summary = "A Later Shu official commissioned the Confucian classics to be carved on stone at Chengdu on a Tang model, and the year given here is a placed year.",
        details = "The sources do not date the completion of the stones: the carving is tied to the Guangzheng era that Meng Chang adopted in 938, but accounts differ on the year the work began. The year 965, when the Northern Song took Later Shu, is the year of the anchor and not the year of the carving. Wu Zhaoyi is credited with the commission and Zhang Dezhao with transcribing the texts onto stone for display at Chengdu, after the stone classics of Tang Chang'an.",
        rare = true,
    ),

)
