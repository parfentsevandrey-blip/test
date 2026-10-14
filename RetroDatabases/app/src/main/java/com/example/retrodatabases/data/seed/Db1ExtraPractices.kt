package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of the 1980s that were missing from the first edition: design, formal methods, standards and culture. */
val db1ExtraPractices: List<RecordEntity> = listOf(

    // ---------- Design ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Abstract data types",
        originalTitle = "Abstract data types",
        author = "Barbara Liskov, Stephen Zilles",
        year = 1974,
        summary = "A type is described by a set of operations rather than by its data layout; the internal representation is hidden.",
        details = "Liskov and Zilles (1974) proposed describing types through behaviour. This idea became the basis of OOP and of modern libraries.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Liskov substitution principle",
        originalTitle = "Liskov substitution principle",
        author = "Barbara Liskov",
        year = 1987,
        summary = "A subclass must work wherever its base class works, without breaking the program's expectations.",
        details = "The principle was formulated in 1987. It explains when inheritance is safe, and it underlies the design of interfaces.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Design patterns",
        originalTitle = "Design patterns",
        author = "Kent Beck, Ward Cunningham",
        year = 1987,
        summary = "Reusable solutions to typical problems in object-oriented programs: the seed of the future Gang of Four book.",
        details = "Beck and Cunningham presented patterns at OOPSLA 1987 in the context of Smalltalk. The ideas were later collected in a 1994 book.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "CRC cards",
        originalTitle = "CRC cards (Class-Responsibility-Collaboration)",
        author = "Kent Beck, Ward Cunningham",
        year = 1989,
        summary = "Designing classes on index cards: the class, its responsibilities and its collaborators.",
        details = "The cards let a group design objects without a computer. The method is handy for teaching object-oriented thinking.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Jackson's method (JSP)",
        originalTitle = "Jackson structured programming",
        author = "Michael Jackson",
        year = 1975,
        summary = "A program's structure is derived from the structure of its input and output data.",
        details = "Jackson (1975) proposed building a program from the hierarchy of its data. The method was widely used in Britain for business systems.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Warnier–Orr diagrams",
        originalTitle = "Warnier–Orr diagrams",
        author = "Jean-Dominique Warnier, Kenneth Orr",
        year = 1974,
        summary = "Nested brackets describe the structure of data and of a program hierarchically: from the general to the specific.",
        details = "Warnier proposed the diagrams in 1974, and Orr developed them in 1977. The diagrams were used to design reports and files.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "HIPO diagrams",
        originalTitle = "HIPO (Hierarchy plus Input-Process-Output)",
        author = "IBM",
        year = 1974,
        summary = "A hierarchy of modules with a description of the inputs, processing and outputs of each part of the program.",
        details = "HIPO was created at IBM to document large systems. Each block described its input, process and output separately.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Jackson System Development",
        originalTitle = "JSD (Jackson System Development)",
        author = "Michael Jackson",
        year = 1983,
        summary = "A method of modelling a system by its events, with the life cycle of each object.",
        details = "JSD (1983) continued the ideas of JSP: first the world and its events are modelled, then the program. " +
            "The method was used in banking systems.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Information Engineering",
        originalTitle = "Information Engineering",
        author = "Clive Finkelstein, James Martin",
        year = 1981,
        summary = "A methodology (1981) for designing a company's whole information system from its data and processes.",
        details = "The methodology combined data and process models into one picture. It was used for large corporate systems.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Rapid application development",
        originalTitle = "Rapid Application Development (RAD)",
        author = "James Martin",
        year = 1982,
        summary = "Development in short iterations with prototypes and tools that speeded up building screens.",
        details = "James Martin described the approach in the book “Application Development Without Programmers” (1982). " +
            "The idea: a quick mock-up, user feedback and reuse of components.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Booch method",
        originalTitle = "Booch method (object-oriented design)",
        author = "Grady Booch",
        year = 1986,
        summary = "Object-oriented design (1986): class and object diagrams for large systems.",
        details = "Booch described the method in a 1986 book. His diagrams became the basis of the UML notations that appeared later.",
        rare = true,
    ),

    // ---------- Quality and testing ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Z notation",
        originalTitle = "Z notation",
        author = "Jean-Raymond Abrial, Oxford",
        year = 1980,
        summary = "A formal specification (1980) in mathematical notation: requirements are written as predicates.",
        details = "The Z notation was used in industrial specifications, including for security systems. It combines set theory and first-order logic.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "VDM method",
        originalTitle = "Vienna Development Method (VDM)",
        author = "Dines Bjørner, Cliff Jones",
        year = 1978,
        summary = "A formal specification method (1978) from Vienna: data models and operations with preconditions.",
        details = "VDM combined specification and correctness proofs through refinement steps. The method was used in industrial projects.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Mutation testing",
        originalTitle = "Mutation testing",
        author = "Richard DeMillo, Richard Lipton, Frederick Sayward",
        year = 1978,
        summary = "Tests check themselves: the program gets artificial bugs, and the tests must catch them.",
        details = "The idea of 1978: if the tests let the introduced mutations through, they are weak. " +
            "The method is expensive, but it reveals gaps in coverage.",
        rare = true,
    ),

    // ---------- Processes and standards ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "IEEE 829 and 830 standards",
        originalTitle = "IEEE 829 (1983), IEEE 830 (1984)",
        author = "IEEE",
        year = 1983,
        summary = "Standards for test documentation (829, 1983) and software requirements (830, 1984).",
        details = "The standards defined the content of the documents: the test plan, test cases, reports and the requirements specification. " +
            "They are still used in contracts.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "DOD-STD-2167A standard",
        originalTitle = "DOD-STD-2167A",
        author = "US Department of Defense",
        year = 1988,
        summary = "A software development standard (1988) for military projects: strict documents and milestones.",
        details = "2167A replaced 2167 (1985). It described the life cycle, documents and reviews for military software.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "CMM maturity model",
        originalTitle = "Capability Maturity Model (CMM)",
        author = "Software Engineering Institute (SEI)",
        year = 1987,
        summary = "Five levels of maturity of development processes (SEI, 1987): from chaos to measured processes.",
        details = "The first SEI questionnaires appeared in 1987. The full CMM model was published in 1991. It became a yardstick for contractors.",
        rare = true,
    ),

    // ---------- Culture and openness ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Hacker ethic",
        originalTitle = "Hacker ethic",
        author = "Steven Levy (the book “Hackers”, 1984)",
        year = 1984,
        summary = "Hacker principles: access to computers should be free and information should be open.",
        details = "Levy described the culture of MIT and of the first computer enthusiasts. These principles underlay free software.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Demoscene",
        originalTitle = "Demoscene",
        author = "Programmer groups on the Amiga, C64 and PC",
        year = 1985,
        summary = "A programming competition (1985 and later): demos of 64 KB or less, with graphics and music.",
        details = "Demo groups created effects at the limit of the hardware. The scene grew out of groups that cracked games.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Electronic bulletin boards",
        originalTitle = "Bulletin Board System (BBS)",
        author = "Ward Christensen, Randy Suess (CBBS, 1978)",
        year = 1978,
        summary = "The first BBSs (1978): a computer with a modem that users dialled into to read messages and files.",
        details = "CBBS in Chicago is considered the first BBS. File exchange, forums and online communities of the 80s grew around BBSs.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Copy protection",
        originalTitle = "Copy protection",
        author = "Developers of games and programs of the 1980s",
        year = 1980,
        summary = "Methods of protecting floppy disks from copying (1980s) and the crackers' answer: cracks and intros inserted into programs.",
        details = "The protection relied on features of the floppy's tracks and on key checks. " +
            "Crackers released unprotected programs with the screens of their groups.",
        rare = true,
    ),
)
