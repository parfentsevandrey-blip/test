package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 300 to 310: the handing over of sacred books to the authorities in the Great Persecution, called traditio. */
val db174Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.PRACTICE,
        section = "Persecution",
        title = "Handing the sacred books over to the authorities, the practice called traditio",
        originalTitle = "Traditio",
        author = "Christians of the Great Persecution",
        year = 303,
        summary = "Under the first edict of 303, some Christians handed their sacred books over to the authorities, an act called traditio.",
        details = "Those who gave up books were called traditores, and the question of whether they could keep their office later split the churches of Africa. Eusebius records that some surrendered the scriptures under the edict.",
        rare = true,
    ),

)
