package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 70 to 80: the stone-cutter's chisel that cut Roman public inscriptions, placed at 79 because the sources do not date it. */
val db197Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.TOOL,
        section = "Stone",
        title = "The stone-cutter's chisel that cut Roman public inscriptions, undated in the sources and placed at 79",
        originalTitle = "Scalprum",
        author = "Roman stone-cutters",
        year = 79,
        summary = "The chisel of the Roman stone-cutter cut the letters of public inscriptions, a record kept in stone, and this tool is placed at 79 because the sources do not date it.",
        details = "The sources do not date this tool; 79 is the year of the anchor, the eruption of Vesuvius, not of the making. Roman letters on stone were laid out with ruled guide lines and then cut with chisels, so each public inscription was made by hand, one letter at a time.",
        rare = true,
    ),

)
