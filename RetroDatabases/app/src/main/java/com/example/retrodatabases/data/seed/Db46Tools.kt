package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1580 to 1590: Clavius's arithmetic and sundial books, Stevin's interest tables, Benedetti's book on falling bodies, Porta's natural magic and Vincenzo Galilei's dialogue on music. */
val db46Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Clavius's Epitome of practical arithmetic",
        originalTitle = "Epitome arithmeticae practicae",
        author = "Christopher Clavius",
        year = 1583,
        summary = "Clavius's Epitome of 1583 set out practical arithmetic for schools and merchants, from the four rules to fractions and the rule of three.",
        details = "The Epitome was printed in Rome in 1583 and was written for the schools of the Jesuits, where it was used as a textbook. Its rules for trade calculation include the rule of three.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Finance",
        title = "Stevin's tables of interest, the Tafelen van interest",
        originalTitle = "Tafelen van interest",
        author = "Simon Stevin",
        year = 1582,
        summary = "Stevin's tables of 1582 helped merchants work out interest on sums over several years at fixed rates without long calculation.",
        details = "The tables were printed in 1582 and are among Stevin's early practical works, written for merchants who needed interest figures quickly. They show the trade arithmetic that Stevin wanted to make simpler.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Mechanics",
        title = "Benedetti's Diversarum speculationum of 1585",
        originalTitle = "Diversarum speculationum mathematicarum et physicarum liber",
        author = "Giovanni Battista Benedetti",
        year = 1585,
        summary = "Benedetti's book of 1585 argued that bodies of the same material fall at the same speed in the same medium, whatever their weight, and so challenged Aristotle's view of falling bodies.",
        details = "The book was printed at Turin in 1585, and its argument on falling bodies was a step away from Aristotle's rule that speed follows weight. Historians differ on how far Benedetti anticipated the later work of Galileo on falling bodies.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Natural magic",
        title = "Porta's Magia naturalis, the twenty-book edition",
        originalTitle = "Magia naturalis",
        author = "Giambattista della Porta",
        year = 1589,
        summary = "Porta's Magia naturalis, in the enlarged edition of 1589, gathered natural science and optics in twenty books.",
        details = "The first edition appeared in 1558 in four books, and the enlarged edition of 1589 in twenty books was widely read as a guide to natural knowledge. Its optical books describe the camera obscura.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Music",
        title = "Vincenzo Galilei's Dialogo on ancient and modern music",
        originalTitle = "Dialogo della musica antica, et della moderna",
        author = "Vincenzo Galilei",
        year = 1581,
        summary = "Vincenzo Galilei's dialogue of 1581 compared ancient and modern music and argued against the number-based theory of intervals taught by Zarlino.",
        details = "The dialogue was printed in Florence in 1581, and its author was the father of the astronomer Galileo Galilei. Its dispute with Zarlino over the ratios of intervals was one of the main debates about music theory in the sixteenth century.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.TOOL,
        section = "Sundials",
        title = "Clavius's Gnomonices, the book of sundials",
        originalTitle = "Gnomonices libri octo",
        author = "Christopher Clavius",
        year = 1581,
        summary = "Clavius's Gnomonices of 1581 is an eight-book treatise on the construction of sundials on planes of many kinds.",
        details = "It was printed in Rome in 1581 and was used as a standard guide to dial geometry. Its eight books cover the hour lines of dials on flat and curved faces.",
    ),

)
