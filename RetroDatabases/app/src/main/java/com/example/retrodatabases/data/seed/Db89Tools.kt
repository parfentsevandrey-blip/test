package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1150 to 1160: al-Idrisi's world map for Roger II, Bhaskara's Bijaganita, Peter Lombard's Sentences and John of Salisbury's Policraticus. */
val db89Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 89,
        kind = Kind.TOOL,
        section = "Geography",
        title = "Al-Idrisi's Tabula Rogeriana, the world map made for King Roger II",
        originalTitle = "Tabula Rogeriana",
        author = "Muhammad al-Idrisi",
        year = 1154,
        summary = "Al-Idrisi completed his world map for King Roger II of Sicily in 1154, a map that came with a book describing the lands it shows.",
        details = "Accounts describe the map as engraved on a round silver plate, and they give its weight as 300 pounds in one report and 450 in another, so the sources disagree. Ten manuscript copies of the accompanying book survive, but the later history of the silver plate is not reliably recorded.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.TOOL,
        section = "Sanskrit mathematics",
        title = "Bhaskara II's Bijaganita, the algebra part of the Siddhanta Shiromani",
        originalTitle = "Bijaganita",
        author = "Bhaskara II",
        year = 1150,
        summary = "Bhaskara II's Bijaganita, the algebra part of his Siddhanta Shiromani, teaches the solution of indeterminate equations, including the cyclic method for equations of the Pell type.",
        details = "The Bijaganita is the algebra section of the Siddhanta Shiromani, and the Lilavati is a separate section of the same work. The year 1150 is the usual date for the whole treatise, and the cyclic method is the best-known rule in the algebra section.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.TOOL,
        section = "Theology",
        title = "Peter Lombard's Sentences, the textbook of theology in the schools",
        originalTitle = "Libri quattuor Sententiarum",
        author = "Peter Lombard",
        year = 1155,
        summary = "Peter Lombard's Four Books of Sentences, written in the years around 1155 to 1158, became the standard theology textbook of the medieval schools.",
        details = "The work gathers the opinions of earlier writers on set topics and arranges them in four books, each cut into numbered distinctions. Its exact dates are not fixed, so 1155 is the start of the range usually given, and commentaries on it were written for centuries.",
    ),

    entry(
        databaseId = 89,
        kind = Kind.TOOL,
        section = "Political thought",
        title = "John of Salisbury's Policraticus, a handbook for rulers and courtiers",
        originalTitle = "Policraticus",
        author = "John of Salisbury",
        year = 1159,
        summary = "John of Salisbury completed the Policraticus in 1159, a Latin handbook on the duties of rulers and the failings of courtiers.",
        details = "The book is in eight books and draws on classical and Christian authors, and it criticises the vices of courtiers at length. It was written in the same year as the Metalogicon, so the two books are usually dated together to 1159.",
    ),

)
