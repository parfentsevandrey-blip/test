package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tool of 830: al-Khwarizmi's Zij al-Sindhind, the astronomical tables dedicated to al-Ma'mun, known only in later versions. */
val db121Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.TOOL,
        section = "Arabic",
        title = "Al-Khwarizmi's Zij al-Sindhind, astronomical tables dedicated to al-Ma'mun, known only in later versions",
        originalTitle = "Zij al-Sindhind",
        author = "Muhammad ibn Musa al-Khwarizmi",
        year = 830,
        summary = "The Zij al-Sindhind, astronomical tables by al-Khwarizmi dedicated to al-Ma'mun, is dated by some accounts to about 830, and its Arabic text is lost.",
        details = "No Arabic copy has survived, and the version later readers knew is the Latin rendering made by Adelard of Bath, which seems to come from a later revision. Some accounts date the work to 830, a year inside al-Ma'mun's reign, and the dedication to him is the firm link to his court.",
        rare = true,
    ),

)
