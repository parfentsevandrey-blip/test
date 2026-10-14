package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Methods, design practices, testing, estimation and culture of the 1990s software world. */
val db2Practices: List<RecordEntity> = listOf(

    // ---------- Process and methods ----------

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Extreme Programming (XP)",
        originalTitle = "Extreme Programming",
        author = "Kent Beck and Ward Cunningham, with the Chrysler C3 team",
        year = 1996,
        summary = "Short iterations, pair programming, test-first coding and a customer on site, pushed to the extreme.",
        details = "Beck and Cunningham developed the practices during the Chrysler C3 payroll project in 1996. " +
            "The book Extreme Programming Explained (1999) gave the method its name and a list of practices. " +
            "Teams wrote tests before code, integrated many times a day and changed the design whenever it got in the way.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Scrum",
        originalTitle = "Scrum",
        author = "Ken Schwaber and Jeff Sutherland",
        year = 1995,
        summary = "A framework with sprints, a daily stand-up and a product backlog, presented at OOPSLA 1995.",
        details = "Schwaber and Sutherland described the process at the OOPSLA 1995 conference, based on how product teams had worked with short iterations. " +
            "The sprint and the daily meeting became its trademarks, and the backlog kept the list of wanted features.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Rational Unified Process",
        originalTitle = "RUP",
        author = "Rational Software (Philippe Kruchten and others)",
        year = 1998,
        summary = "An iterative, phased process with disciplines such as requirements and testing, sold as a product.",
        details = "RUP turned the Rational methods into a documented process with roles, artefacts and templates. " +
            "Its size scared smaller teams, so many of them adapted it rather than adopting it whole.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Feature-Driven Development",
        originalTitle = "FDD",
        author = "Jeff De Luca and Peter Coad",
        year = 1997,
        summary = "A process built around small features that the client values, each delivered by a feature team.",
        details = "FDD grew in Singapore in 1997 out of a large banking project. " +
            "Its steps were an overall model, a list of features, planning by feature, and design and build by feature.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "DSDM (Dynamic Systems Development Method)",
        originalTitle = "DSDM",
        author = "DSDM Consortium (UK)",
        year = 1994,
        summary = "A UK framework built on fixed time-boxes and the MoSCoW priority list.",
        details = "The consortium published its method in 1994. Its rules included fixed deadlines, " +
            "prioritised requirements (must, should, could, won't) and close involvement of the users.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Personal Software Process",
        originalTitle = "PSP",
        author = "Watts Humphrey",
        year = 1995,
        summary = "A personal discipline: log every minute and every defect, and plan each program before writing it.",
        details = "Humphrey's book A Discipline for Software Engineering (1995) describes PSP. " +
            "Engineers recorded time and defects for each program, and they used the data to improve their estimates.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Capability Maturity Model",
        originalTitle = "CMM v1.1",
        author = "Software Engineering Institute (Watts Humphrey and others)",
        year = 1993,
        summary = "Five levels of process maturity, from ad hoc to optimising, used to rate software contractors.",
        details = "The SEI published version 1.1 of the model in 1993. Organisations were assessed on key process areas, " +
            "and government buyers often asked for a certain level. Critics said the model encouraged paperwork.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Process and methods",
        title = "Daily build",
        originalTitle = "Daily build and synchronise-and-stabilise",
        author = "Microsoft product teams",
        year = 1995,
        summary = "Building the whole product every night, so that breakage is found the same day.",
        details = "Cusumano and Selby's book Microsoft Secrets (1995) described the daily build and the synchronise-and-stabilise routine at Microsoft. " +
            "It was a forerunner of continuous integration.",
    ),

    // ---------- Design and architecture ----------

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Design Patterns",
        originalTitle = "Design Patterns: Elements of Reusable Object-Oriented Software",
        author = "Erich Gamma, Richard Helm, Ralph Johnson and John Vlissides",
        year = 1994,
        summary = "Twenty-three named solutions to recurring design problems, with a shared vocabulary.",
        details = "The book (1994) described patterns such as Observer, Strategy and Abstract Factory. Its authors became known as the Gang of Four. " +
            "It gave designers a shared vocabulary, although many developers used the names without reading the book.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "UML 1.0 and 1.1",
        originalTitle = "Unified Modeling Language",
        author = "Grady Booch, James Rumbaugh and Ivar Jacobson, submitted to the OMG",
        year = 1997,
        summary = "A standard set of diagrams for classes, use cases, sequences and states, adopted by the OMG in 1997.",
        details = "The three methods of Booch, Rumbaugh and Jacobson were merged into UML. " +
            "Version 1.1 was adopted as an OMG standard in 1997, and CASE tools began to read and draw the same diagrams.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Refactoring",
        originalTitle = "Refactoring: Improving the Design of Existing Code",
        author = "Martin Fowler, with Kent Beck",
        year = 1999,
        summary = "Changing the structure of code in small steps while keeping its behaviour, guided by named code smells.",
        details = "Fowler's book (1999) catalogued refactorings such as extract method and rename, each with its mechanics. " +
            "Every step was small and followed by a test run.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "AntiPatterns",
        originalTitle = "AntiPatterns: Refactoring Software, Architectures, and Projects in Crisis",
        author = "William Brown, Raphael Malveau, Hays McCormick and Thomas Mowbray",
        year = 1998,
        summary = "Named bad habits such as the God Class and Spaghetti Code, with ways to escape them.",
        details = "The book (1998) recorded common mistakes in object-oriented and architectural work and gave a refactored solution for each. " +
            "Its names entered the everyday speech of developers.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Software Architecture (Shaw and Garlan)",
        originalTitle = "Software Architecture: Perspectives on an Emerging Discipline",
        author = "Mary Shaw and David Garlan",
        year = 1996,
        summary = "A vocabulary of architectural styles: pipes and filters, layers, client-server and repositories.",
        details = "The 1996 book named the styles that teams already used and separated architecture from implementation. " +
            "It made architecture a subject of study in its own right.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Pattern-Oriented Software Architecture",
        originalTitle = "POSA, Volume 1",
        author = "Frank Buschmann, Regine Meunier, Hans Rohnert, Peter Sommerlad and Michael Stal",
        year = 1996,
        summary = "Patterns for large systems, from Layers and Pipes and Filters to Broker and Reactor.",
        details = "Volume 1 (1996) described patterns at the scale of whole systems. " +
            "It took the pattern idea of the Gang of Four and applied it to architecture, which made it useful to system designers as well as programmers.",
        rare = true,
    ),

    // ---------- Testing ----------

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Testing",
        title = "JUnit",
        originalTitle = "JUnit 3.x",
        author = "Kent Beck and Erich Gamma",
        year = 1997,
        summary = "A small Java framework for unit tests: write test methods, run them on every build, keep them green.",
        details = "Beck and Gamma wrote JUnit in 1997, reportedly during a flight. It used reflection to find the test methods, " +
            "and the green bar became a habit for many teams. The idea spread to other languages as the xUnit family.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Testing",
        title = "Fuzz testing",
        originalTitle = "Fuzz",
        author = "Barton Miller, Lars Fredriksen and Bryan So",
        year = 1990,
        summary = "Feeding random input to programs to see which ones crash; many standard UNIX tools failed.",
        details = "The 1990 paper An Empirical Study of the Reliability of UNIX Utilities showed that many standard tools crashed on random input. " +
            "The method, later called fuzzing, became a standard way to find security bugs.",
        rare = true,
    ),

    // ---------- Estimation and reviews ----------

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Estimation and reviews",
        title = "Software inspections",
        originalTitle = "Software Inspection",
        author = "Michael Fagan (1976 method); Tom Gilb and Dorothy Graham (1993 book)",
        year = 1993,
        summary = "A structured review meeting with roles, checklists and defect counts, held before testing starts.",
        details = "Fagan formalised inspections at IBM in 1976. Gilb and Graham's book Software Inspection (1993) made the method common in the 90s, " +
            "with checklists and measured defect rates.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Estimation and reviews",
        title = "The CHAOS Report",
        originalTitle = "The CHAOS Report",
        author = "The Standish Group",
        year = 1994,
        summary = "A survey of thousands of IT projects: only about one in six finished on time and on budget.",
        details = "The 1994 report examined more than eight thousand projects. Its figures showed that about 16 percent finished " +
            "on time, on budget and with the planned features, and about a third were cancelled.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Estimation and reviews",
        title = "COCOMO II",
        originalTitle = "COCOMO II model",
        author = "Barry Boehm and the USC Center for Software Engineering",
        year = 1995,
        summary = "A cost model that estimates effort in person-months from the size of the code and a set of cost drivers.",
        details = "COCOMO II was developed in the 1990s as an update of the 1981 COCOMO model. " +
            "It added drivers for reuse, process maturity and modern tools, and it is still used to estimate projects.",
        rare = true,
    ),

    // ---------- Community and culture ----------

    entry(
        databaseId = 2,
        kind = Kind.PRACTICE,
        section = "Community and culture",
        title = "The Cathedral and the Bazaar",
        originalTitle = "The Cathedral and the Bazaar",
        author = "Eric S. Raymond",
        year = 1997,
        summary = "An essay comparing closed and open development, which explained why Linux grew the way it did.",
        details = "Raymond's essay (1997) described the development of Linux as a bazaar: many developers released early and often, and users found the bugs. " +
            "It became a manifesto of the open-source movement.",
    ),
)
