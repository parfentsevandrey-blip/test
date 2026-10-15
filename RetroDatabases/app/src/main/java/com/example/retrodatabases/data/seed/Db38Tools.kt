package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1660 to 1670: a book of microscope drawings, a learned journal, a history of the Royal Society, a chemical dialogue, a brain atlas and a book of comets. */
val db38Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Microscopy",
        title = "Hooke's Micrographia",
        originalTitle = "Micrographia: or some physiological descriptions of minute bodies made by magnifying glasses",
        author = "Robert Hooke",
        year = 1665,
        summary = "Hooke's illustrated book of 1665 showed insects, cloth, cork and other small things through the microscope, with plates he drew himself.",
        details = "The book was printed in London in 1665, and its plates show the flea, the louse and the chambers in cork that Hooke called cells. The word cell in its biological sense comes from this book, and the plates set a pattern for later books of microscopic drawings.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Publishing",
        title = "The Journal des scavans, an early learned journal",
        originalTitle = "Journal des scavans",
        author = "Denis de Sallo",
        year = 1665,
        summary = "A Paris periodical of 1665 that reviewed new books and reported on learned work, and is often named as the first learned journal in Europe.",
        details = "It appeared in January 1665, about two months before the Royal Society's Philosophical Transactions, and the two journals are often named together as the first of their kind. Its example was soon followed by other learned journals in several countries.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Publishing",
        title = "Sprat's History of the Royal Society",
        originalTitle = "The History of the Royal-Society of London, for the Improving of Natural Knowledge",
        author = "Thomas Sprat",
        year = 1667,
        summary = "Thomas Sprat's history of 1667 defended the Royal Society's plain style and its experimental method, and it included plates of the society's instruments.",
        details = "Sprat printed the book in London in 1667 as a public defence of the society and its ideal of plain language. Its plates include the weather instruments that Hooke made for the society, so the book is also a record of the instruments of the period.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Boyle's The Sceptical Chymist",
        originalTitle = "The Sceptical Chymist",
        author = "Robert Boyle",
        year = 1661,
        summary = "A dialogue of 1661 that attacked the traditional three principles of the chemists and urged that elements be found by experiment.",
        details = "Boyle wrote the book as a discussion among speakers who argue over what the elements of matter are, and it was printed in London in 1661. Its lasting effect was to make the chemical element a matter for experimental proof, though accounts differ on how far Boyle's own work met the standard he set.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Anatomy",
        title = "Willis's Cerebri anatome",
        originalTitle = "Cerebri anatome",
        author = "Thomas Willis",
        year = 1664,
        summary = "Thomas Willis's anatomy of the brain of 1664 described the arteries at its base and carried plates drawn by Christopher Wren.",
        details = "The book was printed in London in 1664, and Wren, then Savilian professor of astronomy at Oxford, drew its plates. The arterial ring at the base of the brain is still called the circle of Willis.",
    ),

    entry(
        databaseId = 38,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Hevelius's Cometographia",
        originalTitle = "Cometographia",
        author = "Johannes Hevelius",
        year = 1668,
        summary = "Johannes Hevelius's history of comets, printed at Danzig in 1668, gathered the sightings of comets from ancient times onwards.",
        details = "It was a compendium of reports rather than a theory of comets, and it gave later astronomers a survey of earlier sightings. Hevelius printed it in 1668 at Danzig, where he had his own observatory.",
        rare = true,
    ),

)
