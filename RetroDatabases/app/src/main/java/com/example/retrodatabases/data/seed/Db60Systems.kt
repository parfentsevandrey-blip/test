package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1440 to 1450: a Florentine public library, Portugal's law code, a papal library scheme, Eton's foundation and a monastic reform. */
val db60Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The public library of San Marco in Florence",
        originalTitle = "Biblioteca di San Marco",
        author = "Michelozzo, for Cosimo de' Medici",
        year = 1444,
        summary = "Cosimo de' Medici's library at San Marco, opened in 1444, set its books on reading desks for scholars and is often called the first public library in Europe.",
        details = "Michelozzo designed the building for the Dominican house of San Marco, and the books stood on desks in its hall. The claim to be the first public library in Europe is disputed, since other libraries had also admitted readers.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.SYSTEM,
        section = "Law codes",
        title = "The Afonsine Ordinances, the law code of Portugal",
        originalTitle = "Ordenacoes Afonsinas",
        author = "Afonso V of Portugal",
        year = 1446,
        summary = "The Afonsine Ordinances gathered the laws of Portugal into one national code, made for King Afonso V in 1446.",
        details = "The code was arranged in five books and drew on earlier royal laws and on Roman law. Later Portuguese codes were built on its plan, and this record dates the code to 1446.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.SYSTEM,
        section = "Papal libraries",
        title = "Nicholas V's copyists for the papal library",
        originalTitle = "Bibliotheca Apostolica Vaticana",
        author = "Pope Nicholas V",
        year = 1447,
        summary = "Pope Nicholas V, elected in 1447, began gathering books for a papal library and set copyists and translators to work on Greek and Latin texts.",
        details = "The scheme grew into the Vatican Library, which was formally founded in 1475. Accounts differ on how much of Nicholas V's plan was carried out before his death in 1455.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "Eton College, founded by Henry VI",
        originalTitle = "The King's College of Our Lady of Eton",
        author = "Henry VI",
        year = 1440,
        summary = "Henry VI founded Eton College in 1440 as a college of scholars with fixed places, and its written statutes set out how the scholars were chosen and governed.",
        details = "The foundation set up a school community beside Windsor Castle, with rules for its scholars and officers. It is recorded here as an organisation with written rules, and the college still exists.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.SYSTEM,
        section = "Monastic reform",
        title = "The Bursfelde reform of Benedictine houses",
        originalTitle = "Bursfelder Kongregation",
        author = "Abbots of the Bursfelde reform",
        year = 1446,
        summary = "Benedictine houses joined the Bursfelde reform in 1446, agreeing to shared statutes for their common life.",
        details = "The congregation took its name from the abbey of Bursfelde, where its statutes were agreed. It is a monastic system rather than a machine, and it is listed here because its statutes were written rules shared by a group of houses.",
    ),

)
