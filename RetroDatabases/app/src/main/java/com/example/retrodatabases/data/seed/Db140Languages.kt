package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 640 to 650: Xuanzang's Records of the Western Regions, completed in 646, and the Jin shu, the official history of the Jin dynasty, of 648. */
val db140Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Xuanzang's Records of the Western Regions, an account of the lands he visited, completed in 646",
        originalTitle = "Da Tang Xiyu ji",
        author = "Xuanzang",
        year = 646,
        summary = "Xuanzang's Records of the Western Regions, a Chinese account of the lands he visited in India and Central Asia, was completed in 646.",
        details = "Xuanzang came back to Chang'an in 645, and the record was completed in 646 at the emperor's request in twelve fascicles, with his assistant Bianji writing it down. It is one of the chief sources on the seventh-century lands it describes.",
    ),

    entry(
        databaseId = 140,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Jin shu, the official history of the Jin dynasty, compiled under Fang Xuanling and completed in 648 in 130 juan",
        originalTitle = "Jin shu",
        author = "Fang Xuanling and a team of compilers",
        year = 648,
        summary = "The Jin shu, the official history of the Jin dynasty of 266 to 420, was compiled under Fang Xuanling, with 648 given in some references as its year of completion.",
        details = "It runs to 130 juan in four parts: ten of annals, twenty of treatises, seventy of biographies and thirty of records of the rival regimes of the period. Accounts differ on the dates, since one places the drafting between 646 and 648 and others give 648 as the year of completion.",
        rare = true,
    ),

)
