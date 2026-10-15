package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1750 to 1760: a comet forecast, a magnetising method, a southern survey, an honour and an arc survey. */
val db29Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Clairaut's prediction of Halley's comet",
        originalTitle = "Computation of the comet's return",
        author = "Alexis Clairaut, with Lalande and Lepaute",
        year = 1759,
        summary = "Clairaut's forecast in November 1758 placed the return of Halley's comet in mid-April 1759, and the comet passed perihelion about a month before that.",
        details = "A team of computers led by Clairaut worked out the comet's path with the pull of Jupiter and Saturn included. The comet reached perihelion on 13 March 1759, and accounts differ on the forecast day, given as 13 April or 15 April.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.PRACTICE,
        section = "Magnetism",
        title = "Canton's method of making artificial magnets",
        originalTitle = "A Method of Making Artificial Magnets",
        author = "John Canton",
        year = 1751,
        summary = "A method of making magnets from ordinary steel bars, without the natural lodestone, which Canton described in 1751.",
        details = "Canton described the method in the Philosophical Transactions for 1751, using steel bars that were stroked to take a charge. Michell's treatise of 1750 covers artificial magnets as well, so the two works share a subject.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.PRACTICE,
        section = "Astronomy",
        title = "Lacaille's observations at the Cape",
        originalTitle = "Observations at the Cape of Good Hope",
        author = "Nicolas-Louis de Lacaille",
        year = 1751,
        summary = "Lacaille's observations of southern stars from the Cape of Good Hope, begun in 1751, which later filled a star catalogue.",
        details = "Lacaille reached the Cape in 1751 and spent about two years measuring the southern stars and an arc of the meridian there. His measurements went into a catalogue published after his death.",
        rare = true,
    ),

    entry(
        databaseId = 29,
        kind = Kind.PRACTICE,
        section = "Learned honours",
        title = "The Copley medal for Franklin",
        originalTitle = "Copley Medal",
        author = "Royal Society",
        year = 1753,
        summary = "The Royal Society's Copley medal given to Franklin in 1753 for his electrical experiments, which carried his work to a wider public.",
        details = "Franklin received the medal in 1753 for his experiments with electricity. The Royal Society's medal was its highest honour for research, and it spread the news of Franklin's work across Europe.",
    ),

    entry(
        databaseId = 29,
        kind = Kind.PRACTICE,
        section = "Geodesy",
        title = "Boscovich's survey of the Papal States",
        originalTitle = "De litteraria expeditione per pontificiam ditionem",
        author = "Roger Boscovich and Christopher Maire",
        year = 1755,
        summary = "Boscovich and Maire measured an arc of the meridian through the Papal States, and their account of the survey was published in 1755.",
        details = "The survey was carried out in the early 1750s, and the account was printed in Rome in 1755. It is one of the arc measurements that tested the figure of the Earth.",
    ),

)
