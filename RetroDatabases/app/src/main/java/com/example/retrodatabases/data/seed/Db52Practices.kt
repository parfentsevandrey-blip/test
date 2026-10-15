package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1520 to 1530: Wittenberg's printed worship, catechism, hymns and pictures, the practices that set the words of a church in print. */
val db52Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 52,
        kind = Kind.PRACTICE,
        section = "Worship",
        title = "Luther's Latin Mass reformed for Wittenberg, 1523",
        originalTitle = "Formula missae et communionis pro ecclesia Vuittenbergensi",
        author = "Martin Luther",
        year = 1523,
        summary = "Luther's Formula missae of 1523 kept the Latin Mass for Wittenberg, cut the parts he judged unscriptural and allowed German hymns beside the service.",
        details = "Luther printed the order at Wittenberg in 1523 as a step between the old Latin Mass and the German service of 1526. Accounts differ on how far the cut parts were dropped in practice, since local churches adapted the order to their own needs.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.PRACTICE,
        section = "Worship",
        title = "Luther's German Mass, the service in the vernacular",
        originalTitle = "Deudsche Messe",
        author = "Martin Luther",
        year = 1526,
        summary = "Luther's German Mass of 1526 gave the Wittenberg congregation a service in German, with hymns sung by the people in place of the Latin chants.",
        details = "Luther published the German order in 1526 and kept the outline of the old service while putting German in the place of Latin. Accounts differ on how quickly the German order was taken up in other towns, since many kept the Latin service for years.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.PRACTICE,
        section = "Hymns",
        title = "The Wittenberg hymnal of 1524",
        originalTitle = "Geystlich Gesangk Buchleyn",
        author = "Johann Walter, with a preface by Martin Luther",
        year = 1524,
        summary = "The Wittenberg hymnal of 1524 gathered German hymns with their music, so that schools and choirs could sing them in church and at home.",
        details = "Johann Walter compiled the book at Wittenberg, and Luther wrote the preface, which praised music as a gift for the church. The printed hymns gave congregational singing in German a written standard, and the practice spread to other towns.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Luther's Small Catechism of 1529, questions and answers",
        originalTitle = "Der Kleine Katechismus",
        author = "Martin Luther",
        year = 1529,
        summary = "Luther's Small Catechism of 1529 taught the core of the faith through short questions, each with a short answer, for a child to learn by heart.",
        details = "The catechism was issued in 1529 and its question-and-answer form was taught in homes and schools. Its parts on the Ten Commandments, the Creed and the Lord's Prayer became the model for later Lutheran catechisms, and accounts differ on how many of its editions were printed in the first year.",
    ),

    entry(
        databaseId = 52,
        kind = Kind.PRACTICE,
        section = "Pictures",
        title = "Cranach's pictures of Christ and Antichrist, 1521",
        originalTitle = "Passional Christi und Antichristi",
        author = "Philipp Melanchthon, with woodcuts by Lucas Cranach",
        year = 1521,
        summary = "The Passional of Christ and Antichrist of 1521 set woodcuts of Christ's humility beside woodcuts of the pope's pomp, so that a reader could argue from pictures.",
        details = "Melanchthon wrote the text and Cranach cut the woodcuts, and the book was printed at Wittenberg in 1521. It is an early example of printed pictures used as an argument for a broad public, and accounts differ on how many copies of it were sold.",
    ),

)
