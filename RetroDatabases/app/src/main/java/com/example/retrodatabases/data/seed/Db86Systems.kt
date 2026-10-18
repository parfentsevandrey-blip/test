package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1181 to 1188: Henry II's Assizes of Arms and Woodstock, the Peace of Constance and the Saladin tithe. */
val db86Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.SYSTEM,
        section = "Military",
        title = "The Assize of Arms of 1181, which set the weapons each free man kept",
        originalTitle = "Assisa de armis",
        author = "Henry II of England",
        year = 1181,
        summary = "The Assize of Arms of 1181 set out the arms and armour that each man had to keep, according to the value of his goods.",
        details = "Richer men were required to keep more armour, and the poorest only a few weapons, so the assize tied military duty to wealth. The date 1181 is the year usually given for the assize, which was issued in the reign of Henry II.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.SYSTEM,
        section = "Peace",
        title = "The Peace of Constance of 1183 between the emperor and the Lombard cities",
        originalTitle = "Pax Constantiensis",
        author = "Frederick I Barbarossa and the Lombard League",
        year = 1183,
        summary = "The Peace of Constance, agreed in 1183, gave the cities of the Lombard League self-government under the emperor's overlordship.",
        details = "The cities kept the right to choose their own consuls, and in return they acknowledged the emperor as their lord. The treaty is usually dated to 25 June 1183, and historians still debate how much real power it left to the emperor.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.SYSTEM,
        section = "Forests",
        title = "The Assize of Woodstock of 1184, Henry II's law of the royal forests",
        originalTitle = "Assisa de Wudestoc",
        author = "Henry II of England",
        year = 1184,
        summary = "The Assize of Woodstock of 1184 set out Henry II's rules for the royal forests, including the hunting of deer and the clearing of land.",
        details = "The assize was issued at Woodstock, near Oxford, and its clauses are known from later copies of the text. The date 1184 is the one usually given for it.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.SYSTEM,
        section = "Crusade",
        title = "The Saladin tithe of 1188, a tenth of movable goods for the Third Crusade",
        originalTitle = "Decima Saladini",
        author = "Henry II of England and Philip II of France",
        year = 1188,
        summary = "The Saladin tithe of 1188 was a tenth of movable goods and income, raised in England and France to pay for the Third Crusade.",
        details = "The two kings agreed the tax at Gisors in January 1188, and it was assessed locally in each parish. Accounts differ on which goods and incomes it covered, so the yield and the exemptions are not settled.",
    ),

)
