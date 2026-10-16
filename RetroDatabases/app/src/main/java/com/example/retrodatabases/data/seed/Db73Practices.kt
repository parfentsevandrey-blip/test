package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1316 to 1317: a public dissection at Bologna, the Avignon registers, a bull on benefices, a bull on Franciscan poverty and the annates. */
val db73Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.PRACTICE,
        section = "Anatomy",
        title = "Public dissection of a body at Bologna, as Mondino taught it",
        originalTitle = "Dissectio publica",
        author = "Mondino de' Luzzi",
        year = 1316,
        summary = "At Bologna in the years around 1316, Mondino de' Luzzi taught anatomy by dissecting a human body in front of students while a reader read from the text.",
        details = "Public dissection was then a teaching practice in the Italian universities, and the body was cut to show the parts named in the book. Accounts differ on whether Mondino's demonstrations fell in 1315 or in 1316.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.PRACTICE,
        section = "Papacy",
        title = "The papal registers kept at Avignon, begun in 1316",
        originalTitle = "Registra Avenionensia",
        author = "The papal chancery at Avignon",
        year = 1316,
        summary = "The papal chancery at Avignon began to copy its outgoing letters into registers in the pontificate of John XXII, and the long series of these registers is a major source for medieval administration.",
        details = "Each register records the letters in the order in which they were issued, and the series was kept for most of the fourteenth century. The year 1316 is the date given for the start of the series.",
        rare = true,
    ),

    entry(
        databaseId = 73,
        kind = Kind.PRACTICE,
        section = "Papacy",
        title = "Execrabilis, John XXII's bull against holding several benefices",
        originalTitle = "Execrabilis",
        author = "Pope John XXII",
        year = 1317,
        summary = "Pope John XXII's bull Execrabilis of 1317 attacked the holding of several benefices at once, a practice that the papal court had allowed for many years.",
        details = "The bull sets out rules on the cumulation of benefices and on the dispensations that could still be granted. Accounts differ on how far the rules changed practice at the papal court.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.PRACTICE,
        section = "Franciscans",
        title = "Quorundam exigit, John XXII's bull on Franciscan poverty",
        originalTitle = "Quorundam exigit",
        author = "Pope John XXII",
        year = 1317,
        summary = "John XXII's bull Quorundam exigit of 1317 set out how the Franciscans were to hold their goods and houses, and it ruled on the rule of poverty that the order had disputed.",
        details = "The bull was one of the decisions that brought the question of Franciscan poverty before the papal court. Accounts differ on how far the Franciscans accepted its terms.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.PRACTICE,
        section = "Papacy",
        title = "Papal annates, the first year's income of a church office",
        originalTitle = "Annates",
        author = "The papal court under John XXII",
        year = 1316,
        summary = "Under John XXII the papal court collected annates, the first year's income of a church office, from the holders of benefices who took up their posts.",
        details = "Annates were paid to the papal chamber when a new holder took up a benefice, and their collection was tightened in the years of John XXII's pontificate. Accounts differ on whether the collection was systematic already in 1316 or only from 1317.",
    ),

)
