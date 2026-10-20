package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 200 to 210: the Severan marble plan of Rome, a map of the city cut on marble slabs and dated by inference to 203 to 211. */
val db184Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 184,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Severan marble plan of Rome, a map of the city cut on marble slabs",
        originalTitle = "Forma Urbis Romae",
        author = "Severan craftsmen of Rome",
        year = 203,
        summary = "The Severan marble plan of Rome is a map of the city cut into marble slabs, usually dated to the years 203 to 211.",
        details = "No ancient text mentions the plan, so its date is an inference from its Severan setting and the map's own evidence, and 203 is the first year of the usual span. Accounts give about 150 slabs, with 151 in one count, and the surviving fragments show the streets and buildings of the city at large scale.",
        rare = true,
    ),

)
