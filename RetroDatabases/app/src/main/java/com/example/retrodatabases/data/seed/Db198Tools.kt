package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 60 to 69: Dioscorides's Greek catalogue of drugs, used by physicians for centuries. */
val db198Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 198,
        kind = Kind.TOOL,
        section = "Drugs",
        title = "Dioscorides's De materia medica, a Greek catalogue of drugs in five books",
        originalTitle = "Peri hules iatrikes",
        author = "Pedanius Dioscorides",
        year = 65,
        summary = "Dioscorides's De materia medica is a Greek catalogue of drugs from plants, animals and minerals, arranged in five books and used by physicians for centuries afterwards.",
        details = "Dioscorides was born at Anazarbus in Cilicia and is said to have served as a surgeon in the Roman army, but his service under Nero rests on tradition rather than documents. Dates for the work vary from about 60 to about 77 CE, so 65 is a round date inside that range and not a settled year.",
        rare = true,
    ),

)
