package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 2000 to 2010: version control, build and continuous integration, analysis, IDEs and editors, frameworks, creative software, computing platforms. */
val db3Tools: List<RecordEntity> = listOf(

    // ---------- Version control ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Subversion",
        originalTitle = "Apache Subversion 1.0",
        author = "CollabNet (Karl Fogel, Ben Collins-Sussman, Brian Fitzpatrick)",
        year = 2004,
        summary = "A centralized version control system designed as a better CVS: atomic commits, renames and versioned directories.",
        details = "The project began in 2000 to replace CVS. Subversion 1.0 came out in February 2004. " +
            "Commits were atomic, renames kept each file's history, and directories were versioned too.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Git",
        originalTitle = "Git",
        author = "Linus Torvalds",
        year = 2005,
        summary = "A distributed version control system with content-addressed objects, cheap branches and fast local history.",
        details = "Torvalds wrote Git in April 2005, after the Linux kernel community lost free access to BitKeeper. " +
            "Every clone holds the full history, objects are named by the hash of their content, and branches are only pointers.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Mercurial",
        originalTitle = "Mercurial (hg)",
        author = "Matt Mackall",
        year = 2005,
        summary = "A distributed version control system written mostly in Python, with a small and regular command set.",
        details = "Mackall started Mercurial in April 2005, the same month as Git, after the Linux kernel community's BitKeeper dispute. " +
            "Most of it was written in Python with a small core in C, and its commands were designed to be easy to learn.",
        rare = true,
    ),

    // ---------- Build and continuous integration ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Build and continuous integration",
        title = "Apache Ant",
        originalTitle = "Ant 1.0",
        author = "James Duncan Davidson and the Apache Ant team",
        year = 2000,
        summary = "A build tool with XML files, written in Java, that replaced make for Java projects.",
        details = "Ant grew out of the build of the Tomcat server in 1998 and became an Apache project. " +
            "Its build.xml files described targets and dependencies, and its built-in tasks made Java builds portable across platforms.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Build and continuous integration",
        title = "Maven",
        originalTitle = "Apache Maven 1.0",
        author = "Jason van Zyl and the Apache Maven team",
        year = 2004,
        summary = "A build and dependency tool that downloads libraries from shared repositories and expects a standard project layout.",
        details = "Maven 1.0 appeared in 2004. Its project object model in pom.xml described the dependencies, plugins and the standard directory layout, " +
            "so projects were built the same way. Central repositories made libraries available with one line of configuration.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Build and continuous integration",
        title = "CruiseControl",
        originalTitle = "CruiseControl",
        author = "ThoughtWorks developers",
        year = 2001,
        summary = "A continuous build server that rebuilds and tests the project after every commit and publishes the result.",
        details = "ThoughtWorks released CruiseControl in 2001 as one of the first open-source continuous integration servers. " +
            "It watched the version control system, ran the build on each change, and published the results on a web page.",
        rare = true,
    ),

    // ---------- Testing and analysis ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Testing and analysis",
        title = "TestNG",
        originalTitle = "TestNG",
        author = "Cédric Beust",
        year = 2004,
        summary = "A Java testing framework with annotations, test groups and dependencies between tests.",
        details = "Beust released TestNG in 2004. Its annotations, parallel execution and test groups went beyond what JUnit 3 offered, " +
            "and many teams used it alongside JUnit.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Testing and analysis",
        title = "Selenium",
        originalTitle = "Selenium",
        author = "Jason Huggins and the ThoughtWorks team",
        year = 2004,
        summary = "A tool that drives a web browser from test code, so web applications can be tested the way users see them.",
        details = "Huggins began Selenium in 2004 at ThoughtWorks to test internal web applications. Its first version ran JavaScript inside the browser, " +
            "and later the WebDriver interface gave tests direct control of the browser.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Testing and analysis",
        title = "Checkstyle",
        originalTitle = "Checkstyle",
        author = "Oliver Burn",
        year = 2001,
        summary = "A Java tool that checks source code against a coding standard and reports the violations.",
        details = "Burn released Checkstyle in 2001 to enforce one coding standard across a team. Its rules were configured in XML, " +
            "and its reports could run as part of the build.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Testing and analysis",
        title = "FindBugs",
        originalTitle = "FindBugs",
        author = "William Pugh and David Hovemeyer",
        year = 2004,
        summary = "A static analyzer that looks for bug patterns in compiled Java classes, such as null pointer dereferences.",
        details = "Pugh and Hovemeyer started FindBugs at the University of Maryland in 2004. It analyzed compiled classes rather than source, " +
            "and it reported patterns that code reviews often missed.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Testing and analysis",
        title = "PMD",
        originalTitle = "PMD",
        author = "Tom Copeland and the PMD team",
        year = 2002,
        summary = "A source code analyzer with rules for unused variables, empty blocks and duplicated code.",
        details = "PMD began in 2002 as an open-source checker for Java sources. Its rule sets for common mistakes and its copy-paste detector " +
            "were widely adopted by teams that wanted cheap static analysis.",
        rare = true,
    ),

    // ---------- IDEs and editors ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "Eclipse 3.0",
        originalTitle = "Eclipse 3.0 (Rich Client Platform)",
        author = "IBM and the Eclipse Foundation",
        year = 2004,
        summary = "A Java IDE built on a plug-in platform that IBM gave to the community, with the Rich Client Platform.",
        details = "IBM donated the Eclipse platform in 2001, and version 3.0 (2004) introduced the Rich Client Platform, " +
            "which let other applications reuse the same plug-in system. Eclipse became the dominant Java IDE for much of the decade.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "IntelliJ IDEA",
        originalTitle = "IntelliJ IDEA 1.0",
        author = "JetBrains",
        year = 2001,
        summary = "A Java IDE with intelligent code completion and safe refactorings, for developers who paid for good tools.",
        details = "JetBrains released IntelliJ IDEA 1.0 in 2001. Its code completion understood types and reference chains, " +
            "and its refactorings changed code safely across a whole project. Developers paid for it while free editors were the norm.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "Visual Studio 2005",
        originalTitle = "Visual Studio 2005 (Whidbey)",
        author = "Microsoft",
        year = 2005,
        summary = "The IDE for .NET 2.0, with generics in C#, a new Windows Forms designer and database tools.",
        details = "Visual Studio 2005, codenamed Whidbey, came with .NET 2.0 and generics in C#. " +
            "Its designers and debugger for SQL Server and Windows Forms made Microsoft's tools the main choice for enterprise Windows developers.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "Xcode",
        originalTitle = "Xcode 1.0",
        author = "Apple Computer",
        year = 2003,
        summary = "Apple's IDE for Mac OS X, with the GNU compiler tools, Interface Builder and project templates.",
        details = "Xcode 1.0 (2003) combined the GNU compiler tools with Interface Builder for Cocoa windows. " +
            "Its project templates and build system replaced the older Project Builder.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "TextMate",
        originalTitle = "TextMate 1.0",
        author = "Allan Odgaard",
        year = 2004,
        summary = "A Mac text editor with bundles of language support, snippets and commands that let users script the editor.",
        details = "Odgaard released TextMate in 2004. Its bundles of language grammars and snippets made it quick to edit in many languages, " +
            "and users wrote small commands that changed how the editor behaved.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "IDEs and editors",
        title = "Notepad++",
        originalTitle = "Notepad++",
        author = "Don Ho",
        year = 2003,
        summary = "A Windows source editor built on the Scintilla component, with syntax highlighting and tabs.",
        details = "Ho began the editor in September 2003, and the first public build appeared on SourceForge in November 2003, for Windows only. " +
            "It stayed free, and its tabs and syntax highlighting made it a favourite of Windows programmers.",
        rare = true,
    ),

    // ---------- Frameworks and content ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Frameworks and content",
        title = "Ruby on Rails",
        originalTitle = "Rails 1.0",
        author = "David Heinemeier Hansson",
        year = 2004,
        summary = "A web framework in Ruby built on convention over configuration, with scaffolding and database migrations.",
        details = "Hansson extracted Rails from the Basecamp project and released it as open source in July 2004. " +
            "Its conventions and the active record pattern made database-backed web applications quick to start, and the framework spread through the second half of the decade.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Frameworks and content",
        title = "WordPress",
        originalTitle = "WordPress 0.7",
        author = "Matt Mullenweg and Mike Little",
        year = 2003,
        summary = "A blogging platform in PHP, forked from the b2 engine, that grew into one of the most used content systems on the Web.",
        details = "WordPress began in May 2003 as a fork of the b2 blogging engine. Its plugin system, themes and easy installation " +
            "made it the default blog software for a large part of the decade.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Frameworks and content",
        title = "Django",
        originalTitle = "Django 0.90",
        author = "Adrian Holovaty and Simon Willison at the Lawrence Journal-World",
        year = 2005,
        summary = "A Python web framework with an admin site, a model layer and fast development for content-heavy sites.",
        details = "Django grew at a newspaper in Lawrence, Kansas, and was released as open source in July 2005. " +
            "Its automatic admin interface and model layer made content sites quick to build.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Frameworks and content",
        title = "OpenOffice.org",
        originalTitle = "OpenOffice.org 1.0",
        author = "Sun Microsystems",
        year = 2002,
        summary = "A free office suite derived from StarOffice, with a word processor, spreadsheet and presentation program.",
        details = "Sun released OpenOffice.org 1.0 in May 2002 as the open-source base of StarOffice. " +
            "It read and wrote Microsoft Office formats, which made it useful to offices that could not buy a licence for every machine.",
    ),

    // ---------- Creative software ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Creative software",
        title = "Audacity",
        originalTitle = "Audacity 1.0",
        author = "Dominic Mazzoni and Roger Dannenberg at Carnegie Mellon University",
        year = 2000,
        summary = "A free audio editor for recording, cutting and filtering sound on Windows, Mac and Linux.",
        details = "Mazzoni and Dannenberg released Audacity in 2000 from Carnegie Mellon University. " +
            "Its waveform editing and effects were free, and it became the usual tool for podcasters later in the decade.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Creative software",
        title = "GIMP 2.0",
        originalTitle = "GNU Image Manipulation Program 2.0",
        author = "The GIMP team",
        year = 2004,
        summary = "The free image editor with a reworked user interface and better colour and text handling, released as version 2.0 in 2004.",
        details = "GIMP 2.0 (2004) introduced a reworked user interface and better colour and text handling. " +
            "Photographers and designers used it as a free alternative to commercial image editors.",
    ),

    // ---------- Computing platforms ----------

    entry(
        databaseId = 3,
        kind = Kind.TOOL,
        section = "Computing platforms",
        title = "CUDA",
        originalTitle = "CUDA 1.0",
        author = "NVIDIA",
        year = 2007,
        summary = "NVIDIA's C-like language and toolkit for running general programs on GeForce graphics processors.",
        details = "The first public CUDA SDK came out in February 2007, and version 1.0 followed in mid-2007. " +
            "Programmers wrote kernels in C with extensions, and the GPU ran them across hundreds of threads. It turned gaming graphics cards into compute devices.",
        rare = true,
    ),
)
