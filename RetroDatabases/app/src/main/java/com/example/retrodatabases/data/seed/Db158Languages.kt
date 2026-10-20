package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 460 to 470: the Kahaum pillar's Sanskrit verse inscription of Gupta year 141, dated 460 or 461. */
val db158Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.LANGUAGE,
        section = "Inscriptions",
        title = "The Kahaum pillar, a Sanskrit verse inscription of Gupta year 141",
        originalTitle = "Kahaum stone pillar inscription",
        author = "The Jain ascetic Madra, or Skandagupta by another account",
        year = 460,
        summary = "The Kahaum pillar in Uttar Pradesh carries a Sanskrit verse inscription of Skandagupta's reign, dated to Gupta year 141 and so to about 460 CE.",
        details = "The pillar is a sandstone column about eight metres tall at Khukhundoo, carved with images of Parshvanatha and other tirthankaras. The inscription, largely in Sanskrit verse and opening with the word siddham, is dated in the month of Jyeshtha of Gupta year 141, which falls in 460 or 461 CE depending on whether the Gupta era began in 319 or 320, and this record uses 460. Accounts differ on who raised it: one names the Jain ascetic Madra, and another credits Skandagupta, acting on Madra's advice.",
        rare = true,
    ),

)
