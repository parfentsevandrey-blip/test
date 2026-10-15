package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1650 to 1660: Wallis's infinite arithmetic, Mengoli's fractions, Wallis's mathematics, Riccioli's lunar map, a Dutch exercise book and Hobbes's reckoning of reason. */
val db39Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Series",
        title = "Wallis's Arithmetica infinitorum, the book of infinite series and products",
        originalTitle = "Arithmetica infinitorum",
        author = "John Wallis",
        year = 1656,
        summary = "Wallis's Latin treatise gave a way to find the values of infinite series and products, and it contains the infinite product for pi that carries his name.",
        details = "The book was printed at Oxford in 1656 and treats the areas and sums of curves by infinite series and by interpolation between terms. It also printed Brouncker's continued fraction for four over pi, so the book holds two of the period's best-known routes to pi.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Series",
        title = "Mengoli's arithmetic quadrature of fractions",
        originalTitle = "Novae quadraturae arithmeticae seu de additione fractionum",
        author = "Pietro Mengoli",
        year = 1650,
        summary = "Mengoli's book showed how to add infinite series of fractions by a telescoping method that cancels most of the terms.",
        details = "The book was printed at Bologna in 1650 and collected methods for sums of reciprocal products, including the pronic reciprocals whose sum is exactly one. The telescoping trick it used is still taught as a way to find the sums of series.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Wallis's Mathesis universalis, a general course in mathematics",
        originalTitle = "Mathesis universalis",
        author = "John Wallis",
        year = 1657,
        summary = "Wallis's Mathesis universalis gave a general introduction to arithmetic, algebra and geometry in Latin for students.",
        details = "It was printed at Oxford in 1657 and was meant as a textbook of the common methods of mathematics. Books of this sort carried the new algebraic notation to students, and Wallis's later Treatise of Algebra covered much of the same ground.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Riccioli's Almagestum novum and its lunar map",
        originalTitle = "Almagestum novum",
        author = "Giovanni Battista Riccioli",
        year = 1651,
        summary = "Riccioli's large astronomy treatise of 1651 carried a map of the Moon, and many of the names on it are still in use for lunar features.",
        details = "The map was drawn with Francesco Maria Grimaldi and printed at Bologna in 1651, giving the craters the names of astronomers and the dark plains names drawn from weather and states of mind. The naming scheme lasted, and it is the part of the map that modern lunar maps keep.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Collections",
        title = "van Schooten's Exercitationes mathematicae, a Dutch collection",
        originalTitle = "Exercitationes mathematicae",
        author = "Frans van Schooten",
        year = 1657,
        summary = "Van Schooten's collection of mathematical exercises printed several treatises of the Dutch school, among them Huygens's study of dice and other chances.",
        details = "It was printed at Leiden in 1657, and Huygens's De ratiociniis in ludo aleae appeared in it as an appendix. The collection shows how Dutch mathematicians shared their work in print, one treatise after another.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.TOOL,
        section = "Philosophy",
        title = "Hobbes's Leviathan and reasoning as reckoning",
        originalTitle = "Leviathan",
        author = "Thomas Hobbes",
        year = 1651,
        summary = "Hobbes wrote in Leviathan that reasoning is nothing but adding and subtracting names, a view that made thought a kind of calculation.",
        details = "The book was printed in London in 1651, and its fifth chapter treats reasoning as reckoning with the names of things. Hobbes developed the idea in his De corpore of 1655, where computation is set out as the basis of reasoning.",
    ),

)
