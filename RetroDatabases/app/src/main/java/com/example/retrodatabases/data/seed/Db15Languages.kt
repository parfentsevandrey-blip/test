package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1890 to 1900: a shorthand, a vector notation and four little-known constructed languages. */
val db15Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Gregg Shorthand, light-line phonography",
        originalTitle = "Gregg Shorthand: A Light-Line Phonography for the Million",
        author = "John Robert Gregg",
        year = 1893,
        summary = "A shorthand system of light curved strokes with no shading, which John Robert Gregg first printed as a pamphlet in 1888 and later issued in America as Gregg Shorthand.",
        details = "Gregg's first pamphlet, Light-Line Phonography, appeared in Liverpool in 1888. The American edition under the title Gregg Shorthand is dated 1893 here, and the bound volume is dated 1897 in some accounts. Accounts differ on the first public form, so the date is disputed.",
    ),

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Veltparl, an artificial language of 1896",
        originalTitle = "Veltparl (1896)",
        author = "No author credited",
        year = 1896,
        summary = "An artificial language of the 1890s, one of the schemes made in the years after Volapuk and dated 1896.",
        details = "Accounts of Veltparl agree on the date 1896. No author is credited in them, so the record gives only the name and the date.",
        rare = true,
    ),

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Dil, a constructed language of 1893",
        originalTitle = "Dil (1893)",
        author = "J. Fieweger",
        year = 1893,
        summary = "A constructed auxiliary language of 1893 credited to J. Fieweger, a little-known scheme recorded only briefly.",
        details = "Little is recorded about Dil, and accounts of its author are thin, so the attribution is uncertain. The record gives only the name, the author and the date.",
        rare = true,
    ),

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Orba, a constructed language of 1893",
        originalTitle = "Orba (1893)",
        author = "Jose Guardiola",
        year = 1893,
        summary = "A constructed auxiliary language of 1893 credited to Jose Guardiola, a little-known scheme recorded only briefly.",
        details = "Little is recorded about Orba, and accounts of its author are thin, so the attribution is uncertain. The record gives only the name, the author and the date.",
        rare = true,
    ),

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Bolak, a constructed language of 1899",
        originalTitle = "Bolak (1899)",
        author = "Bollack",
        year = 1899,
        summary = "A constructed auxiliary language dated 1899 and credited to Bollack, a little-known scheme recorded only briefly.",
        details = "Little is recorded about Bolak, and the spelling of the author's name is uncertain. The scheme is kept here as a little-known constructed language of the decade.",
        rare = true,
    ),

    entry(
        databaseId = 15,
        kind = Kind.LANGUAGE,
        section = "Vector notation",
        title = "Heaviside's vector notation for electromagnetism",
        originalTitle = "Electromagnetic Theory, volume 1",
        author = "Oliver Heaviside",
        year = 1893,
        summary = "A notation with vector symbols, in which Heaviside wrote the field laws of electromagnetism in one compact form.",
        details = "Heaviside's 1893 Electromagnetic Theory set out the vector form he had used in papers since the 1880s, and accounts differ on when the notation was first used. This record uses 1893, the date of the book.",
    ),
)
