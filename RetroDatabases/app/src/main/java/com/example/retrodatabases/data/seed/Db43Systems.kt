package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1610 to 1620: two chairs at Oxford, a bank at Hamburg, a national pharmacopoeia, a language society and a colonial lottery. */
val db43Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The Savilian chairs of geometry and astronomy at Oxford",
        originalTitle = "The Savilian chairs of geometry and astronomy at Oxford",
        author = "Sir Henry Savile",
        year = 1619,
        summary = "Savile endowed two professorships at Oxford, one for geometry and one for astronomy, so that the mathematical sciences had paid chairs in the university.",
        details = "Savile set up the chairs in 1619, and Henry Briggs and John Bainbridge were among the first to hold them, Briggs in geometry and Bainbridge in astronomy. The chairs gave mathematics a salaried place in the university.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "The Hamburg Bank",
        originalTitle = "Hamburger Bank",
        author = "The city council of Hamburg",
        year = 1619,
        summary = "The Hamburg Bank, founded in 1619, kept accounts for merchants in a book-money system, so that payments could be made by transfers between accounts.",
        details = "The bank was set up by the city of Hamburg in 1619 as an exchange bank, and its book-money accounts let merchants pay one another by transfer rather than in coin. It was kept by double entry, the method Italian merchants had long used, and it operated until the nineteenth century.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.SYSTEM,
        section = "Medicine",
        title = "The London Pharmacopoeia of 1618",
        originalTitle = "Pharmacopoeia Londinensis",
        author = "The College of Physicians of London",
        year = 1618,
        summary = "The London Pharmacopoeia gave physicians and apothecaries one official list of drugs and the way each preparation was to be made.",
        details = "The College of Physicians printed the first edition in 1618, after several years of work on a standard list of medicines. Its formulas were meant to stop each apothecary from making the same remedy in a different way, and later editions were revised to match changes in medicine.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.SYSTEM,
        section = "Language society",
        title = "The Fruitbearing Society of 1617",
        originalTitle = "Fruchtbringende Gesellschaft",
        author = "Louis of Anhalt-Koethen",
        year = 1617,
        summary = "The Fruitbearing Society was a German society founded in 1617 to keep the German language pure and to cultivate it against foreign words.",
        details = "Prince Louis of Anhalt-Koethen founded the society at Weimar in 1617, and its members took names and emblems and wrote to one another in German. It is often cited as an early attempt to set standards for German, although its members did not agree on a single method.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.SYSTEM,
        section = "Colonies",
        title = "The Virginia Company lottery of 1612",
        originalTitle = "The Virginia Company lottery of 1612",
        author = "The Virginia Company of London",
        year = 1612,
        summary = "In 1612 the Virginia Company raised money for its colony by a lottery, a licensed system of tickets and prizes that was used for several public causes in England.",
        details = "The lottery was licensed in London in 1612, and its tickets were sold to the public to fund the colony in Virginia. Lotteries of this kind were a common means of raising money for public works in England during the period.",
    ),

)
