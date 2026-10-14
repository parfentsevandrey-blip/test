package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Methods, design practices, standards, security and community of 2000 to 2010. */
val db3Practices: List<RecordEntity> = listOf(

    // ---------- Process and agility ----------

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Agile Manifesto",
        originalTitle = "Manifesto for Agile Software Development",
        author = "Seventeen developers who met at Snowbird, Utah",
        year = 2001,
        summary = "Four values and twelve principles for lighter, customer-focused development, written at a ski resort in 2001.",
        details = "Seventeen developers met at Snowbird, Utah, in February 2001 and wrote four values and twelve principles for lighter, customer-focused development. " +
            "The manifesto gave teams a shared vocabulary and became the reference point for later methods.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Test-driven development",
        originalTitle = "Test-Driven Development: By Example",
        author = "Kent Beck",
        year = 2002,
        summary = "Write a failing test first, make it pass with the simplest code, then clean up the design.",
        details = "Kent Beck described the practice in his 2002 book Test-Driven Development: By Example. " +
            "The cycle of red, green and refactor kept the code covered by tests as it grew, and many teams adopted it as a daily habit.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Continuous integration",
        originalTitle = "Continuous Integration",
        author = "Martin Fowler and Matthew Foemmel",
        year = 2006,
        summary = "Merge and build the whole project several times a day, with an automated test run after each build.",
        details = "Fowler and Foemmel described continuous integration in a 2006 article. Changes were merged into the mainline often, " +
            "and each merge triggered a build and a test run, so breakage was found within minutes.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Lean software development",
        originalTitle = "Lean Software Development: An Agile Toolkit",
        author = "Mary and Tom Poppendieck",
        year = 2003,
        summary = "Lean ideas from manufacturing applied to software: remove waste, amplify learning, decide late and deliver fast.",
        details = "The Poppendiecks' book Lean Software Development (2003) adapted the ideas of lean manufacturing to software. " +
            "Its seven principles included eliminating waste, building quality in and optimising the whole.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Kanban for software",
        originalTitle = "Kanban",
        author = "David Anderson",
        year = 2010,
        summary = "A method for software teams that limits work in progress and makes the flow of tasks visible on a board.",
        details = "Anderson's 2010 book brought the Kanban method, borrowed from lean production, into software. " +
            "Teams drew their workflow on a board, set limits on work in progress and measured how long each task took to flow through.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Process and agility",
        title = "Behaviour-driven development",
        originalTitle = "Behaviour-Driven Development",
        author = "Dan North",
        year = 2006,
        summary = "Tests written as examples of behaviour, in a language the business can read.",
        details = "North presented behaviour-driven development in a 2006 article. Its tests were phrased as scenarios with a given, a when and a then, " +
            "so that business people could read them and agree on what the software should do.",
        rare = true,
    ),

    // ---------- Design and architecture ----------

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Domain-driven design",
        originalTitle = "Domain-Driven Design: Tackling Complexity in the Heart of Software",
        author = "Eric Evans",
        year = 2003,
        summary = "Put the business model at the centre of the software, with one shared language for developers and domain experts.",
        details = "Evans's book Domain-Driven Design (2003) argued that software should follow the model of the business. " +
            "Its ubiquitous language, bounded contexts and aggregates gave teams a vocabulary for complex domains.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Aspect-oriented programming",
        originalTitle = "AspectJ",
        author = "Gregor Kiczales and the PARC team",
        year = 2001,
        summary = "Concerns such as logging and security are written in separate modules and woven into the code.",
        details = "The AspectJ project, released in 2001, let programmers keep crosscutting concerns such as logging, transactions and security in aspects. " +
            "A weaver inserted them into the compiled code at the places where they were declared.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Model-driven architecture",
        originalTitle = "Model Driven Architecture (MDA)",
        author = "Object Management Group",
        year = 2001,
        summary = "Build software from platform-independent models, with tools that transform the models into code for a chosen platform.",
        details = "The OMG's Model Driven Architecture (2001) separated the business model from the platform. " +
            "Models written in UML were transformed by tools into code, so the same model could target different technologies.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "MapReduce",
        originalTitle = "MapReduce",
        author = "Jeffrey Dean and Sanjay Ghemawat at Google",
        year = 2004,
        summary = "A programming model for large data sets: map emits key-value pairs, and reduce combines the values for each key.",
        details = "Dean and Ghemawat described MapReduce in a 2004 paper. Users wrote two functions, and the runtime handled distribution, " +
            "failures and the shuffle between machines. Open-source systems such as Hadoop reproduced the model.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Web 2.0",
        originalTitle = "Web 2.0",
        author = "Tim O'Reilly and MediaLive International",
        year = 2004,
        summary = "A style built on user contributions, open interfaces and rich pages, named at a conference in 2004.",
        details = "The first Web 2.0 conference, held in October 2004, gave the name to a style of sites built on user contributions, " +
            "open data and rich interfaces. The term was debated for years, but it described a real shift in how websites were built.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Design and architecture",
        title = "Ajax",
        originalTitle = "Ajax (asynchronous JavaScript and XML)",
        author = "Jesse James Garrett",
        year = 2005,
        summary = "The technique of asynchronous requests with XMLHttpRequest, which update parts of a page without a reload.",
        details = "Garrett's 2005 essay gave the name Ajax to a technique that browsers had supported for years. " +
            "Scripts sent requests in the background and changed parts of the page, which made web applications feel like desktop programs.",
    ),

    // ---------- Standards and governance ----------

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Standards and governance",
        title = "CMMI",
        originalTitle = "Capability Maturity Model Integration, version 1.1",
        author = "Software Engineering Institute",
        year = 2002,
        summary = "The Capability Maturity Model Integration, version 1.1 (2002), merged several SEI models into one framework.",
        details = "CMMI version 1.1 (2002) merged the separate SEI models for software, systems and development into one framework. " +
            "It offered staged and continuous representations, and organisations were appraised against its process areas.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Standards and governance",
        title = "ITIL version 2",
        originalTitle = "IT Infrastructure Library",
        author = "Office of Government Commerce (UK)",
        year = 2001,
        summary = "The IT Infrastructure Library: processes for service support and service delivery, for managing IT as a service.",
        details = "The second version of the core ITIL books appeared between 2000 and 2001. It described processes for incidents, changes and service levels, " +
            "and it became the standard vocabulary for managing IT operations.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Standards and governance",
        title = "SWEBOK",
        originalTitle = "Guide to the Software Engineering Body of Knowledge",
        author = "IEEE Computer Society and ACM",
        year = 2004,
        summary = "The Guide to the Software Engineering Body of Knowledge, which listed ten knowledge areas for the profession.",
        details = "The 2004 edition of SWEBOK, published by the IEEE Computer Society and the ACM, described ten knowledge areas, from requirements to professional practice. " +
            "It gave courses and certifications a common reference.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Standards and governance",
        title = "GNU General Public License version 3",
        originalTitle = "GNU GPL version 3",
        author = "Free Software Foundation (Richard Stallman and Eben Moglen)",
        year = 2007,
        summary = "A licence revision that added patent terms and rules against installation locks, drafted in public.",
        details = "GPLv3 was published in June 2007 after a long public drafting process. It added patent terms, " +
            "rules against installation locks that block modified software, and compatibility with other licences.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Standards and governance",
        title = "Creative Commons licences",
        originalTitle = "Creative Commons",
        author = "Lawrence Lessig and the Creative Commons team",
        year = 2002,
        summary = "A set of licences that let authors share work with clear permissions, first published in December 2002.",
        details = "Creative Commons launched its licences in December 2002. Authors chose from a few combinations of attribution, sharing and commercial rights, " +
            "and the marks let readers see the terms at a glance.",
    ),

    // ---------- Security and quality ----------

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Security and quality",
        title = "OWASP Top 10",
        originalTitle = "OWASP Top Ten",
        author = "The Open Web Application Security Project",
        year = 2003,
        summary = "A list of the most critical web application risks, first published in 2003 with injection and cross-site scripting high on it.",
        details = "OWASP published its first top-ten list in 2003. It named injection, broken authentication and cross-site scripting as the risks to fix first, " +
            "and it became the common reference for web security reviews.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Security and quality",
        title = "Microsoft Security Development Lifecycle",
        originalTitle = "The Security Development Lifecycle",
        author = "Michael Howard and Steve Lipner",
        year = 2006,
        summary = "Security practices built into each stage of development at Microsoft, from requirements to release.",
        details = "Howard and Lipner's book The Security Development Lifecycle (2006) described how Microsoft built security into requirements, design, testing and release. " +
            "Its practices, such as threat modelling and fuzz testing, spread to many other companies.",
        rare = true,
    ),

    // ---------- Web and community ----------

    entry(
        databaseId = 3,
        kind = Kind.PRACTICE,
        section = "Web and community",
        title = "Stack Overflow",
        originalTitle = "Stack Overflow",
        author = "Jeff Atwood and Joel Spolsky",
        year = 2008,
        summary = "Questions and answers for programmers, with voting that moved the best answers to the top.",
        details = "Stack Overflow launched in beta in September 2008. Its voting and reputation system pushed the best answers to the top, " +
            "and it became the main place where programmers searched for help.",
    ),
)
