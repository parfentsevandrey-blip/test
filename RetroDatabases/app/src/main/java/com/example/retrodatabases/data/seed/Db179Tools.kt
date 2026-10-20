package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 250 to 260, placed at 260: the hipposandal, an iron protector fitted over a horse's hoof. */
val db179Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.TOOL,
        section = "Horses",
        title = "The hipposandal, an iron horseshoe-like protector for a horse's hoof",
        originalTitle = "Hipposandal",
        author = "Roman horse keepers and their smiths",
        year = 260,
        summary = "The hipposandal was an iron protector fitted over a horse's hoof, a Roman tool that the sources date only in broad periods, and it is placed here at 260.",
        details = "The sources date the type only in broad Roman periods, and one group found at Vindolanda near Hadrian's Wall is bracketed to about 140 to 180. Accounts differ on whether Rome or the Gauls first made nailed iron shoes, so 260 is the placed year, the anchor of the cavalry reform, and not the date of any protector.",
        rare = true,
    ),

)
