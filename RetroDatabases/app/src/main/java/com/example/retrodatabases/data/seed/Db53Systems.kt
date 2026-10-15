package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1514 to 1517: the Lateran rule for printed books, the Taxis post, the Trilingual College, a Kongo schooling letter and the Aldine Academy. */
val db53Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.SYSTEM,
        section = "Medicine",
        title = "The Royal College of Physicians, chartered in 1518",
        originalTitle = "The Royal College of Physicians, chartered in 1518",
        author = "Thomas Linacre, with the charter of Henry VIII",
        year = 1518,
        summary = "The Royal College of Physicians of London was set up by royal charter in 1518 to examine and license the physicians who practised in and around the city.",
        details = "Thomas Linacre, the king's physician, was its first president, and the charter gave the College power to license physicians within seven miles of London. Accounts differ on how strictly the College enforced its licences in its first years.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Tripartitum, Werboczy's book of customary law, 1517",
        originalTitle = "Tripartitum opus iuris consuetudinarii inclyti regni Hungariae",
        author = "Istvan Werboczy",
        year = 1517,
        summary = "Werboczy's Tripartitum of 1517 set out the customary law of the Hungarian kingdom in three parts, and it was long consulted as a guide to that law.",
        details = "The book was written in Latin, the language of the Hungarian courts of the time, and it was printed in 1517. Accounts differ on when it gained formal authority in the kingdom.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.SYSTEM,
        section = "Languages",
        title = "The Trilingual College at Louvain, Latin, Greek and Hebrew together",
        originalTitle = "Collegium Trilingue",
        author = "Jerome de Busleyden, with Erasmus's support",
        year = 1517,
        summary = "The Trilingual College at Louvain, founded under a bequest of 1517, taught Latin, Greek and Hebrew side by side as one course of study.",
        details = "Busleyden left money in his will for the college, which opened at Louvain in 1518 with teachers in all three languages. Erasmus gave the plan his support.",
    ),

    entry(
        databaseId = 53,
        kind = Kind.SYSTEM,
        section = "Schooling",
        title = "King Afonso's request from Kongo for teachers, a letter of 1514",
        originalTitle = "Carta",
        author = "King Afonso I of Kongo, writing to King Manuel I of Portugal",
        year = 1514,
        summary = "A letter of 1514 from King Afonso I of Kongo to King Manuel I of Portugal asked for teachers and priests, and it is a source for the early schooling of Kongo.",
        details = "The request came from a Christian king who wanted Portuguese teachers and clergy for his realm. Accounts differ on how many schools followed from the request, and on how long the help from Portugal lasted.",
        rare = true,
    ),

    entry(
        databaseId = 53,
        kind = Kind.SYSTEM,
        section = "Greek",
        title = "The Aldine Academy, a Venetian circle that spoke Greek",
        originalTitle = "Neacademia",
        author = "Aldus Manutius and his circle",
        year = 1515,
        summary = "The Aldine Academy was a circle of scholars in Venice who agreed to speak Greek together while Aldus Manutius's press printed Greek books.",
        details = "Its members met around Aldus's press, and Aldus died in 1515. Accounts differ on how long the circle kept meeting after his death.",
        rare = true,
    ),

)
