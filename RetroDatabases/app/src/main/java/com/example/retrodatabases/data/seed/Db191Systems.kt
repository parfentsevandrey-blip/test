package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 130 to 140: the Edictum perpetuum, the praetor's edict fixed in one permanent text under Hadrian. */
val db191Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 191,
        kind = Kind.SYSTEM,
        section = "Roman law",
        title = "The Edictum perpetuum, the praetor's edict fixed in one text under Hadrian",
        originalTitle = "Edictum perpetuum",
        author = "Salvius Julianus",
        year = 130,
        summary = "Hadrian had the rules that the urban praetor announced each year for deciding cases fixed in one permanent text by Salvius Julianus.",
        details = "Accounts date the commission to shortly after 125 and put the finished work between about 125 and 131, so 130 is a round date within that range rather than a fixed year. Once the text was fixed, later changes needed imperial approval, and the surviving text comes from fragments in later legal writings, chiefly the Digest of Justinian.",
        rare = true,
    ),

)
