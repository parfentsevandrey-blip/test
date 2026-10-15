package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1610 to 1620: the logarithm canon, the first telescope book, two books of Jupiter's moons and a map atlas, Burgi's progress tables and Kepler's Epitome. */
val db43Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Logarithms",
        title = "Napier's Mirifici logarithmorum canonis descriptio",
        originalTitle = "Mirifici logarithmorum canonis descriptio",
        author = "John Napier",
        year = 1614,
        summary = "Napier's book of 1614 set out logarithms as a way of turning multiplication into addition, with tables of the logarithms of sines and an explanation of how they were built.",
        details = "The book was printed at Edinburgh in 1614, and Napier defined his logarithms through two points moving along lines at related speeds. The tables gave the logarithms of the sines for each degree and minute, which made trigonometric calculations much quicker for astronomers and surveyors.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Galileo's Sidereus nuncius",
        originalTitle = "Sidereus nuncius",
        author = "Galileo Galilei",
        year = 1610,
        summary = "Galileo's short book of 1610 reported the mountains of the Moon, the moons of Jupiter and the many stars of the Milky Way, all seen through his telescope.",
        details = "The book was printed at Venice in the spring of 1610 and was the first printed report of telescopic observations of the sky. It made the case for the telescope as an instrument of discovery, and it named the Medicean stars after the Florentine court.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Marius's Mundus Iovialis",
        originalTitle = "Mundus Iovialis anno M.DC.IX detectus ope perspicilli Belgici",
        author = "Simon Marius",
        year = 1614,
        summary = "Marius's book of 1614 claimed to have seen Jupiter's moons in 1609 and gave them the names Io, Europa, Ganymede and Callisto, with tables of their motions.",
        details = "The book was printed at Nuremberg in 1614, and Marius said he had observed the moons with a Dutch telescope before Galileo published his report. Galileo's claim to priority rests on his earlier published report, so the question of who saw the moons first is still disputed in the sources.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Maps",
        title = "Speed's Theatre of the Empire of Great Britaine",
        originalTitle = "The Theatre of the Empire of Great Britaine",
        author = "John Speed",
        year = 1611,
        summary = "Speed's atlas of 1611 gave the counties of England, Wales and Scotland as separate maps, one of the first printed atlases of the British counties.",
        details = "The atlas was printed in London, with the maps engraved and sold in a bound volume, and it was used by readers and by later mapmakers as the standard county record. Speed's maps show towns, roads and the arms of the gentry, which made them a reference as well as an ornament.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Logarithms",
        title = "Burgi's Progress Tabulen",
        originalTitle = "Aritmetische und Geometrische Progress Tabulen",
        author = "Jost Burgi",
        year = 1620,
        summary = "Burgi's progress tables of 1620 listed powers of 1.0001 scaled by 10 to the eighth, so that a sum of exponents gave a product of the scaled numbers.",
        details = "The tables were printed at Prague in 1620, and the black column grows by a factor of 1.0001 from one row to the next, up to exponent 23027. The construction was made independently of Napier's, and the date when Burgi first worked out his tables is disputed, since the sources do not agree and one date often given is around 1588.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Kepler's Epitome of Copernican astronomy",
        originalTitle = "Epitome astronomiae Copernicanae",
        author = "Johannes Kepler",
        year = 1618,
        summary = "Kepler's Epitome of Copernican astronomy was a textbook of tables and rules for predicting the positions of the planets, arranged so that a reader could compute them.",
        details = "The first part was printed at Linz in 1618, and the later books followed in the early 1620s. The Epitome is a working manual, with the rules set out step by step, and it used Kepler's own laws of planetary motion to give the tables their form.",
    ),

)
