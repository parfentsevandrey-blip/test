package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1760 to 1770: a marine clock, spinning machines, a steam condenser, a glass instrument and two machines of motion. */
val db28Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Timekeeping",
        title = "Harrison's marine chronometer H4",
        originalTitle = "Harrison's marine chronometer H4",
        author = "John Harrison",
        year = 1761,
        summary = "A clock about five inches across, built to keep time at sea so that a ship could find its longitude, first tested on a voyage to Jamaica in 1761.",
        details = "The H4 was carried by Harrison's son William on HMS Deptford to Jamaica in 1761 and 1762, and its compensation for temperature changes kept its rate steady. At Port Royal it was reported about five seconds slow, which is roughly 1.25 nautical miles of longitude at the equator. Accounts differ on the length of the voyage, with one giving 81 days while the departure and landing dates give 62, and historians disagree on how far the five-second figure can be trusted.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "Hargreaves's spinning jenny",
        originalTitle = "Hargreaves's spinning jenny",
        author = "James Hargreaves",
        year = 1764,
        summary = "A frame that spun several threads at once from rovings, so that one spinner could work many spindles.",
        details = "Hargreaves built the first jenny in Lancashire about 1764. Accounts give dates from 1764 to 1767 for the first machine, and Hargreaves's patent is dated 1770, so the date of the invention is disputed. Its yarn was too weak for the warp, so it was used mainly for the weft.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "Arkwright's water frame",
        originalTitle = "Arkwright's water frame",
        author = "Richard Arkwright",
        year = 1769,
        summary = "A spinning frame driven by water power, whose rollers drew cotton out into a strong yarn fit for the warp.",
        details = "Arkwright's patent for the frame was granted in 1769. Roller spinning had been patented earlier by Lewis Paul and John Wyatt in 1738, so the origin of the idea is disputed. Arkwright's first water-powered mill at Cromford opened in 1771.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Steam",
        title = "Watt's separate condenser for the steam engine",
        originalTitle = "Watt's separate condenser for the steam engine",
        author = "James Watt",
        year = 1765,
        summary = "A condenser kept apart from the cylinder, so that the cylinder stayed hot and the engine used less steam.",
        details = "The idea of the separate condenser is dated to 1765, when Watt was repairing a model of a Newcomen engine at Glasgow University. He patented the condenser in 1769. The change saved fuel, and it became the core of the engines that Boulton and Watt later built.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Glass",
        title = "Franklin's glass armonica",
        originalTitle = "Franklin's glass armonica",
        author = "Benjamin Franklin",
        year = 1761,
        summary = "A set of glass bowls turned on a spindle and played by touching the wet rims, which Franklin developed into an instrument for the drawing room.",
        details = "Franklin built his glass armonica in London in 1761, working from earlier musical glasses. The bowls were fitted on a spindle that a foot treadle turned, and the player touched the rims with wet fingers. Accounts give 1761 and 1762 for the first instrument.",
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Steam",
        title = "Polzunov's twin-cylinder steam engine",
        originalTitle = "Polzunov's twin-cylinder steam engine",
        author = "Ivan Polzunov",
        year = 1766,
        summary = "A steam engine with two cylinders, designed by the Russian mechanic Ivan Polzunov at the Altai silver works in the 1760s.",
        details = "Polzunov designed the engine in 1763 and worked on it at Barnaul, where he was a mechanic at the silver works. He died in 1766, before the engine was tested.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Electricity",
        title = "Nairne's electrical machine",
        originalTitle = "Nairne's electrical machine",
        author = "Edward Nairne",
        year = 1767,
        summary = "A glass friction machine for making electric sparks, built by the London instrument maker Edward Nairne for lectures and experiments.",
        details = "Nairne was a London instrument maker who sold apparatus for electrical lectures and experiments. His machines turned a glass cylinder against leather pads to collect a charge, and they were made in the 1760s for teaching and research.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.DEVICE,
        section = "Music",
        title = "Zumpe's square piano",
        originalTitle = "Zumpe's square piano",
        author = "Johannes Zumpe",
        year = 1768,
        summary = "A small square piano built by the London maker Johannes Zumpe, which made a keyboard instrument cheap enough for home use.",
        details = "Zumpe worked in London from the late 1760s, and his square pianos were smaller and cheaper than the grand instruments of the period. He is often credited with popularising the square piano for domestic use.",
    ),
)
