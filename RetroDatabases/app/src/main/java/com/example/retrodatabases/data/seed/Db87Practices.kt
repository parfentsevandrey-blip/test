package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1175 to 1180: the Grand Assize of 1179, the short-cross penny of 1180 and the hot-iron ordeal of the 1170s. */
val db87Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.PRACTICE,
        section = "Land and courts",
        title = "The Grand Assize of 1179, a royal option that moved land cases out of feudal courts",
        originalTitle = "Magna assisa",
        author = "Henry II of England",
        year = 1179,
        summary = "The Grand Assize of 1179 let a tenant take a freehold land dispute from a feudal court to the king's court, where a jury of knights could replace the duel.",
        details = "The procedure is also called the Assize of Windsor, and Henry II hoped that it would curb private violence and bring the crown more income. The sources describe the aim more clearly than the practice, so the year 1179 marks the law and not the cases heard under it.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.PRACTICE,
        section = "Money",
        title = "The short-cross penny of 1180, the English silver coin Henry II reissued",
        originalTitle = "Short-cross penny",
        author = "Henry II's moneyers",
        year = 1180,
        summary = "The short-cross penny was an English silver coin with a cross on the reverse that stopped short of the rim, struck from about 1180.",
        details = "Henry II had the silver coinage reissued in this form in 1180, and the coin stayed in use until the long-cross penny replaced it in 1247. The name comes from the cross, which does not reach the edge.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.PRACTICE,
        section = "Courts",
        title = "The hot-iron ordeal, a judicial test still used in English courts in the 1170s",
        originalTitle = "Ordeal by hot iron",
        author = "Priests and judges of the English courts",
        year = 1175,
        summary = "In the hot-iron ordeal an accused person carried a red-hot iron, and the state of the wound after three days decided the case.",
        details = "The Fourth Lateran Council of 1215 forbade priests to take part in ordeals, and the practice faded in England afterwards. Accounts describe the three-day wait and the inspection of the wound, but how often the ordeal was used in the 1170s is hard to judge from the surviving records.",
        rare = true,
    ),

)
