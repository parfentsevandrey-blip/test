package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1070: an Arabic treatise on algebra that solves cubic equations with conic sections. */
val db97Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Omar Khayyam's Treatise on Demonstration of Problems of Algebra",
        originalTitle = "Risala fi'l-barahin 'ala masa'il al-jabr wa'l-muqabala",
        author = "Omar Khayyam",
        year = 1070,
        summary = "Omar Khayyam wrote an Arabic treatise on algebra about 1070 that solves cubic equations by intersecting conic sections.",
        details = "Khayyam (about 1048 to 1131) classified equations of the third degree and found their roots as points where two conic sections cross. The treatise is one of the main Arabic works on cubic equations, and most accounts date it to 1070.",
    ),

)
