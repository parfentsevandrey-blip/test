package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 20 to 30: the Roman norma, a square for setting out right angles, placed at 21 by the anchor of the Gaulish revolt. */
val db202Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.TOOL,
        section = "Surveying",
        title = "The norma, a Roman square for setting out right angles",
        originalTitle = "Norma",
        author = "Roman carpenters and surveyors",
        year = 21,
        summary = "The norma was a Roman square for setting out right angles, used by carpenters and surveyors.",
        details = "The sources do not date the norma, so 21 is the year of the anchor, the revolt of Julius Sacrovir in Gaul, and not the year the square was made. Roman surveyors named it among their right-angle instruments, beside the groma, which set out right angles with plumb lines.",
        rare = true,
    ),

)
