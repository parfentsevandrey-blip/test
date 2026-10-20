package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 640 to 650: Xuanzang's translation bureau at the Da Ci'en temple in Chang'an, set up in 648 with copyists, proofreaders and scholars. */
val db140Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "Xuanzang's translation bureau at the Da Ci'en temple in Chang'an, set up in 648 with copyists, proofreaders and scholars",
        originalTitle = "Da Ci'en si",
        author = "Xuanzang",
        year = 648,
        summary = "Xuanzang set up a translation bureau at the Da Ci'en temple in Chang'an in 648, where Sanskrit texts were rendered into Chinese by a team.",
        details = "The bureau brought together Xuanzang, foreign monks, Chinese scholars, copyists and proofreaders, and its work moved from the Sanskrit originals through spoken explanation and draft renderings to revision. The temple was enlarged that year by the crown prince Li Zhi, who later reigned as Emperor Gaozong.",
    ),

)
