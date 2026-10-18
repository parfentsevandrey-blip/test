package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1250 to 1260, only two verified: the Tripitaka Koreana woodblocks of 1251 and the silver drinks tree of Karakorum in 1254. */
val db79Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The Tripitaka Koreana woodblocks, carved at Haeinsa in Korea",
        originalTitle = "Tripitaka Koreana",
        author = "The carvers of the Goryeo court",
        year = 1251,
        summary = "The Goryeo court completed carving the Tripitaka Koreana printing blocks in 1251, a set of wooden blocks for printing the Buddhist canon.",
        details = "Carving began about 1236, and the blocks are kept at Haeinsa temple in South Korea. Accounts give 81,258 blocks as the usual total, with 23 lines of 14 characters on each side, though other block counts are also given.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.DEVICE,
        section = "Mongol court",
        title = "The silver tree at Karakorum, a drinks fountain for the Khan",
        originalTitle = "Silver tree",
        author = "Guillaume Boucher, a goldsmith of Paris",
        year = 1254,
        summary = "A silver tree stood at the Khan's court at Karakorum in 1254, and pipes in its base poured drinks for the Khan's feasts.",
        details = "William of Rubruck saw the tree during his visit of 1254, and it was made by Guillaume Boucher, a goldsmith from Paris. The common account says four lions held up the tree and four liquors flowed from it.",
        rare = true,
    ),

)
