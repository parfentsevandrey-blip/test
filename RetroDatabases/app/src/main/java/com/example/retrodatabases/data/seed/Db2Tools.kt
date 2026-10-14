package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of the 1990s: version control, compilers and IDEs, editors, documentation, music trackers, internet and archivers. */
val db2Tools: List<RecordEntity> = listOf(

    // ---------- Version control ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Version control",
        title = "CVS (Concurrent Versions System)",
        originalTitle = "CVS",
        author = "Brian Berliner (C version)",
        year = 1990,
        summary = "Many people edit one project, and a central server keeps the history and merges their changes.",
        details = "CVS grew from shell scripts built on top of RCS. Brian Berliner's C version appeared around 1990, " +
            "and CVS let several developers work on the same files at once. It became the standard open-source version control of the decade.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Perforce",
        originalTitle = "Perforce",
        author = "Christopher Seiwald (Perforce Software)",
        year = 1995,
        summary = "A commercial version control system with a central server, built for very large repositories.",
        details = "Seiwald's system appeared in 1995. Game studios and chip makers used it for huge repositories, " +
            "where its central server design suited big teams.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Visual SourceSafe",
        originalTitle = "Microsoft Visual SourceSafe",
        author = "Microsoft (bought from One Tree Software)",
        year = 1994,
        summary = "Microsoft's file-based source control: easy to set up, and widely criticised for database corruption.",
        details = "Microsoft bought the product in 1994 and bundled it with its development tools. " +
            "Its shared-file design was easy to install, but users reported database corruption so often that it became a warning story about version control.",
        rare = true,
    ),

    // ---------- Compilers and IDEs ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "Borland C++ 4.5",
        originalTitle = "Borland C++ 4.5",
        author = "Borland International",
        year = 1995,
        summary = "An IDE with a fast compiler and the ObjectWindows library for Windows programming.",
        details = "Borland C++ 4.5 (1995) combined a fast compiler, an editor and a debugger in one IDE. " +
            "The ObjectWindows Library (OWL) wrapped the Windows API in C++ classes, and Borland's compilers were popular with professional Windows developers.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "Visual C++ 2.0",
        originalTitle = "Microsoft Visual C++ 2.0",
        author = "Microsoft",
        year = 1994,
        summary = "Microsoft's C++ IDE with the Microsoft Foundation Classes for 32-bit Windows applications.",
        details = "Visual C++ 2.0 (1994) brought 32-bit Windows programming and the Microsoft Foundation Classes into the Microsoft IDE. " +
            "Its wizards generated the skeleton of a program in a few clicks.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "Turbo Pascal 7 IDE",
        originalTitle = "Turbo Pascal 7.0",
        author = "Borland (Anders Hejlsberg)",
        year = 1992,
        summary = "A DOS integrated environment: editor, compiler and debugger in one fast program.",
        details = "Turbo Pascal 7.0 (1992) ran the editor, compiler and debugger inside one screen. " +
            "Compiles took a fraction of a second, and students used it for years. Hejlsberg's design set the pattern for the integrated environments that followed.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "NetBeans",
        originalTitle = "Xelfi, then NetBeans",
        author = "Students of Charles University in Prague",
        year = 1996,
        summary = "A Java IDE that began as a student project in Prague, first aimed at a Delphi-style environment for Java.",
        details = "The project started in 1996 as Xelfi, a student effort at Charles University in Prague. " +
            "A company was founded in 1997 to develop it commercially, and Sun Microsystems bought it in 1999. Later it was given to the Apache Software Foundation.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "Cygwin",
        originalTitle = "Cygwin",
        author = "Cygnus Solutions",
        year = 1995,
        summary = "A Unix-like environment on Windows: a POSIX emulation layer with the GNU tools built on top.",
        details = "Cygnus Solutions began Cygwin in 1995 so that Unix programs could be built on Windows NT. " +
            "Its DLL emulated a POSIX system, and the GNU tools ran on top of it.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "MinGW",
        originalTitle = "Minimalist GNU for Windows",
        author = "Colin Peters and contributors",
        year = 1998,
        summary = "Native Windows programs built with GCC, without a POSIX layer or a runtime DLL.",
        details = "MinGW started in 1998 as a way to build native Windows executables with GCC and the Windows headers. " +
            "Unlike Cygwin, it needed no runtime DLL, so the programs could be distributed freely.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Compilers and IDEs",
        title = "NASM",
        originalTitle = "Netwide Assembler",
        author = "Simon Tatham and Julian Hall",
        year = 1996,
        summary = "An x86 assembler that writes many object formats, including DOS, Win32 and ELF.",
        details = "The Netwide Assembler began in 1996. One source file could target DOS, Windows or Linux, " +
            "because the assembler wrote several object formats.",
        rare = true,
    ),

    // ---------- Editors ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Editors",
        title = "Vim",
        originalTitle = "Vi IMproved",
        author = "Bram Moolenaar",
        year = 1991,
        summary = "A vi-compatible editor with syntax colouring, undo history and scripting.",
        details = "Moolenaar began Vim in 1988 for the Amiga and released it to the public in 1991. " +
            "It kept the vi keys and added scripting, undo history and syntax colouring, so programmers carried their editing habits from machine to machine.",
    ),

    // ---------- Documentation ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Documentation",
        title = "Doxygen",
        originalTitle = "Doxygen",
        author = "Dimitri van Heesch",
        year = 1997,
        summary = "Generates documentation from annotated C and C++ source, with graphs of classes and calls.",
        details = "Van Heesch released Doxygen in 1997. Comments written in a special syntax became HTML, LaTeX and man pages, " +
            "and the tool drew class diagrams and call graphs from the code.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Documentation",
        title = "LyX",
        originalTitle = "LyX",
        author = "Matthias Ettrich",
        year = 1995,
        summary = "A word processor that writes LaTeX underneath: you edit the structure and the result is typeset.",
        details = "Ettrich started LyX in 1995. The editor shows the document with its structure, and it saves LaTeX code that a typesetter turns into a page. " +
            "Academic writers used it for papers and theses.",
        rare = true,
    ),

    // ---------- Music and graphics ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Music and graphics",
        title = "Winamp",
        originalTitle = "Winamp",
        author = "Justin Frankel at Nullsoft",
        year = 1997,
        summary = "A small MP3 player with skins and plugins, which set the look of desktop audio in the late 90s.",
        details = "Nullsoft released Winamp in 1997. The player was small and fast, and skins let users change its look. " +
            "Its plugin system made it the default player for many PC users.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Music and graphics",
        title = "Impulse Tracker",
        originalTitle = "Impulse Tracker (IT)",
        author = "Jeffrey 'Pulse' Lim",
        year = 1995,
        summary = "A DOS music tracker with a better sample engine and its own module format, used in the demoscene.",
        details = "Lim wrote Impulse Tracker in assembly language for DOS PCs and released it in 1995 as freeware with paid extras. " +
            "Scream Tracker was its model, but it added a better sample engine and the IT module format. Its last patch came in 1999.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Music and graphics",
        title = "FastTracker 2",
        originalTitle = "FastTracker II",
        author = "Triton (Fredrik Huss and Magnus Högdahl)",
        year = 1994,
        summary = "A PC music tracker with instruments, envelopes and more channels; its XM format spread through the music scene.",
        details = "FastTracker 2 added instruments with volume and pitch envelopes, and more channels than its predecessors. " +
            "Its XM module format spread through the demoscene and the wider music scene.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Music and graphics",
        title = "Blender",
        originalTitle = "Blender",
        author = "Ton Roosendaal at NeoGeo",
        year = 1994,
        summary = "A 3D suite that started as an in-house tool at the Dutch animation studio NeoGeo.",
        details = "Blender began in 1994 at NeoGeo, a Dutch animation studio, as an internal tool for its productions. " +
            "It became open source in 2002.",
        rare = true,
    ),

    // ---------- Internet ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Internet",
        title = "mIRC",
        originalTitle = "mIRC",
        author = "Khaled Mardam-Bey",
        year = 1995,
        summary = "An IRC client for Windows with a scripting language, popular in chat rooms across the Internet.",
        details = "mIRC appeared in 1995 and gave Windows users a usable IRC client. " +
            "Its scripting language let users build bots and automated chat rooms, and much of the early Windows chat culture ran through it.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Internet",
        title = "ICQ",
        originalTitle = "I Seek You",
        author = "Mirabilis (an Israeli start-up)",
        year = 1996,
        summary = "An instant messenger that gave each user a number and showed who was online.",
        details = "ICQ (1996, a pun on the words I seek you) gave each user a number, the UIN, and a list of contacts whose online status it showed. " +
            "Its success led AOL to buy Mirabilis in 1998.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Internet",
        title = "Mosaic",
        originalTitle = "NCSA Mosaic",
        author = "Marc Andreessen and Eric Bina at NCSA",
        year = 1993,
        summary = "The first popular graphical web browser, which showed images inside pages and took the Web to a wider audience.",
        details = "Mosaic (1993) ran on X Window, Windows and the Macintosh. " +
            "Andreessen and Bina left NCSA to found Netscape in 1994.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Internet",
        title = "Trumpet Winsock",
        originalTitle = "Trumpet Winsock",
        author = "Peter Tattam",
        year = 1993,
        summary = "A shareware TCP/IP stack that gave Windows 3.1 PCs their first Internet connection.",
        details = "Trumpet Winsock let Windows 3.1 users dial in and run TCP/IP programs. " +
            "Its author distributed it as shareware, and it was the first Internet connection for many home users.",
        rare = true,
    ),

    // ---------- Archivers ----------

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Archivers",
        title = "RAR",
        originalTitle = "RAR archive",
        author = "Eugene Roshal",
        year = 1993,
        summary = "A proprietary archiver with strong compression and recovery records that could repair damaged archives.",
        details = "Roshal released RAR in 1993. Its recovery records could repair a damaged archive, " +
            "and it became common on bulletin boards.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Archivers",
        title = "zlib",
        originalTitle = "zlib",
        author = "Jean-loup Gailly and Mark Adler",
        year = 1995,
        summary = "The compression library behind gzip, PNG and many other formats.",
        details = "zlib appeared in 1995 as a free implementation of the deflate algorithm. " +
            "Programs and file formats across the Internet used it, including gzip archives and PNG images.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.TOOL,
        section = "Archivers",
        title = "bzip2",
        originalTitle = "bzip2",
        author = "Julian Seward",
        year = 1996,
        summary = "A block-sorting compressor that packed text tighter than gzip.",
        details = "Seward released bzip2 in 1996. It sorts the input in blocks with the Burrows-Wheeler transform, " +
            "which made text files smaller than gzip could manage.",
        rare = true,
    ),
)
