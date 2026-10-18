package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1120 to 1130: the Premonstratensian canons, the Concordat of Worms, the First Lateran Council, the Pactum Warmundi and the Templar rule approved at Troyes. */
val db92Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.SYSTEM,
        section = "Religious orders",
        title = "The Premonstratensian canons, founded at Premontre in 1120",
        originalTitle = "Ordo Praemonstratensis",
        author = "Norbert of Xanten",
        year = 1120,
        summary = "Norbert of Xanten founded the Premonstratensian canons at Premontre in 1120, a community of canons who lived by the Rule of St Augustine.",
        details = "Pope Honorius II approved the canons' rule in 1126, and the order spread through northern Europe in the years that followed. The year 1120 is the foundation at Premontre, and the papal approval of 1126 marks the order's recognition by Rome.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.SYSTEM,
        section = "Investiture",
        title = "The Concordat of Worms, the settlement of the investiture dispute in 1122",
        originalTitle = "Pactum Calixtinum",
        author = "Pope Callixtus II and Emperor Henry V",
        year = 1122,
        summary = "The Concordat of Worms settled how bishops were invested, with Pope Callixtus II and Emperor Henry V agreeing the terms in 1122.",
        details = "The agreement was sealed at Worms on 23 September 1122. It split the investiture of a bishop into a spiritual act given by the church and a temporal act given by the ruler, and the First Lateran Council confirmed its terms in 1123.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The First Lateran Council of 1123, which confirmed the Concordat of Worms",
        originalTitle = "Concilium Lateranense I",
        author = "Pope Callixtus II",
        year = 1123,
        summary = "The First Lateran Council, held in Rome in 1123 under Pope Callixtus II, confirmed the Concordat of Worms and issued canons on church discipline.",
        details = "The council is counted as the first general council held at the Lateran in Rome, and it is named after the palace where it met. Its canons dealt with clergy, church property and the conduct of church business.",
    ),

    entry(
        databaseId = 92,
        kind = Kind.SYSTEM,
        section = "Crusades",
        title = "The Pactum Warmundi, Venetian trading privileges in Jerusalem, 1123",
        originalTitle = "Pactum Warmundi",
        author = "Warmund, patriarch of Jerusalem, and the Venetians",
        year = 1123,
        summary = "The Pactum Warmundi of 1123 gave Venetian merchants quarters and tax privileges in the Latin Kingdom of Jerusalem in return for Venetian naval help.",
        details = "The agreement was made between the Venetians and the patriarch Warmund, and it is usually dated to 1123. It is one of the early charters that set out the rights of a Western trading city in the crusader states.",
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.SYSTEM,
        section = "Military orders",
        title = "The Rule of the Knights Templar, approved at the Council of Troyes in 1129",
        originalTitle = "Regula pauperum commilitonum Christi",
        author = "The Council of Troyes, called by Bernard of Clairvaux",
        year = 1129,
        summary = "The Council of Troyes approved the Rule of the Knights Templar in January 1129, giving the military brotherhood a written code of life.",
        details = "The council opened on 13 January 1129, and Hugues de Payens asked for the rule to be approved there. Some accounts give 1128 for the council, and Pope Honorius II did not attend, sending the legate Matthew of Albano instead.",
    ),

)
