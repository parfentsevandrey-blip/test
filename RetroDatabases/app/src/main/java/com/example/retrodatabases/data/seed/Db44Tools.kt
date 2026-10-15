package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1600 to 1610: a Chinese world map, two Kepler books, a Jesuit algebra, a machine book and Gilbert's study of the lodestone. */
val db44Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Cartography",
        title = "Ricci's world map in Chinese",
        originalTitle = "Kunyu wanguo quantu",
        author = "Matteo Ricci",
        year = 1602,
        summary = "Ricci's world map of 1602 showed the continents to readers in China, with the place names set out in Chinese.",
        details = "Ricci printed the map in Beijing in 1602, and later copies and editions followed in East Asia. Maps of this kind were among the Jesuit works that presented the wider world to educated readers in the Ming empire.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Kepler's Paralipomena on the eye and light",
        originalTitle = "Ad Vitellionem paralipomena, quibus astronomiae pars optica traditur",
        author = "Johannes Kepler",
        year = 1604,
        summary = "Kepler's optics of 1604 explained how the eye forms an image on the retina and stated that the light from a point weakens as the square of the distance grows.",
        details = "Kepler wrote the book as a supplement to the optics of Witelo, the Vitellio of its title, and it was printed in 1604. It is the work in which he set out the inverted image in the eye and the inverse-square rule for light.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Machines",
        title = "Zonca's theatre of machines",
        originalTitle = "Novo teatro di machine et edificii",
        author = "Vittorio Zonca",
        year = 1607,
        summary = "Zonca's book of 1607 gave engraved views of mills, pumps and other machines, a picture book of practical engineering for the trades of its day.",
        details = "It was printed in Padua in 1607, and its plates are a main source for historians of early machinery. The machines it shows are driven by water, animals and gears, and they are drawn so that a builder could follow them.",
        rare = true,
    ),

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Clavius's Algebra of 1608",
        originalTitle = "Algebra Christophori Clavii Bambergensis e Societate Iesu",
        author = "Christoph Clavius",
        year = 1608,
        summary = "Clavius's Algebra of 1608 was a Jesuit textbook that taught the solution of equations by fixed rules, written in Latin for students.",
        details = "The book was printed in Rome in 1608 by a Jesuit mathematician who taught at the Roman College. It sets out the rules of algebra step by step, and it was one of the textbooks through which Latin readers learned the subject.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Kepler's Astronomia nova",
        originalTitle = "Astronomia nova, seu physica coelestis",
        author = "Johannes Kepler",
        year = 1609,
        summary = "Kepler's book of 1609 found that the orbit of Mars is an ellipse, after years of trying circles against the observations he had inherited from Tycho Brahe.",
        details = "Kepler printed it at Heidelberg in 1609, and it is built on Tycho Brahe's observations of Mars, which Kepler received after Tycho's death in 1601. The book shows the steps by which he moved from circles to the ellipse, with the computations laid out in full.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.TOOL,
        section = "Magnetism",
        title = "Gilbert's De magnete",
        originalTitle = "De magnete, magneticisque corporibus, et de magno magnete tellure",
        author = "William Gilbert",
        year = 1600,
        summary = "Gilbert's book of 1600 treated the lodestone and the compass, and argued that the earth itself behaves as a great magnet.",
        details = "It was printed in London in 1600 and is among the first large books of experimental natural philosophy in England. Gilbert tested his claims with lodestone spheres and compass needles, and he described the attraction of rubbed amber as well.",
    ),

)
