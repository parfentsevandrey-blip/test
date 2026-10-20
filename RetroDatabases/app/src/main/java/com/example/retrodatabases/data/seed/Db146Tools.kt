package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 580 to 590: wax-covered writing tablets, undated in the sources and placed at 582, the year of Maurice's accession. */
val db146Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.TOOL,
        section = "Late Antique",
        title = "Wax-covered writing tablets, the everyday notebooks of Late Antique schools and offices, undated in the sources and placed at 582",
        originalTitle = "tabulae ceratae",
        author = "Unnamed Late Antique scribes",
        year = 582,
        summary = "Wooden leaves coated with wax and scratched with a stylus served as the everyday notebooks of Late Antique schools and offices.",
        details = "The sources do not date this tool; 582 is the year of the anchor, the accession of the Byzantine emperor Maurice, not the date of any tablet. Tablets were hinged in pairs or sets of three, and their wax could be smoothed and written again, which made them the cheap draft paper of the period.",
        rare = true,
    ),

)
