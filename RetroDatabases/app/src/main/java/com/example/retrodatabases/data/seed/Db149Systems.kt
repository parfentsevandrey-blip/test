package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 550 to 560: Narses's victory at Taginae in 552 and Justinian's Pragmatic Sanction for Italy of 554. */
val db149Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 149,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Narses defeats the Ostrogothic king Totila at Taginae in 552, the battle that broke the Gothic army in Italy",
        originalTitle = "Taginae",
        author = "Narses",
        year = 552,
        summary = "At Taginae in the summer of 552 the Byzantine general Narses defeated the Ostrogothic army, and its king Totila died of his wounds soon after.",
        details = "The battle was fought in the hill country of central Italy, and it broke the Gothic field army, although the war went on into the autumn, when the last Gothic king, Teia, fell at Mons Lactarius. Procopius tells the battle in his Gothic War, and the numbers given for the two armies differ between accounts, so the record does not use them.",
        rare = true,
    ),

    entry(
        databaseId = 149,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Justinian's Pragmatic Sanction of August 554, which set out the government and law of Italy after the Gothic War",
        originalTitle = "Sanctio pragmatica pro petitione Vigilii",
        author = "Justinian",
        year = 554,
        summary = "Justinian issued the Pragmatic Sanction in August 554 to set out the government and law of Italy once the Gothic War was over.",
        details = "It answered a petition from Pope Vigilius, and its text is kept among the imperial laws that Justinian's compilers gathered. It is a decree for one region rather than a full code, and it covers the offices and courts of the restored government in Italy.",
    ),

)
