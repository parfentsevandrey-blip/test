package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 750 to 760: the printed Pure Light Dharani of Silla, dated before 751, and the register of Shomu's treasures given to Todai-ji in 756. */
val db129Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.PRACTICE,
        section = "Silla",
        title = "The Pure Light Dharani, a Silla woodblock charm found in the Seokgatap pagoda, printed before 751",
        originalTitle = "Mugujeonggwang dharani-gyeong",
        author = "Unknown printers of Silla",
        year = 751,
        summary = "The Pure Light Dharani, a woodblock print of a Buddhist charm from Silla, was found in 1966 inside the Seokgatap pagoda at Bulguksa and was printed before 751.",
        details = "The pagoda is thought to have been built about 751, and that year is used here as the limit of the print's date, although the construction date is itself only probable. Estimates for the printing run from the early 700s to 751, and accounts differ on the earliest date, while the find on 13 October 1966 during repairs made it one of the oldest surviving woodblock prints.",
        rare = true,
    ),

    entry(
        databaseId = 129,
        kind = Kind.PRACTICE,
        section = "Nara",
        title = "The Register of Donated Articles, the list of Emperor Shomu's objects given to Todai-ji in 756",
        originalTitle = "Todai-ji Kenmotsu-cho",
        author = "Empress Komyo",
        year = 756,
        summary = "On the forty-ninth day after Emperor Shomu's death in 756, Empress Komyo gave about 600 of his objects to the Great Buddha at Todai-ji, and a register lists them one by one.",
        details = "The register names each object with details of it, and the gift is dated to 756. Accounts differ on whether the objects came in one dedication or in batches over several years, and on whether Empress Komyo or Empress Koken founded the repository that kept them.",
        rare = true,
    ),

)
