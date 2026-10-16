package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1343 to 1350: the treasury of merits, Florentine loans to a king, the flagellant processions, manor accounts after the plague and the jubilee of 1350. */
val db70Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.PRACTICE,
        section = "Indulgences",
        title = "The treasury of merits set out in the bull Unigenitus of 1343",
        originalTitle = "Unigenitus Dei Filius",
        author = "Pope Clement VI",
        year = 1343,
        summary = "In the bull Unigenitus of January 1343 Pope Clement VI described a treasury of the merits of Christ and the saints, from which indulgences could be drawn.",
        details = "The idea of a treasury of merits gave the theology behind indulgences, and it became a point of dispute when indulgences were later sold. Historians differ on how far the bull changed existing practice, since the notion of merits was discussed before 1343.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.PRACTICE,
        section = "Banking",
        title = "The Bardi and Peruzzi loans to Edward III and the default that broke them",
        originalTitle = "Bardi e Peruzzi",
        author = "The Bardi and Peruzzi companies of Florence",
        year = 1343,
        summary = "Florentine banks such as the Bardi and Peruzzi lent heavily to Edward III of England, and their failure in 1343 followed the king's default on those loans.",
        details = "The Bardi were the chief lenders, and the Peruzzi also lost heavily, so both companies were in serious trouble by 1343. Accounts differ on how far the king's default alone caused the collapse, since the companies had other debts and losses.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.PRACTICE,
        section = "Penance",
        title = "The flagellant processions of 1349 and the pope's ban on them",
        originalTitle = "Flagellants",
        author = "Bands of penitents from German and Italian towns",
        year = 1349,
        summary = "Bands of flagellants walked from town to town in 1349 whipping themselves in public penance, and Pope Clement VI condemned the processions that year.",
        details = "The processions had spread through German and Italian towns in the plague years of 1348 and 1349. Accounts differ on how many marchers took part, and the papal condemnation followed the disorder that the bands had caused.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.PRACTICE,
        section = "Estates",
        title = "English manor accounts that listed empty holdings after the Black Death",
        originalTitle = "Compotus roll",
        author = "Stewards and bailiffs of English manors",
        year = 1349,
        summary = "After the Black Death the accounts of English manors recorded more holdings left vacant, and stewards had to change how they totalled rents and dues.",
        details = "A compotus roll was the yearly account that a manor's bailiff gave to the lord, and many rolls for the years after 1349 list holdings standing empty. Historians differ on how far rents fell in those years, so the effect of the plague on the rolls is debated.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.PRACTICE,
        section = "Pilgrimage",
        title = "The jubilee of 1350, when Clement VI shortened the interval to fifty years",
        originalTitle = "Anno Santo",
        author = "Pope Clement VI",
        year = 1350,
        summary = "Pope Clement VI shortened the interval between Roman jubilees from a hundred years to fifty, and the jubilee of 1350 was the first held at the shorter interval.",
        details = "Pilgrims came to Rome in large numbers in 1350, and the indulgences of the year drew many from across Europe. Accounts differ on the date at which Clement VI set the new interval.",
    ),

)
