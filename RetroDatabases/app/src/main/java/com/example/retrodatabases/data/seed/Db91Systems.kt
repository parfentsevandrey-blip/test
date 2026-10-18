package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1130 to 1140: Roger II's trilingual chancery, the Polish seniorate of 1138, the Second Lateran Council, the Assizes of Ariano and the Shaoxing era. */
val db91Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.SYSTEM,
        section = "Sicily",
        title = "The trilingual chancery of Roger II of Sicily, which wrote in Latin, Greek and Arabic",
        originalTitle = "Diwan of Roger II",
        author = "The royal chancery of Roger II",
        year = 1130,
        summary = "Roger II's royal chancery at Palermo issued documents in Latin, Greek and Arabic, a practice that reflected the three languages of his kingdom from his coronation in 1130.",
        details = "The kingdom's population included Greeks, Arabs and Latins, and the chancery's scribes wrote in the languages of its different communities. Accounts of the office's staff and its arrangements are incomplete, and the year 1130 marks Roger II's coronation rather than a founding date for the office.",
    ),

    entry(
        databaseId = 91,
        kind = Kind.SYSTEM,
        section = "Poland",
        title = "The testament of Boleslaw III, which set up Poland's seniorate in 1138",
        originalTitle = "Testament of Boleslaw III",
        author = "Boleslaw III Wrymouth",
        year = 1138,
        summary = "Boleslaw III Wrymouth's testament of 1138 divided Poland among his sons and gave the senior prince the city of Krakow, a rule known as the seniorate.",
        details = "The seniorate tied the highest rank and the city of Krakow to the eldest living prince, while the younger princes received shares of the land, and the arrangement led to conflict among the brothers for decades. The terms are known from the chronicles, and the exact wording of the testament is debated.",
        rare = true,
    ),

    entry(
        databaseId = 91,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Second Lateran Council of 1139 and its canons on clergy and warfare",
        originalTitle = "Concilium Lateranense II",
        author = "Pope Innocent II",
        year = 1139,
        summary = "The Second Lateran Council met in Rome in April 1139 under Pope Innocent II to end the schism of 1130, and it issued canons on clergy, simony and warfare.",
        details = "The double election of 1130 had split the Church between Innocent II and Anacletus II, and the council met after the death of the rival pope in 1138. One of its canons barred the crossbow against Christians.",
    ),

    entry(
        databaseId = 91,
        kind = Kind.SYSTEM,
        section = "Sicily",
        title = "The Assizes of Ariano, Roger II's law code for the kingdom of Sicily",
        originalTitle = "Assise di Ariano",
        author = "Roger II of Sicily",
        year = 1140,
        summary = "The Assizes of Ariano were a law code that King Roger II of Sicily issued at Ariano in 1140, setting out rules for royal courts, offences and feudal service.",
        details = "The kingdom's population included Greeks, Arabs, Latins and Lombards, and the assizes applied one body of law across Sicily and the mainland. Historians still debate how far the code recorded older custom and how far it made new law.",
        rare = true,
    ),

    entry(
        databaseId = 91,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Shaoxing reign era, from 1131, under which the Southern Song counted its years",
        originalTitle = "Shaoxing",
        author = "Emperor Gaozong of the Song",
        year = 1131,
        summary = "The Shaoxing reign era began in 1131 and gave the Southern Song court its own numbered years, so that documents were dated by era name and year.",
        details = "A reign era was a name that a ruler chose for a span of years, and each year was counted within it from one. Shaoxing ran from 1131 to 1162 and followed the Jianyan era, which ended in 1130, and both eras belonged to the reign of Emperor Gaozong.",
    ),

)
