package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1850 to 1860: a difference engine, printing and duplex telegraphs, railway signalling, a cable instrument, a sound recorder and an induction coil. */
val db19Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Difference engines",
        title = "Scheutz's difference engine",
        originalTitle = "Difference engine built by Georg and Edvard Scheutz (1853)",
        author = "Georg Scheutz and Edvard Scheutz",
        year = 1853,
        summary = "The Scheutz difference engine was a working calculating machine that printed mathematical tables from differences, built in Stockholm to Babbage's method.",
        details = "Georg Scheutz and his son Edvard built the engine in Stockholm, and it was completed in 1853. Later engines were built for other buyers, and the Scheutz machines were among the first difference engines to be built and used.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Printing telegraphs",
        title = "Hughes printing telegraph",
        originalTitle = "Printing telegraph patent (1855)",
        author = "David Edward Hughes",
        year = 1855,
        summary = "Hughes's printing telegraph let an operator type letters on a keyboard and have them printed at the far end, so messages no longer needed a Morse operator.",
        details = "Accounts differ on the patent date: some give 1855, while the printing-telegraph patents credited to Hughes are dated from 1856 to 1859. The instrument reached England in 1857, and it was taken up in France after Hughes moved there in 1860.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Duplex working",
        title = "Gintl's duplex telegraph",
        originalTitle = "Duplex telegraph for two-way working on one wire (1853)",
        author = "Carl Gintl",
        year = 1853,
        summary = "Gintl's duplex telegraph let two messages pass along one wire in opposite directions at the same time, which doubled the traffic a single line could carry.",
        details = "Each station balanced its own receiving instrument against the line, so that it could send and receive together without the two signals interfering. Duplex working spread through the telegraph companies in the following decades.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Railway signalling",
        title = "Saxby's interlocking signal frame",
        originalTitle = "Interlocking frame for points and signals (1856)",
        author = "John Saxby",
        year = 1856,
        summary = "Saxby's interlocking frame joined the levers for points and signals so that a signal could not clear while a conflicting route was set.",
        details = "Accounts date the first interlocked installation to 1856, and the frame's locking bars turned the signalling of junctions into a fixed rule of mutual exclusion. Such frames became the standard arrangement in railway signal boxes.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Printing telegraphs",
        title = "Phelps's printing telegraph",
        originalTitle = "Printing telegraph of George May Phelps (1859)",
        author = "George May Phelps",
        year = 1859,
        summary = "Phelps's printing telegraph used synchronised distributors, so that the receiver printed each letter at the moment the sender selected it.",
        details = "Phelps refined the printing instruments of his time, and his machines were later combined with the designs of Hughes and House. Synchronised distribution was the principle that made practical printing telegraphs possible.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Cable instruments",
        title = "Kelvin's mirror galvanometer",
        originalTitle = "Reflecting galvanometer for submarine cables (1858)",
        author = "William Thomson, later Lord Kelvin",
        year = 1858,
        summary = "A small mirror on a light magnet swung in a coil, so a weak cable current turned a spot of light across a scale that could be read.",
        details = "Thomson designed the instrument to detect the feeble currents that reached the far end of a long submarine cable. It was used in the work on the Atlantic cable of 1858.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Sound recording",
        title = "Scott's phonautograph",
        originalTitle = "Phonautographe (1857)",
        author = "Edouard-Leon Scott de Martinville",
        year = 1857,
        summary = "The phonautograph traced sound waves as a line on smoked paper, the first device to record sound, although it could not play the recording back.",
        details = "Scott de Martinville patented the device in Paris in 1857. The traces stayed silent until modern digital methods were used to recover the sounds from them.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.DEVICE,
        section = "Induction coils",
        title = "Ruhmkorff's induction coil",
        originalTitle = "Induction coil (1851)",
        author = "Heinrich Daniel Ruhmkorff",
        year = 1851,
        summary = "Ruhmkorff's induction coil turned a low battery voltage into high-voltage sparks, and coils of this kind later powered spark transmitters.",
        details = "The coil used a make-and-break interrupter to induce a high voltage in a secondary winding. Such coils later powered spark transmitters and X-ray tubes.",
    ),
)
