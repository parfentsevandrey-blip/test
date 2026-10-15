package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1960 to 1970: how programmers shared resources, named their field, reused parts and published their standards. */
val db7Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.PRACTICE,
        section = "Concurrency",
        title = "Cooperating processes and semaphores",
        originalTitle = "Cooperating sequential processes (EWD 123, 1965)",
        author = "Edsger Dijkstra",
        year = 1965,
        summary = "Dijkstra's scheme for processes that share a resource: a counter guarded by two operations, P and V, which wait for a signal and send one.",
        details = "Dijkstra wrote about cooperating sequential processes in 1965, and his semaphores became the standard way to keep processes from using a shared resource at the same time. " +
            "The dining philosophers problem, which he posed in the same period, shows why the order in which locks are taken matters.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.PRACTICE,
        section = "Process",
        title = "Software engineering",
        originalTitle = "NATO Conference on Software Engineering, Garmisch, 1968",
        author = "Peter Naur, Brian Randell and the conference members",
        year = 1968,
        summary = "A term that the NATO conference at Garmisch in October 1968 made public, where engineers and computer scientists asked how to build large software reliably.",
        details = "The conference report was published in 1969. Bertrand Meyer argues that the phrase was in use before Garmisch, so the origin of the term is disputed. " +
            "The conference is still treated as the start of the field.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.PRACTICE,
        section = "Reuse",
        title = "Software components",
        originalTitle = "Mass produced software components (1968)",
        author = "Douglas McIlroy",
        year = 1968,
        summary = "McIlroy's proposal that software should be assembled from tested components, in the way that electronics is assembled from standard parts, presented at the Garmisch conference.",
        details = "McIlroy argued that families of components, each in several variants, would make software cheaper to build. The idea continued in libraries, packages and later component systems.",
    ),

    entry(
        databaseId = 7,
        kind = Kind.PRACTICE,
        section = "Standards",
        title = "Request for Comments",
        originalTitle = "RFC 1, 'Host Software' (7 April 1969)",
        author = "Steve Crocker and the Network Working Group",
        year = 1969,
        summary = "The open series of memos that began with RFC 1 in April 1969, and that still records the standards of the Internet.",
        details = "Crocker wrote the first memo because he saw valuable know-how going unrecorded, and he chose the name Request for Comments to sound less assertive. The memos went by post at first. Crocker edited the series until early 1970, and Jon Postel then took over.",
        rare = true,
    ),
)
