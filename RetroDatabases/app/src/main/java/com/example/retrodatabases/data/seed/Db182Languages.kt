package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 220 to 230: Julius Africanus's world chronology and the Bakhshali arithmetic on birch bark. */
val db182Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.LANGUAGE,
        section = "Chronology",
        title = "Julius Africanus's Chronographiai, a world chronology from creation to 221",
        originalTitle = "Chronographiai",
        author = "Julius Africanus",
        year = 221,
        summary = "Julius Africanus's Chronographiai, a five-book chronology of world history from creation to the reign of Elagabalus, was completed about 221.",
        details = "Accounts differ on which regnal year of Elagabalus closes the work, the third or the fourth, and both fall in or near 221. One scholar's estimate spreads the writing over about 212 to 221, so the year marks the close of a long process rather than a single day. Only fragments survive, quoted by later chronographers, so the five books are known mainly through those excerpts.",
        rare = true,
    ),

    entry(
        databaseId = 182,
        kind = Kind.LANGUAGE,
        section = "Arithmetic",
        title = "The Bakhshali manuscript, a Sanskrit arithmetic on birch bark with a dot for zero",
        originalTitle = "Bakhshali manuscript",
        author = "Anonymous Sanskrit writers",
        year = 224,
        summary = "The Bakhshali manuscript is a Sanskrit arithmetic on birch-bark leaves, with a dot that the Bodleian team reads as a zero.",
        details = "The leaves were found in 1881 in a village northeast of Peshawar and reached the Bodleian Library in 1902. Radiocarbon tests in 2017 gave one leaf the range 224 to 383, while two other tested leaves gave dates near 700 and 900, so 224 is the lower end of one range and not the date of the whole text. Critics dispute how far the oldest leaf dates the text and how the dot should be read.",
        rare = true,
    ),

)
