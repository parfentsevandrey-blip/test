package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Operating systems, databases and servers of the 1990s: Unix variants, Windows, desktop systems and research systems. */
val db2Systems: List<RecordEntity> = listOf(

    // ---------- Unix family ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "Linux 1.0",
        originalTitle = "Linux kernel 1.0",
        author = "Linus Torvalds and contributors",
        year = 1994,
        summary = "The free Unix-like kernel that Torvalds began in 1991, released as version 1.0 in 1994.",
        details = "Torvalds started the kernel in 1991 as a hobby on an Intel 386 PC. " +
            "Version 1.0 (March 1994) brought networking, TCP/IP and drivers for many devices. " +
            "Distributions such as Slackware and Debian put it on home computers, and developers from many countries sent patches over the Internet.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "FreeBSD 2.0",
        originalTitle = "FreeBSD 2.0",
        author = "The FreeBSD Project",
        year = 1994,
        summary = "A free BSD Unix for PCs, with the 386 and 486 as its main targets.",
        details = "FreeBSD 2.0 was announced on 22 November 1994. It grew from the 386BSD patch kit and ran on ordinary PCs. " +
            "Its ports collection and package tools made it a favourite of server administrators and enthusiasts, and the 2.x line lasted for years.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "NetBSD 1.0",
        originalTitle = "NetBSD 1.0",
        author = "The NetBSD Project",
        year = 1994,
        summary = "A portable BSD that aimed to run on almost every machine with a processor and some memory.",
        details = "NetBSD 1.0 appeared in October 1994, descended from 4.4BSD-Lite. " +
            "Its goal was portability: the same system ran on Amigas, Macintoshes, Suns and many other machines. " +
            "The slogan \"Of course it runs NetBSD\" became a joke in the Unix community.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "OpenBSD 1.0",
        originalTitle = "OpenBSD 1.0",
        author = "Theo de Raadt and the OpenBSD team",
        year = 1996,
        summary = "A BSD forked from NetBSD in 1995, with security as the first priority and audits of its code.",
        details = "OpenBSD 1.0 was released in October 1996. De Raadt split the project from NetBSD after a dispute with its core team. " +
            "Security became the trademark: secure defaults, code audits and fast fixes. The project's mascot is a pufferfish.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "Solaris 2.0",
        originalTitle = "Solaris 2.0 (SunOS 5)",
        author = "Sun Microsystems",
        year = 1992,
        summary = "Sun's Unix with a System V base and symmetric multiprocessing, the standard Unix of Sun servers.",
        details = "Solaris 2.0 (1992) replaced SunOS 4 with a kernel based on System V Release 4. " +
            "Its symmetric multiprocessing support let one machine use several processors, and it became the usual Unix on Sun servers through the decade.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Unix family",
        title = "IRIX 5",
        originalTitle = "IRIX 5.x",
        author = "Silicon Graphics Inc.",
        year = 1993,
        summary = "SGI's Unix for MIPS workstations, built around the graphics hardware and OpenGL.",
        details = "IRIX 5 (1993) ran on the MIPS-based workstations of Silicon Graphics. " +
            "Its graphics stack was built around OpenGL, which SGI had developed, and some film effects of the period were produced on SGI machines.",
        rare = true,
    ),

    // ---------- Windows and DOS ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Windows and DOS",
        title = "Windows NT 3.1",
        originalTitle = "Windows NT 3.1",
        author = "Microsoft, team led by David Cutler",
        year = 1993,
        summary = "A 32-bit Windows with protected memory and a portable kernel, for workstations and servers.",
        details = "NT 3.1 shipped in July 1993 with the Win32 API, a Windows-style desktop and the NTFS file system. " +
            "David Cutler, who had worked on DEC's VMS, led the design. The system ran on Intel processors and on other architectures, and its security model was part of the design from the start.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Windows and DOS",
        title = "Windows 95",
        originalTitle = "Windows 95",
        author = "Microsoft",
        year = 1995,
        summary = "The consumer Windows that added the Start button, the taskbar and long file names, and ran most DOS programs.",
        details = "Windows 95 arrived in August 1995 with the Start menu, a taskbar, long file names and Plug and Play support. " +
            "It was still built on the DOS-based Windows 9x kernel, so it ran most DOS games and programs. " +
            "Its launch was a media event, and the 32-bit API became the standard for new programs.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Windows and DOS",
        title = "Windows CE 1.0",
        originalTitle = "Windows CE 1.0",
        author = "Microsoft",
        year = 1996,
        summary = "A small Windows for handheld PCs and embedded devices, with a reduced version of the Win32 API.",
        details = "Windows CE 1.0 was released in November 1996 for handheld PC hardware. " +
            "It was a separate code base from desktop Windows, with a subset of the Win32 API, so programs had to be ported to it. " +
            "The first machines were palmtop-style computers with small keyboards.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Windows and DOS",
        title = "ReactOS",
        originalTitle = "ReactOS (begun as FreeWin95)",
        author = "The ReactOS project",
        year = 1996,
        summary = "An open-source attempt to write a Windows-compatible system from scratch, begun in the 90s as FreeWin95.",
        details = "The project began in 1996 as FreeWin95, an attempt to build a free clone of Windows 95. " +
            "Its goal is binary compatibility with Windows programs, so that the same applications run without changes. " +
            "That goal is much harder than it sounds, and the project's history shows why.",
        rare = true,
    ),

    // ---------- Desktop and workstations ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Desktop and workstations",
        title = "OS/2 Warp",
        originalTitle = "OS/2 Warp 3",
        author = "IBM",
        year = 1994,
        summary = "IBM's 32-bit successor to DOS and Windows 3.1, with multitasking and the Workplace Shell desktop.",
        details = "OS/2 Warp 3 (1994) came with the object-oriented Workplace Shell and could run Windows 3.1 programs. " +
            "Banks and large companies adopted it, but drivers and games were scarce, and Windows 95 took most of the market.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Desktop and workstations",
        title = "BeOS R3",
        originalTitle = "BeOS Release 3",
        author = "Be Inc. (Jean-Louis Gassée)",
        year = 1996,
        summary = "A multimedia operating system with threads everywhere and a file system that stores attributes.",
        details = "Be Inc. wrote BeOS for its own BeBox hardware and then for PCs. " +
            "Every window and driver ran in threads, and the BFS file system kept attributes next to each file, so the system could search files by those attributes.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Desktop and workstations",
        title = "AmigaOS 3.5",
        originalTitle = "AmigaOS 3.5",
        author = "Commodore-Amiga",
        year = 1994,
        summary = "The last major Commodore release of the Amiga system: a multitasking desktop with preemptive threads.",
        details = "Commodore released AmigaOS 3.5 in 1994, when the company was already in financial trouble. " +
            "The Workbench desktop and the Exec kernel with preemptive multitasking had been ahead of many PCs. " +
            "Later versions came from other companies.",
        rare = true,
    ),

    // ---------- Research systems ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Research systems",
        title = "Plan 9 from Bell Labs",
        originalTitle = "Plan 9 from Bell Labs",
        author = "Rob Pike, Ken Thompson, Dave Presotto, Phil Winterbottom and others at Bell Labs",
        year = 1992,
        summary = "A research system in which everything is a file and each process builds its own namespace.",
        details = "Plan 9 came out of Bell Labs in 1992. Every resource, including network connections and the screen, appears as a file, " +
            "and each process can mount other machines' files into its own view. " +
            "In 1992 Ken Thompson and Rob Pike designed UTF-8 for the system, and that encoding later became the standard on the Internet.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Research systems",
        title = "Inferno",
        originalTitle = "Inferno",
        author = "Bell Labs, the Plan 9 group",
        year = 1996,
        summary = "A network operating system from the Plan 9 team, with a portable virtual machine and the Limbo language.",
        details = "Inferno (1996) took the Plan 9 ideas to a portable system. Programs run on a virtual machine, so the same binaries work on many hosts. " +
            "Its programming language was Limbo, and its protocol family, derived from 9P, let machines share resources as files.",
        rare = true,
    ),

    // ---------- Databases ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Databases",
        title = "Oracle 7",
        originalTitle = "Oracle7",
        author = "Oracle Corporation",
        year = 1992,
        summary = "A relational database with stored procedures, triggers and distributed queries across several servers.",
        details = "Oracle 7 (1992) added stored procedures, triggers and distributed queries across servers. " +
            "It became the database of choice for large corporations, and the company sold its PL/SQL language along with it.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Databases",
        title = "Postgres95 (PostgreSQL)",
        originalTitle = "Postgres95",
        author = "Andrew Yu and Jolly Chen at Berkeley",
        year = 1995,
        summary = "An open-source relational database from the Postgres research project at Berkeley, with SQL added in 1995.",
        details = "The Postgres research project began at Berkeley under Michael Stonebraker in the 1980s. " +
            "In 1995 Andrew Yu and Jolly Chen added an SQL interface and released the result as Postgres95. " +
            "It was renamed PostgreSQL in 1996.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Databases",
        title = "MySQL",
        originalTitle = "MySQL",
        author = "Michael Widenius, David Axmark and Allan Larsson (MySQL AB)",
        year = 1995,
        summary = "A fast, simple SQL database that became the usual choice behind PHP websites.",
        details = "Widenius and Axmark began MySQL in the mid-90s to build a fast SQL server for their own data, and MySQL AB was founded in 1995. " +
            "Its simple design and quick reads made it the usual database behind PHP sites, and it was the M of the LAMP stack that web hosts used.",
    ),

    // ---------- Networks and web servers ----------

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Networks and web servers",
        title = "NetWare 4",
        originalTitle = "NetWare 4.0",
        author = "Novell Inc.",
        year = 1993,
        summary = "The file and print server that ruled office networks, with a global directory service called NDS.",
        details = "NetWare 4 (1993) introduced Novell Directory Services, a single tree of users, servers and printers for the whole company " +
            "instead of one user list per server. Before the Web took over the office, most companies shared files and printers through NetWare servers.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.SYSTEM,
        section = "Networks and web servers",
        title = "Apache 1.0",
        originalTitle = "Apache HTTP Server 1.0",
        author = "The Apache Group (volunteers)",
        year = 1995,
        summary = "A web server built from patches to the NCSA httpd code, which soon became the most used server on the Internet.",
        details = "Apache 1.0 was released in December 1995 by a group of volunteers who had patched the NCSA server. " +
            "Its modules and plain-text configuration made it flexible, and it soon became the most common web server on the Internet.",
    ),
)
