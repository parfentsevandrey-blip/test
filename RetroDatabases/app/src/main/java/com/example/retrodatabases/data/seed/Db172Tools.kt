package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tool of 330: Eusebius's ten canon tables, which link parallel passages in the four gospels. */
val db172Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.TOOL,
        section = "Gospels",
        title = "Eusebius's ten canon tables, a grid that links parallel passages in the four gospels",
        originalTitle = "Canones Eusebiani",
        author = "Eusebius of Caesarea",
        year = 330,
        summary = "Eusebius's canon tables are ten lists that link passages shared by the four gospels, so a reader can jump between parallel passages while each gospel keeps its own order.",
        details = "The first table lists passages that all four gospels share, and the next three list passages shared by three of them, with the rest for smaller groups. The explanatory letter is usually placed in the 320s or 330s, so the year 330 is an estimate.",
        rare = true,
    ),

)
