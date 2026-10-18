package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1140 to 1150: a papal crusade bull, the glass and choir of Saint-Denis, and the glossing of law books at Bologna. */
val db90Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.PRACTICE,
        section = "Papacy",
        title = "Quantum praedecessores, the papal bull that called the Second Crusade",
        originalTitle = "Quantum praedecessores",
        author = "Pope Eugenius III",
        year = 1145,
        summary = "Pope Eugenius III issued the bull Quantum praedecessores in December 1145, a papal letter that granted crusading privileges and called the Second Crusade to the Holy Land.",
        details = "The bull was addressed to King Louis VII of France and it set out the indulgence that crusaders received for their service. Its date of December 1145 is the one usually given, and the crusade it launched was preached by Bernard of Clairvaux in 1146.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.PRACTICE,
        section = "Glass",
        title = "The stained glass that Abbot Suger set in the choir of Saint-Denis",
        originalTitle = "Vitrail de Saint-Denis",
        author = "Abbot Suger of Saint-Denis",
        year = 1144,
        summary = "Abbot Suger had new stained-glass windows fitted in the rebuilt choir of Saint-Denis, which was consecrated in 1144, and he described the glass in his own account of the work.",
        details = "Suger's account of the rebuilding names the gold, the gems and the coloured glass that filled the choir, and it describes how the windows were made to let light into the church. The consecration of the choir in 1144 is the date usually given, and the glass itself survives only in part.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.PRACTICE,
        section = "Law",
        title = "The glosses that Bologna's lawyers wrote on Justinian's law books",
        originalTitle = "Glossae",
        author = "Bulgarus, Martinus Gosia and Hugo of Bologna",
        year = 1145,
        summary = "Lawyers at Bologna wrote short notes in the margins of Justinian's law books in the mid-twelfth century, and the practice built the school of law that later taught all of Europe.",
        details = "The early glossators named in later sources include Bulgarus, Martinus Gosia and Hugo, and the group is known as the four doctors. Accounts differ on when the first glosses were written, so 1145 is an approximate year, and the year is given for the mid-century practice rather than for a single gloss.",
        rare = true,
    ),

)
