package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 120 to 130: Hadrian's Wall, the stone and turf frontier across northern Britain, begun in 122. */
val db192Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.SYSTEM,
        section = "Frontiers",
        title = "Hadrian's Wall, the stone and turf frontier across northern Britain, begun in 122",
        originalTitle = "Hadrian's Wall",
        author = "Emperor Hadrian",
        year = 122,
        summary = "Hadrian's Wall was begun in 122 across northern Britain as a frontier of stone and turf, with forts and milecastles set along its line.",
        details = "Hadrian visited Britain in 122, and the line ran about 73 Roman miles from the Tyne to the Solway. Part of the first build was turf, and stone replaced it in places, so the work went through more than one plan.",
    ),

)
