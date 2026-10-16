package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1490 to 1500: a Florentine arithmetic, a world chronicle, a garden of health, an almanac of tables, a medical handbook and a book of music. */
val db55Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Calandri's Arithmetica of 1491, the illustrated Italian arithmetic",
        originalTitle = "Arithmetica",
        author = "Filippo Calandri",
        year = 1491,
        summary = "Calandri's Arithmetica, printed at Florence in 1491, is usually called the first illustrated Italian arithmetic, and it taught commercial sums to merchants and builders.",
        details = "Its method of division, known as a danda, is still common in schools today. Accounts differ on the first edition, since copies are dated 1491 and 1491/92 and name different printers.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Chronicles",
        title = "Schedel's Nuremberg Chronicle with its city views",
        originalTitle = "Liber chronicarum",
        author = "Hartmann Schedel",
        year = 1493,
        summary = "Schedel's world chronicle of 1493 told the history of the world from creation, with woodcut views of cities and portraits, and it is one of the most lavishly illustrated books of its century.",
        details = "It was printed at Nuremberg in July 1493 in Latin and German editions. Accounts differ on the exact number of woodcuts, which is usually given as well over a thousand, and many of the city views were made from blocks used again for other towns.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Herbals",
        title = "The Ortus sanitatis, the garden of health from Mainz",
        originalTitle = "Ortus sanitatis",
        author = "Anonymous compiler, printed by Jacob Meydenbach",
        year = 1491,
        summary = "The Ortus sanitatis, printed at Mainz in 1491, is a Latin garden of health that describes plants, animals and stones with woodcut pictures of their forms.",
        details = "Its plant woodcuts were printed with the Latin text for physicians and apothecaries who needed the names and uses of common remedies. Accounts differ on how far the plant pictures were drawn from life.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Astronomical tables",
        title = "Zacuto's Almanach perpetuum of 1496",
        originalTitle = "Almanach perpetuum",
        author = "Abraham Zacuto",
        year = 1496,
        summary = "Zacuto's Almanach perpetuum, printed at Leiria in 1496, gave astronomical tables of the sun and planets for the use of astronomers and seamen, with a table of the sun's declination among them.",
        details = "The work was written in Hebrew, and Jose Vizinho translated it into Latin and then into Castilian. Copies differ on whether the canons are in Latin or Castilian, and one account dates the Leiria printing to 25 February 1496.",
        rare = true,
    ),

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Ketham's Fasciculus medicinae with its woodcut figures",
        originalTitle = "Fasciculus medicinae",
        author = "Johannes de Ketham",
        year = 1491,
        summary = "Ketham's Fasciculus medicinae, printed at Venice in 1491, is a medical handbook with woodcut figures, among them anatomical pictures and a zodiac man that links the signs to parts of the body.",
        details = "The handbook was printed at Venice in 1491 and was reprinted in several editions in the years that followed. Accounts differ on how far its anatomical pictures followed the dissections of its time.",
    ),

    entry(
        databaseId = 55,
        kind = Kind.TOOL,
        section = "Music theory",
        title = "Gaffurio's Practica musicae of 1496",
        originalTitle = "Practica musicae",
        author = "Franchinus Gaffurius",
        year = 1496,
        summary = "Gaffurio's Practica musicae, printed at Milan in 1496, taught singers the notes, intervals and ratios of music in a book with diagrams for practice.",
        details = "Gaffurio was the choirmaster of Milan Cathedral, and the book set out the theory and the practice of music for the singers who sang there. Accounts differ on how far the work was meant for practising singers and how far for scholars.",
    ),

)
