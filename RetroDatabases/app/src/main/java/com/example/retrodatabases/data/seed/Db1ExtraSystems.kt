package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of the 1980s that were missing from the first edition: operating systems, microkernels, windows, networks and DBMSs. */
val db1ExtraSystems: List<RecordEntity> = listOf(

    // ---------- Operating systems ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "UCSD p-System",
        originalTitle = "UCSD p-System",
        author = "University of California, San Diego",
        year = 1978,
        summary = "An OS and environment based on P-code (1978): one program ran on the Apple II, the IBM PC and many other machines.",
        details = "The system was written in Pascal and ran on a virtual P-machine. Portability was achieved by porting a single interpreter.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "CP/M-86",
        originalTitle = "CP/M-86",
        author = "Digital Research",
        year = 1981,
        summary = "The 16-bit version of CP/M (1981) from Digital Research for the Intel 8086 processor.",
        details = "CP/M-86 was intended as the successor to CP/M for 16-bit machines, but it lost to MS-DOS on the IBM-compatible PC market.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "TRS-DOS",
        originalTitle = "TRSDOS",
        author = "Tandy / Radio Shack",
        year = 1977,
        summary = "A disk OS for the TRS-80 (1977): a file system and commands for programs in BASIC and assembly.",
        details = "TRSDOS ran on the TRS-80 and on later Tandy models. For millions of users it was their first disk OS.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "Apple DOS 3.3",
        originalTitle = "Apple DOS 3.3",
        author = "Apple",
        year = 1980,
        summary = "The disk OS of the Apple II (1980): the standard for 80s programs on that machine.",
        details = "DOS 3.3 replaced DOS 3.2. It supported thousands of programs, and ProDOS appeared later (1983).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "DR DOS",
        originalTitle = "DR DOS",
        author = "Digital Research",
        year = 1988,
        summary = "A clone of MS-DOS from Digital Research (1988) with its own memory management and utilities.",
        details = "DR DOS offered memory management that was missing from early versions of MS-DOS. It was sold as an alternative to Microsoft.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "MSX-DOS",
        originalTitle = "MSX-DOS",
        author = "Microsoft, ASCII",
        year = 1984,
        summary = "An OS for MSX computers (1984), compatible with CP/M in its programming interface.",
        details = "MSX-DOS let programs written for CP/M run on MSX machines. It was a bridge between the CP/M world and home computers.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "ITS",
        originalTitle = "Incompatible Timesharing System",
        author = "MIT, Artificial Intelligence Laboratory",
        year = 1967,
        summary = "The MIT time-sharing system (1967–1980s): hacker culture and the place where Emacs appeared.",
        details = "ITS was written and used by MIT programmers. Richard Stallman worked with this system until he created GNU.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "TOPS-20",
        originalTitle = "TOPS-20",
        author = "Digital Equipment Corporation",
        year = 1976,
        summary = "DEC's OS for PDP-10 mainframes (1976): the basis of many university computing centres.",
        details = "TOPS-20 developed the ideas of time sharing. It was used in universities for teaching and research until the late 1980s.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "GS/OS",
        originalTitle = "GS/OS",
        author = "Apple",
        year = 1986,
        summary = "The operating system of the Apple IIGS (1986) with a directory hierarchy and multitasking.",
        details = "GS/OS replaced ProDOS on the 16-bit machine and supported long file names.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Operating systems",
        title = "Desqview",
        originalTitle = "DESQview",
        author = "Quarterdeck",
        year = 1985,
        summary = "A multitasking shell on top of MS-DOS (1985): several programs in windows on an ordinary PC.",
        details = "DESQview gave multitasking to DOS programs that could not do it themselves. Together with Windows it showed the demand for windows.",
        rare = true,
    ),

    // ---------- Unix variants ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "Unix System V",
        originalTitle = "UNIX System V",
        author = "AT&T",
        year = 1983,
        summary = "AT&T's commercial Unix (1983): the basis of a large family of Unix variants.",
        details = "System V became the reference for commercial Unix systems. Solaris, HP-UX and many others descend from it.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "AIX",
        originalTitle = "IBM AIX",
        author = "IBM",
        year = 1986,
        summary = "IBM's Unix (1986): the first version was for the RT PC workstations.",
        details = "AIX first appeared on IBM RT PC workstations, and later ran on IBM's large systems.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "A/UX",
        originalTitle = "Apple A/UX",
        author = "Apple",
        year = 1988,
        summary = "Unix for the Macintosh II (1988): Unix and Mac programs could run on one machine.",
        details = "A/UX was Apple's first attempt to offer a full Unix. Sales were modest, but the idea lived on in Mac OS X.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Unix variants",
        title = "Ultrix",
        originalTitle = "DEC Ultrix",
        author = "Digital Equipment Corporation",
        year = 1984,
        summary = "DEC's Unix for its machines (1984): versions for the VAX and for MIPS workstations.",
        details = "Ultrix was installed in universities and research centres where DEC machines were in use.",
        rare = true,
    ),

    // ---------- Microkernels and distributed systems ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Microkernels and distributed systems",
        title = "Mach",
        originalTitle = "Mach",
        author = "Carnegie Mellon University",
        year = 1985,
        summary = "The Mach microkernel (1985): the basis of NeXTSTEP and later macOS.",
        details = "Mach moved file systems and networking out of the kernel into user-level servers. This idea lives on in the modern XNU kernel.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Microkernels and distributed systems",
        title = "NeXTSTEP",
        originalTitle = "NeXTSTEP",
        author = "NeXT",
        year = 1989,
        summary = "The NeXT system (1989) built on Mach and BSD, with Objective-C and a graphical interface.",
        details = "NeXTSTEP became the basis for the future macOS. Its object-oriented interface was built on Objective-C.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Microkernels and distributed systems",
        title = "QNX",
        originalTitle = "QNX",
        author = "Quantum Software Systems",
        year = 1982,
        summary = "A microkernel real-time OS (1982) for industry and medicine.",
        details = "QNX passed messages between processes, which gave predictable delays. The system is used in cars and medical equipment.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Microkernels and distributed systems",
        title = "Amoeba",
        originalTitle = "Amoeba",
        author = "Andrew Tanenbaum and colleagues, Vrije Universiteit",
        year = 1981,
        summary = "A distributed OS (1981): many machines look like one system with shared files.",
        details = "Amoeba was created as a teaching and research project on distributed systems. Its ideas influenced later work.",
        rare = true,
    ),

    // ---------- Graphics and networks ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "X Window System",
        originalTitle = "X Window System (X10, X11)",
        author = "MIT, Project Athena",
        year = 1984,
        summary = "A windowing system for Unix (1984; X11 in 1987): windows are not tied to the hardware and work over the network.",
        details = "X separated the server, which draws, from the client, which decides what to draw. This scheme is still used in Linux.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "NeWS",
        originalTitle = "NeWS",
        author = "Sun Microsystems",
        year = 1986,
        summary = "Sun's windowing system (1986) built on PostScript: a program described the windows and the graphics.",
        details = "NeWS was powerful but too heavy for the hardware of the 1980s. Its ideas influenced later graphical systems.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "Usenet",
        originalTitle = "Usenet",
        author = "Tom Truscott and Jim Ellis, Duke University",
        year = 1980,
        summary = "A news-exchange system over the Unix network (1980): the ancestor of modern forums and mailing lists.",
        details = "Messages were passed between machines over uucp. In the 80s Usenet became one of the main places to discuss programming.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "FidoNet",
        originalTitle = "FidoNet",
        author = "Tom Jennings",
        year = 1984,
        summary = "A BBS network (1984) that linked thousands of home PCs through nightly modem calls.",
        details = "Messages were passed from node to node on a schedule. FidoNet ran over ordinary telephone lines and linked users around the world.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "TCP/IP",
        originalTitle = "TCP/IP",
        author = "Vint Cerf, Bob Kahn and colleagues",
        year = 1983,
        summary = "The TCP/IP protocol stack (1983): ARPANET switched to it on 1 January 1983.",
        details = "The protocols separated reliable transport (TCP) from packet addressing (IP). This scheme became the basis of the Internet.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Graphics and networks",
        title = "Ethernet",
        originalTitle = "Ethernet (DIX)",
        author = "Xerox PARC, DEC, Intel",
        year = 1980,
        summary = "The Ethernet specification (1980): a local network that connected workstations and PCs.",
        details = "The first DIX specification appeared in 1980. Coaxial cable, and later twisted pair, made the network cheap.",
    ),

    // ---------- Database management systems ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Database management systems",
        title = "IBM DB2",
        originalTitle = "IBM DB2",
        author = "IBM",
        year = 1983,
        summary = "IBM's relational DBMS (1983) for mainframes with the SQL language: a benchmark for commercial databases.",
        details = "DB2 came out for MVS and later received versions for other platforms. Many banks and insurers kept their data in it for decades.",
    ),
)
