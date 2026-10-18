package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1221 and 1228: a quick papal canonization and Kamakura's jito stewards after the Jokyu War. */
val db82Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 82,
        kind = Kind.PRACTICE,
        section = "Saints",
        title = "The canonization of Francis of Assisi in 1228, two years after his death",
        originalTitle = "Mira circa nos hodie",
        author = "Pope Gregory IX",
        year = 1228,
        summary = "Pope Gregory IX canonized Francis of Assisi on 16 July 1228, only two years after his death, and the bull of canonization opens with the words Mira circa nos.",
        details = "A canonization that came so soon after a death was unusually quick, and accounts link the speed to the friars and to the papacy's wish to honour the founder. The bull records the miracles accepted in the process, and the saint's cult spread quickly after it.",
    ),

    entry(
        databaseId = 82,
        kind = Kind.PRACTICE,
        section = "Estates",
        title = "Kamakura's jito stewards, placed over estates taken from the court's side in 1221",
        originalTitle = "Jito",
        author = "The Kamakura shogunate",
        year = 1221,
        summary = "After the Jokyu War of 1221, the Kamakura shogunate appointed jito stewards over estates taken from the court's supporters, a step that widened its control of the western provinces.",
        details = "The war ended with the court's forces defeated, and the shogunate set up the Rokuhara deputies in Kyoto to watch the court. Accounts differ on how many estates were taken and on the exact terms the stewards were given.",
    ),

)
