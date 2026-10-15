package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1970 to 1980: how programmers designed modules, defended against faults, wrote small tools and built shared worlds. */
val db8Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Structured design",
        originalTitle = "Structured design (Stevens, Myers and Constantine, 1974)",
        author = "Wayne Stevens, Glenford Myers and Larry Constantine",
        year = 1974,
        summary = "A method for turning data-flow diagrams into hierarchies of modules, judged by the coupling between modules and the cohesion within them.",
        details = "The method was published in the IBM Systems Journal in 1974. Low coupling and high cohesion became the standard words for a module that can be changed without breaking its neighbours.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.PRACTICE,
        section = "Fault tolerance",
        title = "N-version programming",
        originalTitle = "N-version programming (Chen and Avižienis, 1977)",
        author = "Liming Chen and Algirdas Avižienis",
        year = 1977,
        summary = "Several independently written versions of a program run on the same input, and a voter picks the answer that most of them agree on, as a defence against software faults.",
        details = "The idea was published in 1977 and developed in 1978. Its premise, that independently written versions rarely share the same faults, was questioned later in theoretical work, so the method helps without being a guarantee.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.PRACTICE,
        section = "Programming style",
        title = "Software Tools",
        originalTitle = "Software Tools (Kernighan and Plauger, 1976)",
        author = "Brian Kernighan and P. J. Plauger",
        year = 1976,
        summary = "The book's idea: write small programs that do one job well, and build larger programs by connecting them through text streams.",
        details = "Kernighan and Plauger taught the method with Ratfor programs that copied the Unix tools. A later edition moved the examples to Pascal, and the idea of reusable filters outlived both languages.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.PRACTICE,
        section = "Online communities",
        title = "Multi-user dungeons",
        originalTitle = "MUD1, the multi-user dungeon at Essex University",
        author = "Roy Trubshaw and Richard Bartle",
        year = 1978,
        summary = "A shared text world in which several people explored rooms, fought and talked at the same time, starting with MUD1 at Essex University in 1978.",
        details = "The first version was written in 1978 by two students, and it connected players over the university's network. Its rules of rooms, objects and commands became the model for later online games and chat systems.",
        rare = true,
    ),
)
