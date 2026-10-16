package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1380 to 1390: a Ming household register, an English head tax, a cathedral building office, a crusade indulgence and a music notation. */
val db66Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 66,
        kind = Kind.PRACTICE,
        section = "Records",
        title = "The Ming yellow registers of households, begun in 1381",
        originalTitle = "Huangce",
        author = "The Hongwu court",
        year = 1381,
        summary = "The Ming court began the yellow registers of households in 1381, a register of families used to assess taxes and labour service.",
        details = "The registers listed each household with its members and property, and they were the basis for the groups of households called li and jia. The 1381 start is the date usually given for the first registers.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.PRACTICE,
        section = "Taxes",
        title = "The English poll tax of 1380, a flat tax counted by head",
        originalTitle = "Poll tax of 1380",
        author = "The English parliament",
        year = 1380,
        summary = "The English poll tax of 1380 charged a flat sum on each adult, and its collection by head count helped set off the rising of 1381.",
        details = "Parliament granted the tax in 1380 at a rate of one groat, four pence, for each adult, and collectors counted the people in each town. The tax and the anger over its collection led to the revolt of June 1381.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.PRACTICE,
        section = "Building",
        title = "The fabbrica of Milan Cathedral, begun in 1386",
        originalTitle = "Fabbrica del Duomo",
        author = "The Visconti dukes of Milan",
        year = 1386,
        summary = "Milan Cathedral's building works began in 1386, run by a standing office called the fabbrica, which paid the masons and bought the marble.",
        details = "The fabbrica was the lay office that managed the works, and the cathedral was still under construction for centuries after 1386. The year 1386 is the year usually given for the start of the building.",
    ),

    entry(
        databaseId = 66,
        kind = Kind.PRACTICE,
        section = "Crusades",
        title = "The indulgences of Bishop Despenser's crusade in Flanders, 1383",
        originalTitle = "Crusade of 1383",
        author = "Henry le Despenser, bishop of Norwich",
        year = 1383,
        summary = "Bishop Henry le Despenser led a crusade into Flanders in 1383 and offered indulgences to those who fought in it.",
        details = "The campaign was aimed at Flemish supporters of the rival pope Clement VII, and it collapsed within months. The indulgences show a practice of granting spiritual rewards for war against other Christians.",
        rare = true,
    ),

    entry(
        databaseId = 66,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Ars subtilior, the subtle notation of late fourteenth-century music",
        originalTitle = "Ars subtilior",
        author = "Composers of France, Italy and Spain",
        year = 1385,
        summary = "Ars subtilior is a late fourteenth-century style of music notation with complex rhythms, dated roughly from the 1370s to about 1420.",
        details = "The notation uses black and coloured note forms to show rhythms that were hard to sing, and the style was written in France, Italy and Spain. The year 1385 is a representative date within that range, and the record names the notation style rather than any one manuscript.",
        rare = true,
    ),

)
