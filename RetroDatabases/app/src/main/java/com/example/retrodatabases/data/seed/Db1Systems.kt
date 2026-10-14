package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Operating systems, environments and DBMSs of the 1980s: mass-market, research and almost forgotten. */
val db1Systems: List<RecordEntity> = listOf(

    // ---------- Operating systems ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "UNIX",
        originalTitle = "UNIX",
        author = "Ken Thompson, Dennis Ritchie (Bell Labs)",
        year = 1971,
        summary = "A multitasking OS from Bell Labs, written mostly in C; the ideological ancestor of Linux, BSD and macOS.",
        details = "The project began in 1969 on a PDP-7, and the first manual came out in 1971. In 1973 the kernel was rewritten in C. " +
            "Version 7 (1979) became the starting point for System V and BSD. Philosophy: small programs, text and pipelines.",
        example = """
            ls -l | grep '^d'   # directories only
        """,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "Multics",
        originalTitle = "Multics",
        author = "MIT, Bell Labs, General Electric",
        year = 1969,
        summary = "The ancestor of Unix: a multi-user OS with hierarchical directories and protection rings.",
        details = "A project of 1965–1969. The ideas of hierarchical directories and protection rings passed into Unix, " +
            "but the system itself was complex and slow.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "IBM OS/360",
        originalTitle = "OS/360",
        author = "IBM",
        year = 1966,
        summary = "The main OS of the System/360 family: a huge project, and the experience with it gave birth to Brooks's law.",
        details = "The first releases came in 1966. Fred Brooks led the development; the experience is described in “The Mythical Man-Month” (1975). " +
            "OS/360 set the standards of batch processing and the JCL job control language.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "OS ES (Unified System)",
        originalTitle = "OS ES EVM",
        author = "Developers of the ES EVM, USSR and CMEA countries",
        year = 1973,
        summary = "Operating systems of the Unified System of computers (ES EVM): Soviet machines with the IBM System/360 architecture.",
        details = "Programs for the ES EVM were created on the model of OS/360. In the 1970s and 1980s these systems ran " +
            "in the computing centres of research institutes and at enterprises.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "CP/M",
        originalTitle = "CP/M",
        author = "Gary Kildall, Digital Research",
        year = 1974,
        summary = "An OS for 8-bit machines with the Intel 8080 and Zilog Z80; before MS-DOS it was the de facto standard.",
        details = "Kildall created CP/M in 1974. It ran on the Altair, the Osborne 1 and hundreds of other machines. " +
            "WordStar and the first versions of dBase were written for CP/M, and its interface influenced MS-DOS.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "MS-DOS",
        originalTitle = "MS-DOS, PC DOS",
        author = "Microsoft",
        year = 1981,
        summary = "A single-tasking OS for the IBM PC with the FAT file system and a command line.",
        details = "Microsoft licensed 86-DOS and supplied it to IBM as PC DOS (1981). In the 80s MS-DOS set the standard for the PC: " +
            "640 KB of conventional memory, drivers, overlays and terminate-and-stay-resident programs (TSRs).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "VMS",
        originalTitle = "VMS (later OpenVMS)",
        author = "Digital Equipment Corporation",
        year = 1977,
        summary = "The OS of the VAX minicomputers: virtual memory, clusters and reliability.",
        details = "DEC created VMS for the VAX-11/780. Clusters of several machines with shared disks were considered a model of fault tolerance. " +
            "The descendant of the system, OpenVMS, still works today.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "OS-9",
        originalTitle = "OS-9",
        author = "Microware, Motorola",
        year = 1979,
        summary = "A multitasking UNIX-like OS for the 6809 and 68000 processors, used in industry and home electronics.",
        details = "OS-9 ran on industrial controllers and on the Tandy Color Computer home machines. " +
            "Its kernel was compact and suited to real-time systems.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "OS/2",
        originalTitle = "OS/2",
        author = "IBM, Microsoft",
        year = 1987,
        summary = "A multitasking OS for the PC with memory protection and the Presentation Manager graphical shell.",
        details = "OS/2 was conceived as the successor to DOS. It required a lot of memory and was slow to gain programs; " +
            "later it was displaced by Windows NT and Windows 95.",
    ),

    // ---------- Unix variants ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "BSD 4.2",
        originalTitle = "4.2BSD",
        author = "University of California, Berkeley",
        year = 1983,
        summary = "A Unix variant from Berkeley: sockets, the TCP/IP stack and a fast file system.",
        details = "At Berkeley they added network sockets and TCP/IP, and made the FFS file system fast. " +
            "FreeBSD and OpenBSD grew out of BSD, and BSD code went into macOS.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "SunOS",
        originalTitle = "SunOS",
        author = "Sun Microsystems",
        year = 1982,
        summary = "Unix for Sun workstations based on BSD; the NFS network file system appeared in 1984.",
        details = "Sun released SunOS in 1982. NFS made it possible to mount another machine's disks directly into the directory tree, " +
            "which was a novelty for the 80s.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "Xenix",
        originalTitle = "Xenix",
        author = "Microsoft",
        year = 1980,
        summary = "Microsoft's Unix variant for PCs and microcomputers, licensed from AT&T.",
        details = "Microsoft sold Xenix for the PC/XT and PC/AT. Unix gave multitasking and networking, " +
            "but MS-DOS remained simpler and cheaper in the 80s.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "Minix",
        originalTitle = "MINIX",
        author = "Andrew Tanenbaum",
        year = 1987,
        summary = "A teaching Unix-like clone for the IBM PC; it inspired Linus Torvalds to create Linux.",
        details = "Tanenbaum wrote Minix so that students could see how an OS works in its source code. " +
            "In 1991 Torvalds started Linux, inspired by this system.",
    ),

    // ---------- OS for home and graphics computers ----------

    entry(
        kind = Kind.SYSTEM,
        section = "OS for home and graphics computers",
        title = "Mac OS System 1",
        originalTitle = "System 1",
        author = "Apple",
        year = 1984,
        summary = "The graphical OS of the Macintosh: windows, menus, icons and a mouse.",
        details = "System 1 came out together with the Macintosh 128K. The interface drew on the experience of Xerox Star and Lisa " +
            "and became a model for the industry of the 80s. Programs used a common set of procedures, the Toolbox.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "OS for home and graphics computers",
        title = "AmigaOS",
        originalTitle = "AmigaOS (Exec, Intuition)",
        author = "Commodore",
        year = 1985,
        summary = "The multitasking OS of the Amiga: separate processes for sound, graphics and the interface.",
        details = "Exec is the kernel, Intuition is the graphical interface. Programs ran in parallel, while the chipset took over sound and graphics, " +
            "freeing the processor.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "OS for home and graphics computers",
        title = "TOS and GEM",
        originalTitle = "TOS, GEM",
        author = "Atari, Digital Research",
        year = 1985,
        summary = "The Atari ST system: TOS in ROM and the GEM graphical shell.",
        details = "TOS was written into ROM and started right after power-on. GEM is a windowing shell similar to the Macintosh. " +
            "The ST became popular with musicians thanks to its built-in MIDI ports.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "OS for home and graphics computers",
        title = "Windows 1.0",
        originalTitle = "Windows 1.0",
        author = "Microsoft",
        year = 1985,
        summary = "A graphical shell on top of MS-DOS: windows, a mouse and simple applications.",
        details = "The first versions of Windows (1985) were slow and barely used the processor's capabilities. " +
            "Mass success came with Windows 3.0 (1990).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "OS for home and graphics computers",
        title = "ProDOS",
        originalTitle = "ProDOS",
        author = "Apple",
        year = 1983,
        summary = "The Apple II OS with hierarchical directories and support for large disks.",
        details = "ProDOS replaced DOS 3.3 and supported subdirectories and high-capacity block devices. " +
            "For Apple II programmers it was a noticeable step forward.",
        rare = true,
    ),

    // ---------- Research environments ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Research environments",
        title = "Symbolics Lisp machines",
        originalTitle = "Symbolics Genera",
        author = "Symbolics, LMI",
        year = 1980,
        summary = "An environment in which the editor, the debugger and the system itself are written in Lisp.",
        details = "Symbolics and LMI Lisp machines were personal workstations for AI. The source code of the system could be read and changed " +
            "right on the machine. They cost tens of thousands of dollars.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Research environments",
        title = "Oberon System",
        originalTitle = "Oberon System",
        author = "Niklaus Wirth, Jürg Gutknecht (ETH Zurich)",
        year = 1987,
        summary = "Wirth's OS and editor: all the code is in Oberon, and any text can be a command.",
        details = "The system was created at ETH Zurich for the Ceres workstation. A mouse click on a word in the text runs a procedure, " +
            "so the document and the interface are one and the same.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Research environments",
        title = "Xerox Star",
        originalTitle = "Xerox 8010 Star",
        author = "Xerox",
        year = 1981,
        summary = "The first commercial system with windows, icons and a mouse.",
        details = "The Star sold for a high price and lost the competition to the IBM PC. " +
            "But its desktop, folders and icons became a model for Apple and Microsoft. The software was written in Mesa.",
        rare = true,
    ),

    // ---------- Database management systems ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Database management systems",
        title = "Oracle",
        originalTitle = "Oracle Version 2",
        author = "Oracle Corporation",
        year = 1979,
        summary = "One of the first commercial DBMSs with the SQL language (second version, 1979).",
        details = "The second version of Oracle came out in 1979 and became one of the first commercial implementations of SQL. " +
            "In the 80s the company quickly became a leader of the relational DBMS market.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Database management systems",
        title = "dBase",
        originalTitle = "dBase II",
        author = "Ashton-Tate",
        year = 1980,
        summary = "A personal DBMS with commands for .dbf tables: search, sorting, reports.",
        details = "dBase II first ran under CP/M, then under MS-DOS. In the 80s accounting and warehouse programs were often written in it. " +
            "The .dbf format outlived the program itself.",
    ),
)
