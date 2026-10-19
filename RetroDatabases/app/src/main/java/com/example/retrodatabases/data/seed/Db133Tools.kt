package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 710 to 720: the fudoki that the Japanese court ordered from every province in 713, provincial gazetteers of districts, products and old stories. */
val db133Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.TOOL,
        section = "Japanese",
        title = "The Fudoki, provincial gazetteers that the Japanese court ordered from every province in 713",
        originalTitle = "Fudoki",
        author = "The Japanese court under Empress Genmei",
        year = 713,
        summary = "In 713 the Japanese court ordered every province to compile a fudoki, a gazetteer of its districts, products, soil and old stories.",
        details = "The order asked each province for the names of its districts and villages, the products of its mountains and fields, the quality of its soil, the origins of place names and the old tales told there. Five complete fudoki survive, and the Izumo text is the best known of them.",
        rare = true,
    ),

)
