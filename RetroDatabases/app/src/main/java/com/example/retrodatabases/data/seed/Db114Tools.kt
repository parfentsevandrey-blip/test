package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 900 to 910: Regino of Prum's handbook of synods and visitations, dated about 906 and dedicated to Hatto of Mainz. */
val db114Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.TOOL,
        section = "Germany",
        title = "Regino of Prum's handbook for synods and visitations, dedicated to Hatto of Mainz",
        originalTitle = "De synodalibus causis et disciplinis ecclesiasticis",
        author = "Regino of Prum",
        year = 906,
        summary = "Regino of Prum compiled a handbook of church discipline and canon law about 906, and he dedicated it to Hatto, archbishop of Mainz.",
        details = "Each of its two books is prefaced by long lists of questions for bishops or their deputies to put at diocesan synods, covering villages, estates and parishes, and the handbook collects canon-law excerpts. The date of about 906 is the one usually given, and the work may have been finished a little later, so the year is approximate.",
        rare = true,
    ),

)
