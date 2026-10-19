package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 740 to 750: the Golden Light and Lotus copies ordered in 741. */
val db130Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.PRACTICE,
        section = "Japanese",
        title = "Copying the Golden Light and Lotus sutras for each provincial temple and nunnery, ordered in 741",
        originalTitle = "Konkomyo-kyo and Hokke-kyo",
        author = "Emperor Shomu",
        year = 741,
        summary = "The edict of 741 that set up provincial temples and nunneries also made the copying and reading of two sutras part of their duty, the Golden Light for the temples and the Lotus for the nunneries.",
        details = "The edict put one provincial temple and one nunnery in each province, and the Golden Light sutra was prized as a protector of the realm. The arrangement is usually read as Buddhism serving the state, and the year used here is the year of the edict.",
        rare = true,
    ),

)
