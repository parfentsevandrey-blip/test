package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Machines of the 1980s: mainframes, Soviet computers, personal and home computers, processors. */
val db1Devices: List<RecordEntity> = listOf(

    // ---------- Mainframes and minicomputers ----------

    entry(
        kind = Kind.DEVICE,
        section = "Mainframes and minicomputers",
        title = "IBM System/360",
        originalTitle = "IBM System/360",
        author = "IBM",
        year = 1964,
        summary = "A family of compatible machines: one architecture from small models to large ones.",
        details = "IBM released the System/360 in 1964. Programs moved between models, and the idea of compatibility " +
            "became an industry standard. OS/360 and the Soviet ES EVM grew on this architecture.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Mainframes and minicomputers",
        title = "PDP-11",
        originalTitle = "DEC PDP-11",
        author = "Digital Equipment Corporation",
        year = 1970,
        summary = "The DEC 16-bit minicomputer: eight registers and a clear instruction set.",
        details = "The PDP-11 architecture was convenient for programmers. Unix ran on early PDP-11s, " +
            "and the C language was developed on these machines.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Mainframes and minicomputers",
        title = "VAX-11/780",
        originalTitle = "DEC VAX-11/780",
        author = "Digital Equipment Corporation",
        year = 1977,
        summary = "A 32-bit DEC minicomputer with virtual memory; VMS was created for it.",
        details = "The VAX-11/780 became the standard of university computing centres in the 80s. Virtual memory let " +
            "large programs run without overlays.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Mainframes and minicomputers",
        title = "Cray-1",
        originalTitle = "Cray-1",
        author = "Cray Research, Seymour Cray",
        year = 1976,
        summary = "A supercomputer with vector registers: programs were written to process whole arrays at once.",
        details = "The Cray-1 performed vector operations on registers. This style, vectorization, shaped " +
            "how scientific programs of the 80s were written.",
    ),

    // ---------- Soviet machines ----------

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "BESM-6",
        originalTitle = "BESM-6",
        author = "A team led by Sergei Lebedev, ITMiVT",
        year = 1968,
        summary = "The flagship of Soviet computing in the 1960s–70s: about a million operations per second.",
        details = "BESM-6 ran the “Dubna” monitor, Algol-60 and Fortran. The machines stood in institutes and computing centres " +
            "across the country and were the basis of calculations for science and defence.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "ES EVM",
        originalTitle = "Unified System of Computers (ES EVM)",
        author = "CMEA countries",
        year = 1973,
        summary = "The Unified System of computers (ES EVM): a Soviet line compatible with the IBM System/360.",
        details = "The series was developed together with the CMEA countries, and the models were produced in series. " +
            "Compatibility with IBM made it possible to use Western software and ideas.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "Elbrus-1",
        originalTitle = "Elbrus-1",
        author = "A team of ITMiVT",
        year = 1979,
        summary = "A Soviet computer with an unconventional architecture: tagged memory and hardware support for high-level languages.",
        details = "The project was considered one of the boldest in Soviet computing. Programs were written in its own " +
            "high-level language, and the architecture became a subject of study at universities.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "Agat",
        originalTitle = "Agat",
        author = "Soviet industry",
        year = 1984,
        summary = "A Soviet clone of the Apple II: programs written for the Apple often ran on the Agat too.",
        details = "The machine was produced in the USSR on the basis of Apple II ideas and became one of the best-known home machines " +
            "of domestic production. It was loved in schools and hobby clubs.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "BK-0010",
        originalTitle = "Elektronika BK-0010",
        author = "Soviet industry",
        year = 1985,
        summary = "A Soviet home computer: BASIC in ROM, connected to an ordinary TV set.",
        details = "The BK-0010 worked with a TV set and kept BASIC in permanent memory, so the user could write programs right away. " +
            "Such machines stood in school classes and clubs.",
        rare = true,
    ),

    // ---------- Personal computers ----------

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Altair 8800",
        originalTitle = "MITS Altair 8800",
        author = "MITS",
        year = 1975,
        summary = "The first mass-market kit for building a PC on the Intel 8080.",
        details = "It was sold through the magazine Popular Electronics. Microsoft wrote Altair BASIC for it, the company's first product. " +
            "Programs were entered with switches on the front panel.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Apple II",
        originalTitle = "Apple II",
        author = "Apple",
        year = 1977,
        summary = "The Apple personal computer with colour graphics and BASIC in ROM.",
        details = "An open architecture with expansion slots created a huge market for boards and programs. In the 80s " +
            "the Apple II was the main machine of US schools.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "IBM PC",
        originalTitle = "IBM Personal Computer",
        author = "IBM",
        year = 1981,
        summary = "IBM's open architecture with the Intel 8088 and MS-DOS: the standard for personal computers.",
        details = "IBM PC clones quickly took over the market, and the architecture became the standard. For programmers this meant: " +
            "one code, many machines, but also many compatibility subtleties.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Apple Macintosh 128K",
        originalTitle = "Macintosh 128K",
        author = "Apple",
        year = 1984,
        summary = "A personal computer with a mouse, windows and a Motorola 68000 processor.",
        details = "128 KB of memory and a small black-and-white screen, but its mouse-driven interface became the reference. " +
            "Programmers wrote for the Toolbox, a set of interface procedures.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Osborne 1",
        originalTitle = "Osborne 1",
        author = "Osborne Computer Corporation",
        year = 1981,
        summary = "The first mass-market portable computer: about 11 kg, CP/M and a bundle of programs.",
        details = "The machine was sold with WordStar, SuperCalc and BASIC. Its screen was small, but it could be carried around. " +
            "The company went bankrupt after announcing a new model, which gave rise to the term “Osborne effect”.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Atari ST",
        originalTitle = "Atari ST",
        author = "Atari",
        year = 1985,
        summary = "A computer with the Motorola 68000 and built-in MIDI ports; loved by musicians.",
        details = "TOS and GEM were loaded from ROM. The MIDI interface was built into the case, so the ST became the standard " +
            "in studios and among keyboard players.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Amiga 1000",
        originalTitle = "Commodore Amiga 1000",
        author = "Commodore",
        year = 1985,
        summary = "A multimedia computer with co-processors for sound and graphics, and multitasking.",
        details = "The Agnus, Denise and Paula chips took over graphics, sound and disk work. The 68000 processor was free " +
            "for the program, which gave smooth games and animation.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "NeXT Computer",
        originalTitle = "NeXT Computer",
        author = "NeXT, Steve Jobs",
        year = 1988,
        summary = "A workstation with Mach/BSD, Objective-C and the Interface Builder interface designer.",
        details = "Jobs created NeXT after leaving Apple. The NeXTSTEP system, built on Objective-C, anticipated many ideas " +
            "of later platforms.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Xerox Alto",
        originalTitle = "Xerox Alto",
        author = "Xerox PARC",
        year = 1973,
        summary = "The first personal workstation: windows, a mouse and an Ethernet network (a prototype of 1973).",
        details = "The Alto was built at Xerox PARC for research. Programs were written in Smalltalk and Mesa. " +
            "The machine became the model for the Star, the Lisa and the Macintosh.",
        rare = true,
    ),

    // ---------- Home computers ----------

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "ZX Spectrum",
        originalTitle = "Sinclair ZX Spectrum",
        author = "Sinclair Research, Clive Sinclair",
        year = 1982,
        summary = "A British home computer: Z80, BASIC in ROM and colour graphics.",
        details = "The machine sold cheaply, with 16–48 KB of memory. Games and demos for the ZX Spectrum were written in machine code: " +
            "BASIC was slow.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Commodore 64",
        originalTitle = "Commodore 64",
        author = "Commodore",
        year = 1982,
        summary = "A home computer with 64 KB of memory, the SID sound chip and VIC-II graphics.",
        details = "One of the best-selling models in history. The SID sound chip gave polyphonic synthesis, " +
            "and it is still studied in the demoscene.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "BBC Micro",
        originalTitle = "Acorn BBC Micro",
        author = "Acorn Computers",
        year = 1981,
        summary = "An Acorn educational computer for British schools with a 6502 processor, BASIC and Logo.",
        details = "The BBC Computer Literacy Project supplied the machines to schools. The BASIC in ROM had extended commands " +
            "for graphics and sound.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "MSX",
        originalTitle = "MSX",
        author = "Microsoft, ASCII and Japanese manufacturers",
        year = 1983,
        summary = "A unified home computer standard: the same BASIC and cartridges on machines from different makers.",
        details = "Microsoft and Japanese manufacturers agreed on the standard. Programs and cartridges worked on machines from different " +
            "companies, a rare example of compatibility in the home computer world.",
        rare = true,
    ),

    // ---------- Processors ----------

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel 8086",
        originalTitle = "Intel 8086",
        author = "Intel",
        year = 1978,
        summary = "A 16-bit Intel processor: the start of the x86 family and the basis of the IBM PC.",
        details = "The x86 architecture grew out of the 8086 and still runs 80s code in real mode. Programmers of the time " +
            "mastered segmented addressing: a 20-bit address was formed from two 16-bit numbers.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Zilog Z80",
        originalTitle = "Zilog Z80",
        author = "Zilog, Federico Faggin",
        year = 1976,
        summary = "An 8-bit processor with an extended 8080 instruction set: the ZX Spectrum and CP/M machines.",
        details = "The Z80 was compatible with the 8080 at the level of programs and added index registers and new instructions. " +
            "It was used in the ZX Spectrum, the Amstrad CPC and hundreds of CP/M machines.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "MOS 6502",
        originalTitle = "MOS Technology 6502",
        author = "MOS Technology, Chuck Peddle",
        year = 1975,
        summary = "A cheap 8-bit processor: the Apple II, the BBC Micro, the Commodore PET.",
        details = "A simple and fast processor with few registers. Programmers on the 6502 counted cycles and used zero page " +
            "memory as fast registers.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Motorola 68000",
        originalTitle = "Motorola 68000",
        author = "Motorola",
        year = 1979,
        summary = "A processor with 32-bit registers and a 16-bit bus: the Macintosh, the Amiga and the Atari ST.",
        details = "An orthogonal instruction set and convenient addressing modes appealed to C and assembler programmers. " +
            "A whole generation of personal computers was built on the 68000.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "INMOS Transputer",
        originalTitle = "INMOS T414 Transputer",
        author = "INMOS, David May",
        year = 1984,
        summary = "A processor with hardware communication channels for parallel systems; programs were written in Occam.",
        details = "Transputers were connected into networks without shared memory. Programs for them described processes and channels " +
            "directly in the Occam language.",
        rare = true,
    ),
)
