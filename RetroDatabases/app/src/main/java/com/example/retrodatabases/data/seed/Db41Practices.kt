package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1630 to 1640: a trial over a book, a projected observation, a tulip market, a clockmakers' charter and a network of letters. */
val db41Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.PRACTICE,
        section = "Censorship",
        title = "Galileo's trial in 1633 and the licensing of the Dialogue",
        originalTitle = "Dialogo sopra i due massimi sistemi del mondo",
        author = "Roman Inquisition",
        year = 1633,
        summary = "In 1633 the Roman Inquisition tried Galileo for holding Copernican views and made him abjure them, after the Dialogue had been licensed in Florence and then withdrawn in Rome.",
        details = "The Florentine censors had licensed the Dialogue in 1632, but the Roman censors had it withdrawn that year, and in June 1633 Galileo was made to abjure before the Inquisition. The sentence confined him to house arrest for the rest of his life, and the Dialogue was banned.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.PRACTICE,
        section = "Observation",
        title = "Gassendi's projected sun image and the transit of Mercury",
        originalTitle = "Mercurius in Sole visus et Venus invisa",
        author = "Pierre Gassendi",
        year = 1631,
        summary = "Gassendi watched the transit of Mercury across the sun on 7 November 1631 by projecting the sun's image through a telescope onto paper, which spared the eye.",
        details = "The transit had been predicted from Kepler's tables, and Gassendi dated it in the Gregorian calendar, which France used. His account was printed in 1632, and the observation is usually counted as the first recorded transit of Mercury.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.PRACTICE,
        section = "Markets",
        title = "The Dutch tulip market of 1636 and 1637",
        originalTitle = "Tulip bulb contracts, 1636 to 1637",
        author = "Dutch growers and traders",
        year = 1637,
        summary = "The Dutch market in tulip bulbs of 1636 and 1637 traded contracts for future delivery, and the price collapse of February 1637 ended the boom.",
        details = "Buyers in taverns and inns traded contracts for bulbs that were not yet lifted, and the demand was highest for the rarest varieties. Accounts differ on how far the prices really fell, but the crash of early February 1637 is the usual end point of the story.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.PRACTICE,
        section = "Trades",
        title = "The London clockmakers' charter of 1631 and their apprenticeships",
        originalTitle = "Charter of the Clockmakers' Company, 1631",
        author = "Clockmakers' Company of London",
        year = 1631,
        summary = "The London clockmakers received a royal charter in 1631, and the company regulated the trade through apprenticeship and the freedom of the City.",
        details = "The company set the terms on which a craftsman could take apprentices and set up as a master in London. Its charter is dated 1631, and the company was one of the City's livery companies that grew out of the medieval guilds.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.PRACTICE,
        section = "Correspondence",
        title = "Mersenne's letters, a relay for learned men in Paris",
        originalTitle = "Correspondance du P. Marin Mersenne",
        author = "Marin Mersenne",
        year = 1636,
        summary = "Mersenne kept up a wide letter exchange from his convent in Paris, passing questions, results and manuscripts among mathematicians across Europe.",
        details = "Mersenne wrote to Descartes, Fermat, Pascal, Galileo and many others, and he sent results from one correspondent to another, which made his letters a route for new work. His letters were copied and circulated, and the exchange was one of the main ways that mathematics crossed borders before the learned societies of the 1660s.",
    ),

)
