package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 960 to 970: al-Sufi's revised star catalogue with its pictures of the constellations. */
val db108Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.TOOL,
        section = "Shiraz",
        title = "al-Sufi's Book of the Fixed Stars, a revised star catalogue with pictures of the constellations",
        originalTitle = "Kitab suwar al-kawakib al-thabita",
        author = "Abd al-Rahman al-Sufi",
        year = 964,
        summary = "Abd al-Rahman al-Sufi's Book of the Fixed Stars revised Ptolemy's star catalogue and gave pictures of the 48 constellations in about 964.",
        details = "Most sources give 964 for the book, though some give about 965, and accounts differ on the city of writing, with Shiraz named most often and Isfahan given in other listings. The book honours the Buyid ruler Adud al-Dawla, and the catalogue gives coordinates for 1,017 stars. Each constellation has two pictures, one seen from outside a celestial globe and one of a stargazer looking up, and the author's autograph is lost, while the Doha copy dated 1125 is likely the earliest surviving copy.",
        rare = true,
    ),

)
