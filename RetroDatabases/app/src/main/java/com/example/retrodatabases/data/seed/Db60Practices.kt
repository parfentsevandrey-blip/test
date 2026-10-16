package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1440 to 1450: a Strasbourg partnership and its secret art, a loan to a printing partnership, the Holy Year of 1450, a Korean poem in print and the Latin letters of Florence's chancery. */
val db60Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.PRACTICE,
        section = "Partnerships",
        title = "The Strasbourg partnership and its secret art",
        originalTitle = "The Strasbourg partnership",
        author = "Gutenberg and his partners",
        year = 1440,
        summary = "The Strasbourg partnership of 1438 made cast-metal mirrors for pilgrims to Aachen in 1440 and worked in a secret art that later became the subject of a lawsuit.",
        details = "A lawsuit of 1439 brought before the Strasbourg Council turned on the partnership's affairs, and its papers name a costly venture called the art. Sources differ on whether the art was a printing press or a mirror business, and on whether the pilgrimage was held in 1439 or 1440.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.PRACTICE,
        section = "Finance",
        title = "Fust's 1450 loan to a printing partnership in Mainz",
        originalTitle = "The Fust loan",
        author = "Johann Fust",
        year = 1450,
        summary = "In 1450 Johann Fust lent money to a printing partnership in Mainz, and the loan and its terms later became the subject of a lawsuit.",
        details = "The agreement is usually dated to November 1450, and a notarial record of 1455 describes the loan and the partnership's obligations. Fust later sued for repayment of the money he had lent.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.PRACTICE,
        section = "Pilgrimage",
        title = "The Holy Year of 1450 and its pilgrims",
        originalTitle = "Anno Santo 1450",
        author = "Pope Nicholas V",
        year = 1450,
        summary = "The Holy Year of 1450 drew pilgrims to the churches of Rome, who gained an indulgence by visiting them.",
        details = "Pope Nicholas V proclaimed the jubilee for 1450, and this record gives no figures for the crowds. A Holy Year with its pilgrimage to the churches of the city was a regular papal custom.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.PRACTICE,
        section = "Printing in Hangul",
        title = "Yongbi eocheonga, the Songs of the Flying Dragons in print",
        originalTitle = "Yongbi eocheonga",
        author = "King Sejong's scholars",
        year = 1447,
        summary = "The Yongbi eocheonga, a long poem in Hangul praising the founders of the Joseon dynasty, was printed in 1447, among the early large books in the new letters.",
        details = "The poem was written in the mid-1440s, and this record dates its printed edition to 1447. Accounts differ on the exact year the poem was completed, and the record names its subject and its use of the Hangul letters.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.PRACTICE,
        section = "Chancery",
        title = "The Latin letters of the Florentine chancery",
        originalTitle = "Cancelleria fiorentina",
        author = "Leonardo Bruni and Carlo Marsuppini",
        year = 1444,
        summary = "The chancery of Florence wrote the Republic's public letters in classical Latin under its chancellor Leonardo Bruni, and Carlo Marsuppini succeeded him in 1444.",
        details = "Leonardo Bruni held the chancellorship from 1427 until his death in 1444, and the Republic's official letters under him were written in the humanist Latin of the day. Carlo Marsuppini took the office in 1444 and held it until his death in 1453, so the record uses 1444 as the year of the change.",
    ),

)
