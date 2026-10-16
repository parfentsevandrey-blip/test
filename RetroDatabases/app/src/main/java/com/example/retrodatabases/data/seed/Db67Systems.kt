package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1371 to 1380: Ming paper money, the Ming civil examinations, the University of Erfurt, the Western Schism and the abolition of the Ming chancellery. */
val db67Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "The Da Ming baochao, Hongwu's paper money of 1375",
        originalTitle = "Da Ming baochao",
        author = "The Hongwu court",
        year = 1375,
        summary = "The Da Ming baochao was the first paper money issued under the Ming dynasty, put into circulation by the Hongwu court in 1375.",
        details = "The notes were valued in guan, strings of cash, and were meant to circulate alongside copper coins. Accounts differ on how many notes were printed and on how quickly they lost value, so the figures given by different sources vary.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Erfurt, founded in 1379",
        originalTitle = "Studium Erfordense",
        author = "The city of Erfurt and its church authorities",
        year = 1379,
        summary = "The University of Erfurt was founded in 1379, one of the first universities in the German lands after Prague and Vienna.",
        details = "It taught the arts alongside the higher faculties. Accounts differ on the exact founding document and on which pope approved it, so 1379 is the date usually given.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "The Ming abolition of the chancellery and the chief minister's post, 1380",
        originalTitle = "Zhongshu sheng",
        author = "The Hongwu emperor",
        year = 1380,
        summary = "In 1380 the Hongwu emperor abolished the central chancellery and the post of chief minister, so that the six ministries reported to him directly.",
        details = "The chancellor Hu Weiyong was executed for treason in the same year, and the change moved administrative decisions to the throne. Accounts differ on how far the emperor's power changed in practice, so 1380 is the date usually given.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.SYSTEM,
        section = "Examinations",
        title = "The Ming civil examinations, first held in 1371",
        originalTitle = "Keju",
        author = "The Hongwu court",
        year = 1371,
        summary = "The Hongwu court held a civil service examination in 1371, the start of the examination system that later dynasties kept for centuries.",
        details = "The usual date for the first examination is 1371, and the sources give few details of how it was run. The examination became the main road into official posts under the Ming, which is why later histories count 1371 as its beginning.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.SYSTEM,
        section = "Papacy",
        title = "The Western Schism of 1378, with rival popes at Rome and Avignon",
        originalTitle = "The Great Schism",
        author = "The cardinals of the papal court",
        year = 1378,
        summary = "In 1378 the cardinals divided between two popes, one at Rome and one at Avignon, starting a schism that lasted until 1417.",
        details = "Urban VI was chosen in April 1378 and Clement VII in September of the same year, and the two sides drew support from different kingdoms. Accounts differ on how free the first election was, since a crowd in Rome pressed the cardinals.",
    ),

)
