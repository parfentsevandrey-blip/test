package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1960 to 1970: time-sharing, virtual memory, layered design, a computer network and the software of a spacecraft. */
val db7Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.SYSTEM,
        section = "Time-sharing",
        title = "CTSS",
        originalTitle = "Compatible Time-Sharing System (CTSS)",
        author = "Fernando Corbató and the MIT Computation Center",
        year = 1961,
        summary = "MIT's time-sharing system, which let several people use one computer from typewriter terminals at the same time, and one of the first to run in regular service.",
        details = "A prototype was shown in November 1961 on a modified IBM 709 with four terminals. It ran on the 7090 from the fall of 1962 and served Computation Center users from the summer of 1963. " +
            "A separate 7094, called the red machine, kept CTSS running until July 1973.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.SYSTEM,
        section = "Virtual memory",
        title = "Atlas Supervisor",
        originalTitle = "Atlas Supervisor for the Manchester Atlas",
        author = "Tom Kilburn's group at Manchester University",
        year = 1962,
        summary = "The supervisor of the Atlas computer, which managed paging: pages of drum and core were moved in and out as the programs needed them.",
        details = "Atlas went into service in 1962, and its one-level store treated core and drum as a single address space. Standard references name the Atlas Supervisor as the earliest system to adopt paging, though the claim depends on how paging is defined. " +
            "An IEEE Milestone for Atlas calls virtual memory its chief innovation.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.SYSTEM,
        section = "Layered design",
        title = "THE multiprogramming system",
        originalTitle = "The Structure of the 'THE' Multiprogramming System",
        author = "Edsger Dijkstra and his group at Eindhoven",
        year = 1968,
        summary = "Dijkstra's layered operating system at the Technische Hogeschool Eindhoven, in which each layer depends only on the layers below it.",
        details = "The group built the system in 1965 and 1966, and Dijkstra's paper on it appeared in Communications of the ACM in 1968. Standard references credit it with one of the first uses of semaphores in programming, a claim worth checking against the paper.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.SYSTEM,
        section = "Networks",
        title = "ARPANET",
        originalTitle = "Advanced Research Projects Agency Network",
        author = "DARPA, with BBN and the host sites",
        year = 1969,
        summary = "The packet-switched network that linked research sites, whose first message went from UCLA to SRI on 29 October 1969.",
        details = "Interface Message Processors built by BBN under a DARPA contract handled the connections. The first attempt was meant to send the word 'login', but the system crashed after the letters 'lo'. " +
            "By 5 December 1969 four sites were connected.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.SYSTEM,
        section = "Real-time software",
        title = "Apollo Guidance Computer software",
        originalTitle = "Apollo Guidance Computer executive and waitlist",
        author = "MIT Instrumentation Laboratory",
        year = 1969,
        summary = "The real-time software of the Apollo Guidance Computer, with an executive for long jobs and a waitlist for short, time-critical tasks.",
        details = "During the descent of Apollo 11 on 20 July 1969, the computer raised 1202 'executive overflow' alarms. Flight controllers judged that the overloads would not endanger the landing, and the software recovered by restarting its jobs rather than aborting. " +
            "The waitlist ran brief time-critical tasks, each under about four milliseconds.",
        rare = true,
    ),
)
