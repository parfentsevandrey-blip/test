package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 2000 to 2010: Windows, Mac OS X, phone systems, Linux and open systems, databases and storage, virtualization. */
val db3Systems: List<RecordEntity> = listOf(

    // ---------- Desktop and consumer ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Desktop and consumer",
        title = "Windows 2000",
        originalTitle = "Windows 2000 (NT 5.0)",
        author = "Microsoft",
        year = 2000,
        summary = "Windows built on the NT kernel, with Active Directory and Plug and Play for business desktops and servers.",
        details = "Windows 2000 shipped in February 2000 on the NT kernel. It was the first Windows with Active Directory, a directory service for users and computers, " +
            "and it brought Plug and Play to business machines. Companies that had waited for stability moved their desktops to it.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Desktop and consumer",
        title = "Windows XP",
        originalTitle = "Windows XP (NT 5.1)",
        author = "Microsoft",
        year = 2001,
        summary = "The NT-based Windows that joined the consumer and business lines, with a new Start menu and a built-in firewall after SP2.",
        details = "Windows XP (October 2001) united the consumer Windows 9x line with the NT kernel. Its new Start menu and default visual style defined " +
            "a generation of desktops. The built-in firewall arrived with Service Pack 2 in 2004, and it changed how home users thought about security.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Desktop and consumer",
        title = "Windows Vista",
        originalTitle = "Windows Vista (NT 6.0)",
        author = "Microsoft",
        year = 2007,
        summary = "A redesigned Windows with the Aero interface, User Account Control and a new driver model.",
        details = "Windows Vista (January 2007) introduced the Aero interface, User Account Control and a new driver model. " +
            "Its hardware demands and the frequent UAC prompts were hard to live with, and many users kept Windows XP for years.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Desktop and consumer",
        title = "Mac OS X 10.0 Cheetah",
        originalTitle = "Mac OS X 10.0",
        author = "Apple Computer",
        year = 2001,
        summary = "Apple's Unix-based system with the Aqua interface, built on the Mach kernel and BSD tools.",
        details = "Mac OS X 10.0 (March 2001) replaced the classic Mac OS with a system built on the Mach kernel and BSD tools, " +
            "with the Aqua interface on top. The first release was slow, but its Unix terminal and developer tools won over programmers.",
    ),

    // ---------- Phones and handhelds ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Phones and handhelds",
        title = "iPhone OS 1.0",
        originalTitle = "iPhone OS 1.0",
        author = "Apple Inc.",
        year = 2007,
        summary = "The first system of the iPhone, with multi-touch and a full Safari browser, and no third-party apps until 2008.",
        details = "The iPhone launched in June 2007 with a multi-touch screen and a complete Safari browser. Third-party applications were not allowed " +
            "until the App Store opened in 2008, so web pages were the only programs most owners ran.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Phones and handhelds",
        title = "Android 1.0",
        originalTitle = "Android 1.0",
        author = "Google and the Open Handset Alliance",
        year = 2008,
        summary = "Google's Linux-based system for phones, first shipped on the T-Mobile G1 with the Dalvik virtual machine.",
        details = "Android 1.0 came out in 2008 on the T-Mobile G1, the first phone to run it. Applications ran on the Dalvik virtual machine, " +
            "and the Open Handset Alliance of manufacturers and operators backed the platform.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Phones and handhelds",
        title = "Windows Mobile 5.0",
        originalTitle = "Windows Mobile 5.0 (Pocket PC)",
        author = "Microsoft",
        year = 2005,
        summary = "Microsoft's pocket Windows for smartphones and PDAs, with the .NET Compact Framework and a new shell.",
        details = "Windows Mobile 5.0 (2005) gave pocket and smartphone devices the .NET Compact Framework, which let managed programs run on handhelds. " +
            "Its shell and mail program were rebuilt, and the platform stayed the business choice for handhelds until the iPhone.",
        rare = true,
    ),

    // ---------- Linux and BSD ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Linux and BSD",
        title = "Linux 2.6",
        originalTitle = "Linux kernel 2.6.0",
        author = "Linus Torvalds and the kernel developers",
        year = 2003,
        summary = "The 2.6 kernel, released in December 2003, with the O(1) scheduler and a preemptible kernel for desktops.",
        details = "Linux 2.6.0 (December 2003) brought the O(1) scheduler, a new block layer and preemption that made desktops responsive. " +
            "Its short development cycles and many maintainers became the pattern for the rest of the decade.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Linux and BSD",
        title = "Ubuntu 4.10",
        originalTitle = "Ubuntu 4.10 (Warty Warthog)",
        author = "Canonical and the Ubuntu community",
        year = 2004,
        summary = "Canonical's first Ubuntu release, with GNOME, a simple installer and a new release every six months.",
        details = "Canonical, founded by Mark Shuttleworth, released Ubuntu 4.10 in October 2004 on a Debian base. " +
            "It shipped GNOME and a simple installer, and it set the pattern of a predictable release every six months.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Linux and BSD",
        title = "Gentoo Linux",
        originalTitle = "Gentoo Linux 1.0",
        author = "Daniel Robbins and the Gentoo community",
        year = 2002,
        summary = "A source-based distribution with Portage, where each package is compiled for the machine it runs on.",
        details = "Version 1.0 of Gentoo Linux came out on 31 March 2002. Its package manager, Portage, built every program from source " +
            "with the options chosen by the user. People who wanted fine control over their systems liked it, and it grew a large community of documentation writers.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Linux and BSD",
        title = "Knoppix",
        originalTitle = "KNOPPIX",
        author = "Klaus Knopper",
        year = 2000,
        summary = "A Debian-based Linux that boots from a CD and runs without installing anything on the disk.",
        details = "Knoppix debuted in September 2000. It booted from a CD, detected the hardware and ran a full desktop from the disc, " +
            "which made it useful for demonstrations, rescue work and classrooms. Its automatic hardware detection set a standard for later live systems.",
        rare = true,
    ),

    // ---------- Open systems and research ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Open systems and research",
        title = "Solaris 10",
        originalTitle = "Solaris 10 (SunOS 5.10)",
        author = "Sun Microsystems",
        year = 2005,
        summary = "Sun's Unix with DTrace for live tracing and Solaris Containers for isolating applications on one machine.",
        details = "Solaris 10 (January 2005) introduced DTrace, a tool for tracing a running system without restarting it, " +
            "and Solaris Containers, which isolate applications on one machine. It was a strong commercial Unix of its time.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Open systems and research",
        title = "OpenSolaris",
        originalTitle = "OpenSolaris",
        author = "Sun Microsystems and the OpenSolaris community",
        year = 2005,
        summary = "Sun's open-source release of the Solaris code, which brought DTrace and ZFS to a community project.",
        details = "Sun launched OpenSolaris in June 2005 as an open-source project built from the Solaris code. " +
            "Its community brought DTrace and the ZFS file system to developers outside the company, and it tested features that later reached commercial releases.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Open systems and research",
        title = "Haiku",
        originalTitle = "OpenBeOS, later Haiku",
        author = "The Haiku project",
        year = 2001,
        summary = "An open-source system that began in 2001 as OpenBeOS to recreate BeOS, and was renamed Haiku in 2004.",
        details = "The effort began in August 2001 as OpenBeOS, to recreate the BeOS desktop as open-source software. " +
            "Palm's ownership of the BeOS name brought a trademark warning, and the project was renamed Haiku in 2004. Its first alpha release came in 2009.",
        rare = true,
    ),

    // ---------- Databases and storage ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Databases and storage",
        title = "PostgreSQL 8.0",
        originalTitle = "PostgreSQL 8.0",
        author = "The PostgreSQL Global Development Group",
        year = 2005,
        summary = "The first PostgreSQL release with a native Windows port, tablespaces and savepoints.",
        details = "PostgreSQL 8.0 (January 2005) ran natively on Windows instead of through a Unix layer. " +
            "It added tablespaces, which put tables on chosen disks, and savepoints, which allow partial rollbacks inside a transaction.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Databases and storage",
        title = "SQLite 3.0",
        originalTitle = "SQLite 3",
        author = "D. Richard Hipp",
        year = 2004,
        summary = "A file format change in version 3 made SQLite a stable embedded database for phones, browsers and applications.",
        details = "SQLite 3.0 (2004) gave the database a stable file format that later versions would keep reading, which made it safe to ship inside applications. " +
            "Browsers, phones and desktop programs embedded it for local storage, and it became one of the most widely deployed database engines.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Databases and storage",
        title = "Google File System",
        originalTitle = "GFS (Google File System)",
        author = "Sanjay Ghemawat, Howard Gobioff and Shun-Tak Leung at Google",
        year = 2003,
        summary = "A distributed file system for large files on ordinary machines: 64 MB chunks, three replicas and one master for metadata.",
        details = "The Google File System was described at SOSP in 2003. Files were split into 64 MB chunks, each kept on three machines, " +
            "and a single master held the metadata. The design accepted that machines fail often, and it relied on replication to keep data safe.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Databases and storage",
        title = "Cassandra",
        originalTitle = "Apache Cassandra",
        author = "Avinash Lakshman and Prashant Malik at Facebook",
        year = 2008,
        summary = "A distributed database created at Facebook for inbox search and released as open source in 2008, with no single master.",
        details = "Facebook built Cassandra for its inbox search and released it as open source in 2008. Data is spread around a ring of nodes, " +
            "and each node can answer a request, so no single master can fail. Its tunable consistency let applications choose how strict each read should be.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Databases and storage",
        title = "MongoDB 1.0",
        originalTitle = "MongoDB",
        author = "10gen",
        year = 2009,
        summary = "A document database that stores JSON-like records and queries them without a fixed schema, first released in February 2009.",
        details = "10gen released MongoDB in February 2009. Documents are stored as binary JSON, so records in one collection can differ in shape. " +
            "Developers liked it for fast prototypes, and it became one of the best-known NoSQL databases.",
    ),

    // ---------- Virtualization ----------

    entry(
        databaseId = 3,
        kind = Kind.SYSTEM,
        section = "Virtualization",
        title = "Xen 1.0",
        originalTitle = "Xen hypervisor",
        author = "Ian Pratt, Keir Fraser and the Cambridge team",
        year = 2003,
        summary = "A hypervisor that runs several operating systems on one machine through paravirtualization, first released in October 2003.",
        details = "Xen grew out of research at Cambridge's Computer Laboratory, led by Ian Pratt with his PhD student Keir Fraser. " +
            "The first public release came in October 2003. Guest systems were modified to call the hypervisor directly, which made virtualization fast on ordinary x86 hardware.",
        rare = true,
    ),
)
