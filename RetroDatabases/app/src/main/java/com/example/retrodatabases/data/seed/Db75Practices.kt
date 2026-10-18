package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1291 to 1300: a papal valuation of church incomes, a Florentine ban on Arabic figures in 1299, the first Holy Year of 1300 and a Kamakura debt-relief decree of 1297. */
val db75Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "The Taxatio of Pope Nicholas IV, a valuation of church incomes in England and Wales",
        originalTitle = "Taxatio Nicholai IV",
        author = "The papal collectors of Nicholas IV",
        year = 1291,
        summary = "The Taxatio of Pope Nicholas IV, begun in 1291, valued the incomes of church benefices in England and Wales so that a crusade tenth could be levied on them.",
        details = "The valuation was made for a planned crusade to the Holy Land, after the loss of Acre in 1291. Accounts differ on the exact completion date, and on how closely the assessed values matched the real income of each benefice.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "The Florentine money-changers' 1299 ban on Arabic figures in account books",
        originalTitle = "Statuto dell'Arte del Cambio",
        author = "The Arte del Cambio, the money-changers' guild of Florence",
        year = 1299,
        summary = "In 1299 the Florentine money-changers' guild, the Arte del Cambio, moved to ban Arabic figures from merchants' account books, and accounts differ on whether the replacement was words or Roman numerals.",
        details = "The rule is dated to a statute of the guild from 1299. Historians give different reasons for it, and the exact wording of the statute is not agreed.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.PRACTICE,
        section = "Pilgrimage",
        title = "The first Holy Year, proclaimed by Boniface VIII for 1300",
        originalTitle = "Antiquorum habet",
        author = "Pope Boniface VIII",
        year = 1300,
        summary = "Boniface VIII proclaimed a Holy Year for 1300 in the bull Antiquorum habet, so that pilgrims who visited the great basilicas of Rome would receive a full indulgence.",
        details = "The bull is dated 22 February 1300, and the jubilee was meant to recur at set intervals, a rule later shortened to fifty years and then to twenty-five. Accounts differ on the number of pilgrims who came to Rome that year.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.PRACTICE,
        section = "Debt",
        title = "The Eijin debt-relief decree of the Kamakura shogunate, 1297",
        originalTitle = "Eijin tokusei rei",
        author = "The Kamakura shogunate",
        year = 1297,
        summary = "In 1297 the Kamakura shogunate issued a debt-relief decree that let warrior families recover land they had sold or pledged to merchants and commoners.",
        details = "The decree is called the Eijin tokusei and was one of several debt-relief measures the shogunate issued. Accounts differ on how far it was enforced and on which sales it covered.",
        rare = true,
    ),

)
