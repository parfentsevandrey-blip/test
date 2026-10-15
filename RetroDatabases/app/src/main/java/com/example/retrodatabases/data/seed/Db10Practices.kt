package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1940 to 1950: the working habits and teaching that shaped the first programmers, from the stored-program rule to the test for machine intelligence. */
val db10Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 10,
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Program and data in one store",
        originalTitle = "Stored-program concept, as set out in the EDVAC draft (1945)",
        author = "John von Neumann",
        year = 1945,
        summary = "Keep instructions and numbers in the same memory, so that a program can be loaded, changed and run like any other data.",
        details = "The rule became the basis of most later computers. The 1945 EDVAC draft set it out, and the Manchester Baby of 1948 and EDSAC of 1949 were built on it. It is a design principle, so no program accompanies it.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Moore School Lectures",
        originalTitle = "Theory and Techniques for Design of Electronic Digital Computers (1946)",
        author = "J. Presper Eckert, John Mauchly, John von Neumann and others",
        year = 1946,
        summary = "A summer course at the Moore School of the University of Pennsylvania taught the design of electronic digital computers to the engineers who would build them.",
        details = "The course ran from 8 July to the end of August 1946. Wikipedia gives 30 August as the last day and a Bonhams listing gives 31 August. Eckert gave eleven of the talks, and Mauchly and Goldstine six each. Its lectures spread the EDVAC design ideas to builders in America and Britain.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.PRACTICE,
        section = "Debugging",
        title = "The first logged bug, a moth in a relay",
        originalTitle = "Logbook entry, Harvard Mark II, 9 September 1947",
        author = "Harvard Mark II operating team",
        year = 1947,
        summary = "A moth jammed a relay in the Harvard Mark II, and the team taped it into the logbook with the note first actual case of bug being found.",
        details = "The entry is dated 9 September 1947, and the relay was number 70 on panel F. Sources disagree on who wrote the note and who removed the insect, so the team is named. Grace Hopper later told the story in lectures, which made it widely known.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.PRACTICE,
        section = "Testing machines",
        title = "The imitation game as a test of machines",
        originalTitle = "Computing Machinery and Intelligence (Turing, 1950)",
        author = "Alan Turing",
        year = 1950,
        summary = "Turing proposed judging a machine by whether a questioner can tell its written answers from those of a person.",
        details = "The idea appeared in Turing's 1950 paper Computing Machinery and Intelligence, which judges machines by their behaviour rather than by their design. It is a practice for testing, not a method of programming, so no program accompanies it.",
    ),

    entry(
        databaseId = 10,
        kind = Kind.PRACTICE,
        section = "Publications",
        title = "Annals of the Computation Laboratory",
        originalTitle = "Annals of the Computation Laboratory of Harvard University (1946)",
        author = "Howard Aiken and the Computation Laboratory staff",
        year = 1946,
        summary = "Harvard's computing group issued numbered annals that recorded how its machines were built, programmed and used.",
        details = "The annals began in 1946 and continued in volumes through the early 1950s. Their programming and operating notes are an early record of how the Mark machines were used day to day.",
        rare = true,
    ),
)
