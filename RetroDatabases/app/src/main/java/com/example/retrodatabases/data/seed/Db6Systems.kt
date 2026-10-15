package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Operating systems, monitors and large systems of 1950 to 1960: batch monitors, an air defence network and an airline reservation system. */
val db6Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 6,
        kind = Kind.SYSTEM,
        section = "Batch monitors",
        title = "GM-NAA I/O",
        originalTitle = "General Motors and North American Aviation Input/Output System",
        author = "General Motors and North American Aviation, with IBM",
        year = 1956,
        summary = "The first operating system for the IBM 704: a monitor that ran jobs one after another and handled their input and output.",
        details = "General Motors and North American Aviation wrote it for their IBM 704s. It cut the manual work between jobs, so that one program could start as soon as the previous one had finished. " +
            "The SHARE Operating System of 1959 built on its input and output design.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.SYSTEM,
        section = "Real-time networks",
        title = "SAGE air defence network",
        originalTitle = "Semi-Automatic Ground Environment (SAGE)",
        author = "MIT Lincoln Laboratory, IBM and the US Air Force",
        year = 1958,
        summary = "A continental air defence system in which central computers tracked aircraft in real time and showed the operators what they were tracking.",
        details = "The central computers, the AN/FSQ-7, were built by IBM from a design that began in October 1952 as Whirlwind II at MIT. " +
            "The first SAGE centre became operational on 1 July 1958, and the full system was complete only in 1963.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.SYSTEM,
        section = "Batch monitors",
        title = "SHARE Operating System",
        originalTitle = "SHARE Operating System (SOS) for the IBM 709",
        author = "The SHARE user group",
        year = 1959,
        summary = "The operating system that the SHARE user group wrote for the IBM 709 and then adapted to the transistorised 7090.",
        details = "It was built on the GM-NAA I/O design, and its aim was to make programs easy to exchange between installations. " +
            "Papers about it appeared in the April 1959 issue of the Journal of the ACM. IBM stopped supporting SOS in 1962, and its successor IBSYS was not compatible with it.",
    ),

    entry(
        databaseId = 6,
        kind = Kind.SYSTEM,
        section = "Batch monitors",
        title = "IBSYS",
        originalTitle = "IBSYS monitor for the IBM 7090 and 7094",
        author = "IBM",
        year = 1960,
        summary = "IBM's tape-based monitor for the 709, 7090 and 7094, which read the control cards that stood between each program and its data.",
        details = "Reference works give 1960 as the first release, and no primary IBM source settles that date. Its lineage is often traced to the FORTRAN Monitor System, though Bell Labs' BESYS is named as the more probable influence. " +
            "A 1963 article in the IBM Systems Journal describes its design.",
        rare = true,
    ),

    entry(
        databaseId = 6,
        kind = Kind.SYSTEM,
        section = "Commercial systems",
        title = "SABRE reservations",
        originalTitle = "SABRE (Semi-Automated Business Research Environment)",
        author = "IBM and American Airlines",
        year = 1960,
        summary = "The airline reservation system that IBM built for American Airlines, handling bookings in real time from terminals.",
        details = "Most sources date its first working version to 1960, with the planning starting in the late 1950s. " +
            "It was among the first large real-time transaction systems.",
    ),
)
