package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 100 to 110: Cai Lun's paper, made from tree bark, hemp, rags and fishing nets and reported to the Han court in 105. */
val db194Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "Cai Lun's paper, made from bark, hemp, rags and fishing nets for the Han court",
        originalTitle = "Cai Lun's paper",
        author = "Cai Lun",
        year = 105,
        summary = "In 105 the palace eunuch Cai Lun reported to the Han court a writing material made from tree bark, hemp, rags and fishing nets.",
        details = "The report is dated to 105 under Emperor He, and the materials were tree bark, hemp, rags and fishing nets. Older paper fragments from the Western Han show that paper existed before Cai Lun, so the dispute is over what he reformed and reported rather than who first made paper.",
    ),

)
