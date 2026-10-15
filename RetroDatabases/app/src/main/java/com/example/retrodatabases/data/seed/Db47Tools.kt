package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1570 to 1580: Ortelius's atlas, a Latin Euclid, Dee's navigation book, Palladio's architecture, a seaman's manual and a Copernican almanac. */
val db47Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Atlases",
        title = "Ortelius's Theatrum orbis terrarum, the first modern atlas",
        originalTitle = "Theatrum orbis terrarum",
        author = "Abraham Ortelius",
        year = 1570,
        summary = "Ortelius's Theatrum of 1570 gathered maps of uniform size into one bound book, and it is often named the first modern atlas.",
        details = "The first edition appeared at Antwerp in 1570 with 53 maps, and it was reissued in many editions and languages in the years after. The book sold well, and the claim to be the first modern atlas depends on how an atlas is defined.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Euclid",
        title = "Commandino's Latin Euclid of 1572",
        originalTitle = "Euclidis Elementorum libri XV",
        author = "Federico Commandino",
        year = 1572,
        summary = "Commandino's Latin Euclid of 1572 gave the Elements in a new Latin version with commentary, and it became one of the most influential editions of the book.",
        details = "Commandino printed the edition at Pesaro in 1572, working from the Greek text and from earlier Latin versions. It was one of the most influential Latin editions of the Elements in the century that followed.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Dee's General and Rare Memorials on the art of navigation, 1577",
        originalTitle = "General and Rare Memorials pertayning to the Perfect Arte of Navigation",
        author = "John Dee",
        year = 1577,
        summary = "Dee's book of 1577 argued that England should build a navy and a trade empire on the art of navigation, and it is often linked with the phrase British Empire.",
        details = "The book was printed in London in 1577, and the phrase British Empire is often credited to Dee's writing of the time. Accounts differ on how far the book was an imperial programme and how far a plea for the navy, and on how much it shaped the voyages that followed.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Architecture",
        title = "Palladio's Four Books of Architecture, 1570",
        originalTitle = "I quattro libri dell'architettura",
        author = "Andrea Palladio",
        year = 1570,
        summary = "Palladio's Four Books of 1570 set out the classical orders, his own villas and churches, and measured drawings of ancient Roman buildings for builders to use.",
        details = "The book was printed at Venice in 1570, and its plans and elevations were drawn to scale so that a builder could follow them. Its rules for the proportions of the columns were copied for centuries, and it was widely used in English and American building.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Bourne's Regiment for the Sea, a seaman's manual of 1574",
        originalTitle = "A Regiment for the Sea",
        author = "William Bourne",
        year = 1574,
        summary = "Bourne's Regiment for the Sea of 1574 was an English manual for seamen, with rules for the compass and for the course a ship should steer.",
        details = "The book was printed in London in 1574 for English seamen and was used as a navigation handbook. Accounts differ on how much of its content was new, since Bourne drew on earlier writers on navigation and on the teaching of his time.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Digges's Perfit Description of the Caelestiall Orbes, 1576",
        originalTitle = "A Perfit Description of the Caelestiall Orbes",
        author = "Thomas Digges",
        year = 1576,
        summary = "Digges's Perfit Description of 1576 gave the Copernican system in English, with a diagram in which the fixed stars spread beyond the outermost sphere.",
        details = "It was printed in London in 1576 as part of Digges's almanac, the Prognostication, and it is one of the first English accounts of Copernicus's heliocentric system. Accounts differ on how far the diagram of an unbounded star field was Digges's own idea, and on how closely he had read Copernicus himself.",
    ),

)
