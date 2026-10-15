package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1810 to 1820: a garden telegraph, a steam press, a metronome and five machines of the decade. */
val db23Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Telegraphs",
        title = "Ronalds's electric telegraph",
        originalTitle = "Descriptions of an Electrical Telegraph (1823)",
        author = "Francis Ronalds",
        year = 1816,
        summary = "A telegraph built in Ronalds's garden at Hammersmith, which sent signals along a long wire by static electricity.",
        details = "Ronalds built the working line in 1816, and he described it in print in 1823. The record uses the 1816 date of construction, and the printed description is the later date.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Koenig's steam printing press",
        originalTitle = "Steam printing press for The Times (1814)",
        author = "Friedrich Koenig and Andreas Bauer",
        year = 1814,
        summary = "A press driven by steam that replaced hand presses for newspapers, first used for The Times in 1814.",
        details = "The machine printed The Times in November 1814. An earlier English patent is dated to 1810, so accounts differ on when the invention was made, and the record uses 1814, the year of the first newspaper use.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Music",
        title = "Maelzel's metronome",
        originalTitle = "Metronome patent (1815)",
        author = "Johann Nepomuk Maelzel",
        year = 1815,
        summary = "A clockwork pendulum that ticked a set number of beats per minute, so that composers could mark an exact tempo.",
        details = "Maelzel took out the patent in 1815, but accounts credit Dietrich Nikolaus Winkel with a similar pendulum device, so the priority is disputed. The record uses 1815, the year of the patent.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Mining",
        title = "Davy's safety lamp for miners",
        originalTitle = "Account of a safety lamp for coal mines (1816)",
        author = "Humphry Davy",
        year = 1816,
        summary = "A miner's lamp with a fine wire gauze around the flame, which kept the flame from igniting gas outside the lamp.",
        details = "Davy's paper on the lamp was published in 1816. George Stephenson's rival lamp of the same years is credited with an independent invention, so the priority is disputed. The record uses the 1816 publication date.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Brewster's kaleidoscope",
        originalTitle = "Kaleidoscope, announced in 1816 and patented in 1817",
        author = "David Brewster",
        year = 1816,
        summary = "A tube of mirrors holding loose coloured pieces, which formed symmetrical patterns as the tube was turned.",
        details = "Brewster announced the instrument in 1816 and took out a patent in 1817, and his treatise on it appeared in 1819. The record uses the 1816 date of the first announcement.",
        rare = true,
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Tunnelling",
        title = "Brunel's tunnelling shield",
        originalTitle = "Patent for a tunnelling shield (1818)",
        author = "Marc Isambard Brunel",
        year = 1818,
        summary = "A cast-iron frame with compartments that held the face of a tunnel while workers cut the earth in safe sections.",
        details = "Brunel patented the shield in 1818, and it was used on the Thames Tunnel, whose construction began in 1825. The record uses the 1818 patent date.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Locomotives",
        title = "Stephenson's Blucher locomotive",
        originalTitle = "Blucher, colliery steam locomotive at Killingworth (1814)",
        author = "George Stephenson",
        year = 1814,
        summary = "A steam locomotive built at Killingworth to haul coal wagons on a colliery railway.",
        details = "Blucher was built at Killingworth in 1814. Accounts differ on which early engine was the first to haul under steam, so the record uses the 1814 build date only.",
    ),

    entry(
        databaseId = 23,
        kind = Kind.DEVICE,
        section = "Heat engines",
        title = "Stirling's hot-air engine",
        originalTitle = "Patent for an economiser, the air engine (1816)",
        author = "Robert Stirling",
        year = 1816,
        summary = "A closed-cycle engine that moved air between the hot and cold ends of a sealed cylinder, with a regenerator to save heat.",
        details = "Stirling took out a Scottish patent in 1816 for his air engine, and later engines of the same kind were built. The record uses the 1816 date of the patent.",
        rare = true,
    ),
)
