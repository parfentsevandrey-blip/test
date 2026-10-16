package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1410 to 1420: Qalqashandi's Arabic encyclopedia of chancery practice, with its section on cryptology. */
val db63Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.TOOL,
        section = "Chancery",
        title = "Qalqashandi's Subh al-a'sha, an encyclopedia of chancery practice",
        originalTitle = "Subh al-a'sha fi sina'at al-insha'",
        author = "Ahmad al-Qalqashandi",
        year = 1412,
        summary = "Qalqashandi's Subh al-a'sha is a large Arabic encyclopedia of chancery practice that includes a section on cryptology.",
        details = "Several entries give 1412 for the completion of the work. Its cryptology section draws on Ibn al-Durayhim, and the survival of Ibn al-Durayhim's own treatise is disputed.",
        rare = true,
    ),

)
