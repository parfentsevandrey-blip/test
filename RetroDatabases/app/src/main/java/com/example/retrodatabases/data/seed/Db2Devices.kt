package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of the 1990s: processors, consoles, graphics and sound cards, handhelds, calculators and clusters. */
val db2Devices: List<RecordEntity> = listOf(

    // ---------- Processors ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel Pentium",
        originalTitle = "Pentium (P5)",
        author = "Intel",
        year = 1993,
        summary = "Intel's fifth-generation processor with a superscalar core; its floating-point division bug became famous.",
        details = "The Pentium (1993) was Intel's first processor to carry a name rather than a number, which made the brand easy to protect. " +
            "In 1994 Thomas Nicely, a number theorist, found an error in floating-point division. " +
            "Intel first played the problem down, then ran a replacement program for the affected chips.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "PowerPC 601",
        originalTitle = "PowerPC 601",
        author = "Apple, IBM and Motorola",
        year = 1993,
        summary = "The first chip of the PowerPC alliance, built on the POWER design from IBM and used in the first Power Macintosh.",
        details = "The 601 (1993) was the first PowerPC processor. The alliance of Apple, IBM and Motorola shared the design, " +
            "which came from IBM's POWER architecture. Power Macintosh machines were among the first desktop systems to use it.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "DEC Alpha 21064",
        originalTitle = "Alpha AXP 21064",
        author = "Digital Equipment Corporation",
        year = 1992,
        summary = "A 64-bit RISC processor designed for speed, used in workstations and servers and in early Windows NT ports.",
        details = "The 21064 (1992) was the first chip of the Alpha family and one of the fastest processors of its day. " +
            "DEC used it in workstations and servers, and Windows NT ran on Alpha machines as well as on Intel ones.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "AMD K6",
        originalTitle = "AMD K6",
        author = "AMD",
        year = 1997,
        summary = "A Pentium-class processor that was cheap and fast in integer code, and the start of AMD's comeback.",
        details = "The K6 (1997) came from NexGen, which AMD had bought, and it fit the same Socket 7 motherboards as the Pentium. " +
            "The later K6-2 added 3DNow! instructions for 3D games.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Cyrix 6x86",
        originalTitle = "Cyrix 6x86 (M1)",
        author = "Cyrix Corporation",
        year = 1995,
        summary = "A fast-for-its-price x86 processor whose floating-point unit was weaker than Intel's.",
        details = "The 6x86 (1995) ran integer code well and was sold as a cheaper alternative to the Pentium. " +
            "Its floating-point unit lagged behind, so users of CAD and 3D software noticed the difference.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel Pentium Pro",
        originalTitle = "Pentium Pro (P6)",
        author = "Intel",
        year = 1995,
        summary = "The P6 core, with out-of-order execution and a second-level cache on the same package, built for servers.",
        details = "The Pentium Pro (1995) introduced the P6 microarchitecture, which broke x86 instructions into simpler internal operations " +
            "and ran them out of order. Its second-level cache sat on the same package as the processor.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "ARM7TDMI",
        originalTitle = "ARM7TDMI",
        author = "Advanced RISC Machines (ARM)",
        year = 1994,
        summary = "A small 32-bit RISC core that ended up in phones, pagers and handhelds made by many companies.",
        details = "The name lists its features: Thumb (16-bit instructions), Debug, a hardware Multiplier and EmbeddedICE debug logic. " +
            "Licensees built it into handsets and organisers, and the core remained common in small devices.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Processors",
        title = "StrongARM SA-110",
        originalTitle = "StrongARM SA-110",
        author = "Digital Equipment Corporation (ARM core licensed)",
        year = 1996,
        summary = "A low-power 32-bit ARM processor that brought the ARM design to handhelds and set-top devices.",
        details = "DEC designed the SA-110 (1996) on the ARM architecture. It combined high clock speeds with low power use, which suited handhelds. " +
            "Intel later bought the line from DEC and sold it as XScale.",
        rare = true,
    ),

    // ---------- Game consoles ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Sega Saturn",
        originalTitle = "Sega Saturn",
        author = "Sega",
        year = 1994,
        summary = "Sega's console with two SH-2 processors and separate video chips, a design that was hard to program for.",
        details = "The Saturn appeared in Japan in 1994 and in the West in 1995. " +
            "Its two Hitachi SH-2 processors and separate video chips gave developers many options, and few of them mastered the machine. " +
            "It lost the 32-bit race to the PlayStation.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Sony PlayStation",
        originalTitle = "PlayStation (PSX)",
        author = "Sony Computer Entertainment",
        year = 1994,
        summary = "Sony's CD-based console with a 32-bit MIPS processor and a development kit that made C programming possible.",
        details = "The PlayStation launched in Japan in December 1994 with a 32-bit MIPS CPU and CD-ROM media. " +
            "Ridge Racer was a launch title, and developers wrote in C with a set of official libraries.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Nintendo 64",
        originalTitle = "Nintendo 64 (N64)",
        author = "Nintendo",
        year = 1996,
        summary = "A cartridge console with a 64-bit processor, an analogue stick and a Rumble Pak accessory.",
        details = "The Nintendo 64 (1996) used a 64-bit MIPS-based processor and cartridges. " +
            "Its controller had an analogue stick as the main way to steer through 3D worlds, and the Rumble Pak made the controller vibrate.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Atari Jaguar",
        originalTitle = "Atari Jaguar",
        author = "Atari Corporation",
        year = 1993,
        summary = "Atari's console sold with a 64-bit claim and three custom chips that few developers mastered.",
        details = "The Jaguar (1993) was marketed as a 64-bit console. Its custom chips were hard to program, and few games used the machine fully. " +
            "Atari left the console market soon after.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "3DO Interactive Multiplayer",
        originalTitle = "3DO Interactive Multiplayer",
        author = "The 3DO Company (hardware licensed to Panasonic and others)",
        year = 1993,
        summary = "A CD console whose design was licensed to several manufacturers, with a 32-bit RISC processor.",
        details = "The 3DO (1993) was built by a company that licensed the design to hardware makers, Panasonic first among them. " +
            "Its 32-bit RISC processor and CD drive looked strong on paper, but the price was high and the console failed in the market.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Virtual Boy",
        originalTitle = "Nintendo Virtual Boy",
        author = "Nintendo, designed by Gunpei Yokoi",
        year = 1995,
        summary = "A stereoscopic console that showed red images through a headset and sat on a stand, remembered for its strange display.",
        details = "Gunpei Yokoi led the Virtual Boy (1995), which showed red images in 3D through a headset. " +
            "Its display caused headaches for many players, and Nintendo discontinued it in 1996.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Net Yaroze",
        originalTitle = "PlayStation Net Yaroze",
        author = "Sony Computer Entertainment",
        year = 1996,
        summary = "A PlayStation sold to hobbyists with development tools, so owners could write their own games in C.",
        details = "Net Yaroze launched in Japan in June 1996 and reached other countries in 1997. " +
            "It was a black PlayStation with a development kit, a C compiler and the official libraries, and owners wrote their own games and demos with it.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Game Boy Color",
        originalTitle = "Game Boy Color",
        author = "Nintendo",
        year = 1998,
        summary = "A colour version of the Game Boy, with a colour screen, extra video memory and a double-speed mode.",
        details = "The Game Boy Color (1998) kept the handheld form of the original and added a colour screen, extra video memory and a double-speed CPU mode. " +
            "Its games were still written for a processor close to the Z80.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Game consoles",
        title = "Sega Dreamcast",
        originalTitle = "Sega Dreamcast",
        author = "Sega",
        year = 1998,
        summary = "Sega's last console, with a Hitachi SH-4 processor, a built-in modem and an operating system based on Windows CE.",
        details = "The Dreamcast launched in Japan in November 1998. Its built-in modem supported online play, " +
            "and its operating system used a Windows CE base. Sega withdrew the console from sale in 2001.",
    ),

    // ---------- Graphics and sound ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Graphics and sound",
        title = "Gravis UltraSound",
        originalTitle = "Gravis UltraSound (GUS)",
        author = "Advanced Gravis Computer Technology",
        year = 1992,
        summary = "A wavetable sound card with its own memory for samples, loved by tracker musicians and demoscene groups.",
        details = "The UltraSound (1992) played recorded instrument samples from its own RAM, which made PC music far richer than the FM synthesis of most sound cards. " +
            "Tracker musicians and demoscene groups wrote music for it.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Graphics and sound",
        title = "Sound Blaster AWE32",
        originalTitle = "Sound Blaster AWE32",
        author = "Creative Technology",
        year = 1994,
        summary = "A Sound Blaster with a wavetable synthesizer, 32 voices and SoundFont instrument banks.",
        details = "The AWE32 (1994) added a wavetable synthesizer with 32 voices and support for SoundFont banks, " +
            "which let games and tools load their own instruments.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Graphics and sound",
        title = "3dfx Voodoo Graphics",
        originalTitle = "Voodoo Graphics",
        author = "3dfx Interactive",
        year = 1996,
        summary = "A separate 3D accelerator card with the Glide API, which games used for their fastest mode in 1997.",
        details = "The Voodoo card (1996) sat beside the ordinary 2D graphics card and handled only 3D work. " +
            "Its Glide API let games such as Quake run faster than software rendering, and the card became the standard 3D accelerator of 1997.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Graphics and sound",
        title = "NVIDIA RIVA 128",
        originalTitle = "RIVA 128",
        author = "NVIDIA",
        year = 1997,
        summary = "A single chip that did both 2D and 3D acceleration and supported Direct3D and OpenGL drivers.",
        details = "The RIVA 128 (1997) put 2D and 3D acceleration on one chip, and it sold strongly in mainstream PCs. " +
            "Its drivers supported Direct3D and OpenGL, which pushed games toward hardware rendering.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Graphics and sound",
        title = "S3 ViRGE",
        originalTitle = "S3 ViRGE",
        author = "S3 Graphics",
        year = 1995,
        summary = "A cheap 2D and 3D chip whose 3D speed was often lower than software rendering, which earned it the nickname 3D decelerator.",
        details = "The ViRGE (1995) was sold as a 3D accelerator, but in many games it ran slower than the CPU alone. " +
            "Reviewers called it the 3D decelerator, and the name stuck.",
        rare = true,
    ),

    // ---------- Home and handheld ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "Amiga CD32",
        originalTitle = "Amiga CD32",
        author = "Commodore",
        year = 1993,
        summary = "A CD-ROM console built from the Amiga 1200 chipset and sold as a games machine.",
        details = "Commodore's CD32 (1993) used the AGA chipset of the Amiga 1200 with a double-speed CD drive. " +
            "Its software was mostly games, and the company's collapse in 1994 ended its life.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "Atari Falcon030",
        originalTitle = "Atari Falcon030",
        author = "Atari Corporation",
        year = 1992,
        summary = "Atari's last home computer, with a DSP for sound and good built-in MIDI support.",
        details = "The Falcon030 (1992) combined a Motorola 68030 processor with a 56001 DSP for sound. " +
            "Its built-in MIDI ports and sampling made it popular with musicians.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "Apple Newton MessagePad",
        originalTitle = "Newton MessagePad",
        author = "Apple Computer",
        year = 1993,
        summary = "A pen-based handheld with handwriting recognition, an early step toward the personal digital assistant.",
        details = "The Newton (1993) ran Newton OS on an ARM processor and recognised handwriting. " +
            "Its recognition was clumsy at first, and the press mocked the device. Its ideas came back in the Palm and Windows CE handhelds.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "Palm Pilot 1000",
        originalTitle = "PalmPilot 1000",
        author = "Palm Computing (Jeff Hawkins)",
        year = 1996,
        summary = "A pocket organiser with a stylus, a handwriting alphabet and desktop sync, which started the Palm era.",
        details = "Hawkins' team designed the Pilot (1996) to fit in a pocket and to synchronise with a desktop PC. " +
            "Its Graffiti alphabet let people write letters with the stylus, and Palm OS later ran on many devices.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "IBM ThinkPad 701",
        originalTitle = "ThinkPad 701 (Butterfly)",
        author = "IBM",
        year = 1995,
        summary = "A notebook whose keyboard opened like a book and spread the keys to full size, the butterfly design.",
        details = "The ThinkPad 701 (1995) had a keyboard that opened out and expanded to full size when the lid was raised. " +
            "It is remembered as one of the most unusual laptop designs of the decade.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "HP 48G",
        originalTitle = "HP 48G",
        author = "Hewlett-Packard",
        year = 1993,
        summary = "A graphing RPN calculator that ran RPL, a stack-based language that engineers programmed.",
        details = "The HP 48 series (1990) introduced a graphing calculator with the RPL language. The 48G (1993) brought more memory. " +
            "Engineers and students wrote games, solvers and utilities in RPL, and many kept a calculator for decades.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Home and handheld",
        title = "TI-92",
        originalTitle = "TI-92",
        author = "Texas Instruments",
        year = 1995,
        summary = "A graphing calculator with a full keyboard and a computer algebra system.",
        details = "The TI-92 (1995) had a QWERTY keyboard and symbolic algebra. " +
            "Students programmed it in TI-BASIC, and an active community shared programs and games.",
        rare = true,
    ),

    // ---------- Clusters and supercomputers ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Clusters and supercomputers",
        title = "Beowulf cluster",
        originalTitle = "Beowulf",
        author = "Donald Becker and Thomas Sterling at NASA's CESDIS",
        year = 1994,
        summary = "A cluster of ordinary PCs running Linux and joined by Ethernet, a cheap alternative to a supercomputer.",
        details = "The first Beowulf (1994) was built at NASA Goddard from commodity PCs and Ethernet. " +
            "The idea spread quickly to universities, and it made parallel computing affordable for many small groups.",
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Clusters and supercomputers",
        title = "Cray T3E",
        originalTitle = "Cray T3E",
        author = "Cray Research (a Silicon Graphics company)",
        year = 1996,
        summary = "A scalable parallel machine built from Alpha processors and linked in a three-dimensional torus.",
        details = "The T3E (1996) connected hundreds of Alpha processors in a three-dimensional torus network. " +
            "It was among the largest parallel systems of its time.",
        rare = true,
    ),

    // ---------- Storage and peripherals ----------

    entry(
        databaseId = 2,
        kind = Kind.DEVICE,
        section = "Storage and peripherals",
        title = "Iomega Zip drive",
        originalTitle = "Iomega Zip",
        author = "Iomega",
        year = 1995,
        summary = "A removable 100 MB disk drive that replaced floppies for backups and file exchange in the late 90s.",
        details = "Iomega launched the Zip drive in 1995, with 100 MB cartridges about the size of a floppy disk. " +
            "It became popular for backups and file exchange, and the \"click of death\" became the name of its most famous failure.",
    ),
)
