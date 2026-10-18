package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1280 to 1283: Alfonso X's book of chess, dice and tables, Franco of Cologne's treatise on measured music and John Peckham's optics textbook. */
val db76Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.TOOL,
        section = "Games",
        title = "Alfonso X's Libro de los juegos, a book of chess, dice and tables",
        originalTitle = "Libro de los juegos",
        author = "Alfonso X of Castile and his scholars",
        year = 1283,
        summary = "The Libro de los juegos, finished in 1283 under Alfonso X, is a Castilian manual on chess, dice and the table game, with rules for each.",
        details = "The book describes chess variants beside the familiar game, and its dice and table sections give rules and reasoning for those games. The year 1283 is the usual date for its completion, and the book is one of the earliest large Western treatments of these games.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.TOOL,
        section = "Music",
        title = "Franco of Cologne's Ars cantus mensurabilis, a treatise on measured music",
        originalTitle = "Ars cantus mensurabilis",
        author = "Franco of Cologne",
        year = 1280,
        summary = "Franco of Cologne's Ars cantus mensurabilis is a Latin treatise on measured music that sets out the values of long and breve notes, usually dated to about 1280.",
        details = "The treatise is the starting point of the system called mensural notation, in which each note is measured against its neighbours in time. Scholars differ on its date, so about 1280 is an approximation rather than a fixed year.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.TOOL,
        section = "Optics",
        title = "John Peckham's Perspectiva communis, an optics textbook of about 1280",
        originalTitle = "Perspectiva communis",
        author = "John Peckham",
        year = 1280,
        summary = "John Peckham's Perspectiva communis is a Latin optics textbook that taught the theory of vision drawn from Alhazen and Witelo to students of the 1280s.",
        details = "Peckham became archbishop of Canterbury in 1279, and the textbook dates from about the same years, though scholars differ on its exact date. The book was copied and studied for centuries, so it was a long-lived teaching text rather than a work of one year.",
        rare = true,
    ),

)
