package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1150 to 1160: a charter for Austria, the cloistered rule of Japan, the imperial regalia of Roncaglia, a papal letter on Ireland and the scutage of 1159. */
val db89Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.SYSTEM,
        section = "Imperial law",
        title = "The Privilegium Minus, the charter that made Austria a duchy",
        originalTitle = "Privilegium minus",
        author = "Frederick Barbarossa",
        year = 1156,
        summary = "Frederick Barbarossa issued the Privilegium Minus at Regensburg on 17 September 1156, a charter that made Austria a separate duchy.",
        details = "The charter split Austria off from Bavaria and gave its dukes wide rights of succession, including inheritance through the female line. The date of 17 September 1156 is the usual one given for the charter at Regensburg.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "Go-Shirakawa's insei, the rule of a retired emperor from a monastery",
        originalTitle = "Insei",
        author = "Emperor Go-Shirakawa",
        year = 1158,
        summary = "Emperor Go-Shirakawa stepped down in 1158 and began the cloistered rule called insei, in which a retired emperor kept power from a monastery.",
        details = "The insei system had begun with Emperor Shirakawa's retirement in 1086, and Go-Shirakawa's cloistered rule ran from 1158 until his death in 1192. His son Nijo took the throne in the same year, so 1158 marks the start of this later phase of the system.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.SYSTEM,
        section = "Imperial law",
        title = "The Roncaglia decrees of 1158, which listed the imperial regalia",
        originalTitle = "Constitutio de regalibus",
        author = "Frederick Barbarossa and four jurists of Bologna",
        year = 1158,
        summary = "At the diet of Roncaglia in 1158, Frederick Barbarossa had a list of imperial rights, the regalia, defined with the help of jurists from Bologna.",
        details = "The decrees named rights such as tolls, mints and roads, and they stated those rights in the terms of Roman law. The diet met in November 1158 on the plain of Roncaglia near Piacenza, and the jurists were teachers of Roman law at Bologna whose advice shaped the list.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.SYSTEM,
        section = "Ireland",
        title = "Laudabiliter, the papal letter said to grant Ireland to Henry II",
        originalTitle = "Laudabiliter",
        author = "Pope Adrian IV",
        year = 1155,
        summary = "The papal letter Laudabiliter, usually dated 1155 or 1156, is said to have given Ireland to Henry II of England, and its authenticity is disputed.",
        details = "Accounts differ on the year, and the surviving text comes down through later copies, so historians still argue over whether Adrian IV issued the letter in the form that survives. The year 1155 is used here as the earlier of the two dates given for it.",
        rare = true,
    ),

    entry(
        databaseId = 89,
        kind = Kind.SYSTEM,
        section = "Finance",
        title = "The scutage of 1159, shield money paid in place of knight service",
        originalTitle = "Scutagium",
        author = "Henry II of England",
        year = 1159,
        summary = "Henry II raised a scutage, a payment of shield money in place of military service, for his Toulouse campaign in 1159.",
        details = "Tenants who owed knight service could pay a sum for each knight's fee instead of serving, and the king used the money to hire soldiers. The scutage of 1159 is among the earliest recorded in England, though accounts differ on whether earlier payments of this kind were made.",
    ),

)
