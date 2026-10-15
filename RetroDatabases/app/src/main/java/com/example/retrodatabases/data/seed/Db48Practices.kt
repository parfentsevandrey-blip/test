package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1560 to 1570: the Frankfurt fair catalogue, Geneva verse numbers, Ramus's pairs, Inca quipus and notched tally sticks. */
val db48Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.PRACTICE,
        section = "Book trade",
        title = "The Frankfurt autumn book fair catalogue",
        originalTitle = "Novorum Librorum quos Nundinae Atumnales, Francoforti Anno 1564 celebratae, Venales Exhibuerent",
        author = "Georg Willer",
        year = 1564,
        summary = "From 1564 a printed list of the new books offered at the Frankfurt autumn fair went out ahead of each fair, and the series ran for many years.",
        details = "The first issue was published by Georg Willer, an Augsburg publisher, for the autumn fair of 1564, and the list later moved to Frankfurt, where it previewed the next fair. The series is described as running until 1749.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.PRACTICE,
        section = "Reference",
        title = "Verse numbers in the Geneva Bible",
        originalTitle = "The Geneva Bible",
        author = "Geneva translators",
        year = 1560,
        summary = "The Geneva Bible of 1560 numbered the verses of every chapter, so a reader could cite a passage by chapter and verse.",
        details = "It was printed at Geneva in 1560, and its verse numbers followed a practice that Robert Estienne had introduced for the New Testament in 1551. Accounts differ on how much the English numbering owed to Estienne's earlier editions.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Ramus's method of dividing a subject in pairs",
        originalTitle = "Scholarum mathematicarum libri unus et triginta",
        author = "Petrus Ramus",
        year = 1569,
        summary = "Ramus taught by splitting each subject into pairs of parts, a method of division that he used in his teaching books.",
        details = "The method, called dichotomy, sorted each topic into two parts at every step, and it was taught in schools for generations. Earlier logicians had used division in pairs, so accounts differ on how far the method was Ramus's own.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.PRACTICE,
        section = "Records",
        title = "Quipu cords for counting goods in the Inca state",
        originalTitle = "Quipu",
        author = "Inca record keepers",
        year = 1567,
        summary = "Quipus were knotted cords that Inca record keepers used to count goods and people, and Spanish officials described the system in the 1560s.",
        details = "The knots on each cord were read in units of ten, so one cord could hold a decimal count. Accounts differ on how much of the system Spanish writers understood, and no single author is named because several officials wrote about it.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "Notched tally sticks in the English Exchequer",
        originalTitle = "Tally sticks",
        author = "Exchequer officers",
        year = 1566,
        summary = "Tally sticks were notched wooden sticks split lengthwise, so that the payer and the Exchequer each kept a half with matching notches.",
        details = "The English Exchequer still recorded payments on tallies in the 1560s, and the practice ended in 1826. Accounts differ on how far the split stick guarded against forgery, so the record gives the practice without a claim about its security.",
    ),

)
