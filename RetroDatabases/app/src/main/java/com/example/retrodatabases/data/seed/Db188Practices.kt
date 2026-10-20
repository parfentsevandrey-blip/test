package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 160 to 170: the gift tribute of ivory, rhinoceros horn and tortoiseshell that Roman envoys presented at the Han court in 166. */
val db188Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.PRACTICE,
        section = "Diplomacy",
        title = "Gift tribute at the Han court, the ivory and horn that Roman envoys presented in 166",
        originalTitle = "Gift tribute",
        author = "Envoys of the Roman ruler and the Han court",
        year = 166,
        summary = "The Hou Hanshu records that envoys came by sea to Rinan and reached the Han court in 166, presenting ivory, rhinoceros horn and tortoiseshell as gifts.",
        details = "The Hou Hanshu dates the mission to the ninth year of the Yanxi period, which is 166, and says the envoys came from a ruler the Chinese called An-tun, probably Marcus Aurelius. Historians dispute whether they were true envoys or traders in diplomatic dress, and the account was compiled in the fifth century, long after the event.",
        rare = true,
    ),

)
