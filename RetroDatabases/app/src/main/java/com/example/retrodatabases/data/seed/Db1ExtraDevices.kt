package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of the 1980s that were missing from the first edition of the catalogue, including rare and regional ones. */
val db1ExtraDevices: List<RecordEntity> = listOf(

    // ---------- Home computers ----------

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Acorn Atom",
        originalTitle = "Acorn Atom",
        author = "Acorn Computers",
        year = 1980,
        summary = "A British Acorn computer (1980) on the 6502 with an open expansion bus; the predecessor of the BBC Micro.",
        details = "The Atom was sold assembled and as a kit. Expansions were connected through a connector on the Eurocard bus. " +
            "Programs were written in BASIC and in 6502 assembler.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Acorn Electron",
        originalTitle = "Acorn Electron",
        author = "Acorn Computers",
        year = 1983,
        summary = "A simplified home version of the BBC Micro (1983) on the same 6502: cheaper and with simpler peripherals.",
        details = "The Electron used the instruction set of the BBC Micro, but with a reduced set of external connectors. " +
            "The machine was sold mainly in Britain.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Amstrad CPC 464",
        originalTitle = "Amstrad CPC 464",
        author = "Amstrad",
        year = 1984,
        summary = "A European home computer (1984) on the Z80 with a cassette deck and a monitor in one case.",
        details = "In 1985 the CPC 664 and 6128 came out with a built-in 3-inch disk drive. The machine was popular in Britain, France and Spain.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Commodore VIC-20",
        originalTitle = "Commodore VIC-20",
        author = "Commodore",
        year = 1980,
        summary = "Commodore's first colour computer (1980) on the 6502; sold in millions of copies.",
        details = "The VIC-20 became one of the first mass-market computers with colour graphics. The low price started a price war " +
            "in the home computer market.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Commodore 128",
        originalTitle = "Commodore 128",
        author = "Commodore",
        year = 1985,
        summary = "A successor to the C64 (1985) with three modes: Commodore BASIC, CP/M on the Z80 and compatibility with the C64.",
        details = "The Z80 processor made it possible to run CP/M. The machine came out late and did not become mass-market, " +
            "although C64 programs ran on it.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Commodore PET 2001",
        originalTitle = "Commodore PET 2001",
        author = "Commodore",
        year = 1977,
        summary = "One of the first ready-made personal computers (1977), with a monochrome screen and built-in BASIC.",
        details = "The PET 2001 is built on the 6502 with BASIC from Microsoft and a built-in cassette deck. Later Commodore moved on to the VIC-20 and the C64.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Atari 800",
        originalTitle = "Atari 400 and 800",
        author = "Atari",
        year = 1979,
        summary = "The Atari home computer (1979) with the special chips ANTIC and GTIA for colour graphics and sound.",
        details = "The 400 and 800 models became the basis of a popular series of 8-bit machines. Hardware sprites and graphics made smooth games possible.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Sinclair ZX81",
        originalTitle = "Sinclair ZX81",
        author = "Sinclair Research",
        year = 1981,
        summary = "An ultra-cheap Sinclair computer (1981) with 1 KB of memory: BASIC and a screen without sound.",
        details = "The ZX81 was sold as a kit and ready-made. The program drew the picture, so the processor was almost always busy with output.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Sinclair QL",
        originalTitle = "Sinclair QL",
        author = "Sinclair Research",
        year = 1984,
        summary = "A Sinclair computer (1984) on the 68008 with the multitasking QDOS and SuperBASIC; Microdrive storage.",
        details = "The QL anticipated multitasking in home computers. The unreliable Microdrive cartridges damaged the machine's reputation.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Oric-1",
        originalTitle = "Oric-1",
        author = "Tangerine Computer Systems",
        year = 1983,
        summary = "A British home computer (1983) on the 6502 with its own ULA logic; a rival of the ZX Spectrum.",
        details = "The Oric-1 sold at the price of a ZX Spectrum and offered colour graphics. The Atmos model (1984) improved the keyboard.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "TI-99/4A",
        originalTitle = "Texas Instruments TI-99/4A",
        author = "Texas Instruments",
        year = 1981,
        summary = "A Texas Instruments home computer (1981) on the 16-bit TMS9900: one of the first 16-bit home machines.",
        details = "A lot of expansion modules grew up around the machine. In the 1983 price war, TI sold the machine at a loss.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Coleco Adam",
        originalTitle = "Coleco Adam",
        author = "Coleco",
        year = 1983,
        summary = "A Coleco home computer (1983) as an add-on to the ColecoVision console, with a built-in printer.",
        details = "The Adam was sold with a printer and a cassette system, but the reliability of data storage was questioned. " +
            "The machine was soon withdrawn from the market.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Enterprise 64",
        originalTitle = "Enterprise 64 and 128",
        author = "Intelligent Software (Great Britain)",
        year = 1985,
        summary = "A British computer (1985) on the Z80 with a large memory and its own graphics chip, Nick.",
        details = "The Enterprise was meant to compete with the ZX Spectrum and the C64, but it appeared late and did not become mass-market.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Dragon 32",
        originalTitle = "Dragon 32",
        author = "Dragon Data",
        year = 1982,
        summary = "A British computer (1982) on the 6809 with BASIC close to that of the TRS-80 Color Computer.",
        details = "The Dragon 32 used the same video chip as the TRS-80 CoCo, so programs were moved with difficulty, but it was possible.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "TRS-80 Color Computer",
        originalTitle = "Tandy TRS-80 Color Computer (CoCo)",
        author = "Tandy / Radio Shack",
        year = 1980,
        summary = "A Tandy home computer (1980) on the 6809, for which the multitasking OS-9 was released.",
        details = "The CoCo worked with OS-9 and supported many clones and expansions. The machine was popular in America and Canada.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "TRS-80 Model 100",
        originalTitle = "Tandy TRS-80 Model 100",
        author = "Tandy",
        year = 1983,
        summary = "A Tandy portable computer (1983) with an LCD screen, built-in BASIC and battery power.",
        details = "The Model 100 was the forerunner of today's laptops: programs were kept in memory, with no disks. Journalists took it on assignments.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Apple IIe",
        originalTitle = "Apple IIe",
        author = "Apple",
        year = 1983,
        summary = "An improved Apple II (1983): lowercase letters, 80-column text and a built-in keyboard.",
        details = "The Apple IIe was produced until the end of the 1980s and was the main machine of US schools. Programs for the Apple II ran on it.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Apple IIGS",
        originalTitle = "Apple IIGS",
        author = "Apple",
        year = 1986,
        summary = "An Apple II on the 16-bit 65C816 (1986) with Super Hi-Res colour graphics and Ensoniq sound.",
        details = "The IIGS is compatible with Apple II programs and ran under GS/OS, its own operating system with a directory hierarchy.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Amiga 500",
        originalTitle = "Commodore Amiga 500",
        author = "Commodore",
        year = 1987,
        summary = "The budget Amiga (1987) with a multimedia chipset and a multitasking OS.",
        details = "The Amiga 500 became one of the best-selling Amiga models. Demos and games that people still remember were written on it.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Sharp X1",
        originalTitle = "Sharp X1",
        author = "Sharp",
        year = 1982,
        summary = "A Japanese home computer (1982) on the Z80: a popular platform for games and education.",
        details = "The X1 used cassettes and floppy disks and had a large library of games in Japan.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Sharp X68000",
        originalTitle = "Sharp X68000",
        author = "Sharp",
        year = 1987,
        summary = "A Japanese computer (1987) on the 68000 with hardware sprites: built as a gaming machine.",
        details = "The X68000 was used for ports of arcade games and for demos. Hardware sprites allowed many objects to move without loading the processor.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "NEC PC-8800",
        originalTitle = "NEC PC-8800",
        author = "NEC",
        year = 1981,
        summary = "A Japanese NEC computer (1981) on the Z80 with colour graphics: a platform for business and games.",
        details = "The PC-8800 series became one of the most popular in Japan alongside the PC-9800.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Fujitsu FM-7",
        originalTitle = "Fujitsu FM-7",
        author = "Fujitsu",
        year = 1982,
        summary = "A Japanese Fujitsu computer (1982) on the 6809 with colour graphics and a disk drive.",
        details = "The FM-7 became popular among Japanese game developers and educational software writers.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Sega SC-3000",
        originalTitle = "Sega SC-3000",
        author = "Sega",
        year = 1983,
        summary = "A Japanese Sega console-computer (1983) on the Z80 with BASIC and a keyboard.",
        details = "The SC-3000 worked with BASIC and cartridges. The SG-1000 line went on as a game console.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Samsung SPC-1000",
        originalTitle = "Samsung SPC-1000",
        author = "Samsung",
        year = 1983,
        summary = "A Korean home computer (1983) on the Z80 with BASIC and a cassette interface.",
        details = "The SPC-1000 was one of Samsung's first computers.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Tatung Einstein",
        originalTitle = "Tatung Einstein",
        author = "Tatung",
        year = 1984,
        summary = "A Taiwanese computer (1984) on the Z80 with CP/M and built-in video output.",
        details = "The Einstein was sold in Europe as an alternative to the Spectrum with a disk drive and CP/M.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Matra Alice",
        originalTitle = "Matra Alice",
        author = "Matra",
        year = 1983,
        summary = "The French Matra Alice computer (1983) on the Motorola 6803: a copy of the TRS-80 MC-10 with the 6847 video chip.",
        details = "The Alice was sold in France. Some sources say it did not catch on at home; others say it was taken into schools.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Thomson TO7",
        originalTitle = "Thomson TO7",
        author = "Thomson",
        year = 1982,
        summary = "A French Thomson computer (1982) on the 6809, popular in France and in schools.",
        details = "Thomson released the TO and MO lines. These machines stood in many French families and schools.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "Philips P2000",
        originalTitle = "Philips P2000",
        author = "Philips",
        year = 1980,
        summary = "A Philips computer (1980) on the Z80. The P2000T model had the Mullard SAA5050 teletext chip and a built-in mini cassette recorder.",
        details = "The P2000T was popular with Dutch enthusiasts and in schools, but the high price and limited teletext held back sales. " +
            "Philips later moved on to MSX.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Home computers",
        title = "MicroBee",
        originalTitle = "MicroBee",
        author = "Microbee Systems",
        year = 1983,
        summary = "An Australian MicroBee computer (1983) on the Z80 with CP/M and disk support.",
        details = "The MicroBee was used in Australian schools and businesses. Several models were produced until the end of the 1980s.",
        rare = true,
    ),

    // ---------- Personal computers ----------

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Apple Lisa",
        originalTitle = "Apple Lisa",
        author = "Apple",
        year = 1983,
        summary = "The first Apple with a graphical interface, windows and a mouse (1983); expensive and slow.",
        details = "The Lisa came before the Macintosh, and its interface influenced Mac OS. It sold poorly because of its high price.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "IBM PC AT",
        originalTitle = "IBM Personal Computer AT",
        author = "IBM",
        year = 1984,
        summary = "The IBM PC on the 80286 (1984) with the ISA bus: the basis of the business standard for years.",
        details = "The PC AT offered protected mode and a 16-bit bus. The ISA bus remained the standard until the end of the 1990s.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "IBM PS/2",
        originalTitle = "IBM PS/2",
        author = "IBM",
        year = 1987,
        summary = "The IBM PS/2 family (1987) with the MCA bus and 3.5-inch floppy disks.",
        details = "The PS/2 introduced the keyboard and mouse connectors that are still used today. The MCA bus did not become a standard.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Compaq Portable",
        originalTitle = "Compaq Portable",
        author = "Compaq",
        year = 1983,
        summary = "The first fully IBM PC-compatible computer (1983), built through a rewritten BIOS.",
        details = "Compaq created its own BIOS that works like the IBM PC BIOS, without an IBM licence. This opened the way for clones.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Kaypro II",
        originalTitle = "Kaypro II",
        author = "Kaypro",
        year = 1982,
        summary = "A portable CP/M computer (1982) with a 9-inch screen and two disk drives.",
        details = "The Kaypro II became one of the most popular CP/M computers. The machine was sold with WordStar and SuperCalc.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Amstrad PCW",
        originalTitle = "Amstrad PCW",
        author = "Amstrad",
        year = 1985,
        summary = "A word-processing computer on the Z80 with CP/M (1985) for home users.",
        details = "The PCW was sold as a cheap replacement for the typewriter: a printer came in the box.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Amstrad PC1512",
        originalTitle = "Amstrad PC1512",
        author = "Amstrad",
        year = 1986,
        summary = "A cheap IBM-compatible computer (1986) with MS-DOS and colour graphics for the home.",
        details = "The PC1512 was compatible with the IBM PC and was sold in Britain as a home computer for work.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "NEC PC-9800",
        originalTitle = "NEC PC-9800 (PC-98)",
        author = "NEC",
        year = 1982,
        summary = "The Japanese PC-98 standard (1982) on the 8086, dominant in Japan until the end of the 1990s.",
        details = "The PC-9800 had its own architecture, incompatible with the IBM PC, so the Japanese market developed separately.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Fujitsu FM Towns",
        originalTitle = "Fujitsu FM Towns",
        author = "Fujitsu",
        year = 1989,
        summary = "A Japanese multimedia PC (1989) on the 80386SX with a CD-ROM: almost a game console.",
        details = "The FM Towns ran DOS and Japanese versions of programs. The CD-ROM provided sound and video at the level of early multimedia systems.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "DEC Rainbow 100",
        originalTitle = "DEC Rainbow 100",
        author = "Digital Equipment Corporation",
        year = 1982,
        summary = "A dual-processor DEC PC (1982): a Z80 for CP/M and an 8088 for MS-DOS.",
        details = "The Rainbow 100 could run both operating systems, which was rare for 1982.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Psion Organiser",
        originalTitle = "Psion Organiser",
        author = "Psion",
        year = 1984,
        summary = "A Psion pocket computer (1984) with a calculator, a diary and programs: the ancestor of today's pocket devices.",
        details = "The Organiser fitted in the palm of a hand and combined a calculator, a notebook and simple programs.",
        rare = true,
    ),

    // ---------- Workstations ----------

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Apollo DN100",
        originalTitle = "Apollo DN100",
        author = "Apollo Computer",
        year = 1981,
        summary = "The Apollo workstation (1981) with the Domain network OS and a mouse.",
        details = "Apollo was one of the first makers of distributed workstations: Domain let people work on a network without a dedicated server.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Sun-1",
        originalTitle = "Sun-1",
        author = "Sun Microsystems",
        year = 1982,
        summary = "The first Sun workstation (1982) with Unix BSD and an Ethernet network connection.",
        details = "The name Sun comes from the Stanford University Network. Sun workstations often stood in university labs.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Symbolics 3600",
        originalTitle = "Symbolics 3600",
        author = "Symbolics",
        year = 1983,
        summary = "The Symbolics Lisp machine (1983) with hardware support for Lisp and the Genera environment.",
        details = "The 3600 ran Lisp faster than general-purpose computers of that time. It was expensive and was sold to scientists and the military.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Three Rivers PERQ",
        originalTitle = "Three Rivers PERQ",
        author = "Three Rivers Computer Corporation",
        year = 1980,
        summary = "One of the first commercial workstations with windows and a mouse (1980).",
        details = "The PERQ was sold as a competitor to Xerox workstations and used raster graphics with its own interface.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Lilith",
        originalTitle = "Lilith (ETH Zurich)",
        author = "Niklaus Wirth, ETH Zurich",
        year = 1980,
        summary = "The ETH Zurich workstation (1980), designed together with the Modula-2 language.",
        details = "Lilith used raster graphics and a mouse. It ran the Medos-2 operating system, written in Modula-2.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Workstations",
        title = "Ceres",
        originalTitle = "Ceres (ETH Zurich)",
        author = "ETH Zurich, Niklaus Wirth, Jürg Gutknecht",
        year = 1986,
        summary = "The ETH Zurich workstation (1986) for the Oberon system: a mouse and a high-density screen.",
        details = "Ceres was built for the Oberon operating system. Together with it came an editor in which the text itself was a command.",
        rare = true,
    ),

    // ---------- Supercomputers and parallel machines ----------

    entry(
        kind = Kind.DEVICE,
        section = "Supercomputers and parallel machines",
        title = "Cray X-MP",
        originalTitle = "Cray X-MP",
        author = "Cray Research",
        year = 1982,
        summary = "A two-processor Cray (1982): the successor to the Cray-1 with shared memory.",
        details = "The X-MP used parallel processors with shared memory. Such machines worked in national scientific centres.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Supercomputers and parallel machines",
        title = "Connection Machine CM-1",
        originalTitle = "Connection Machine CM-1",
        author = "Thinking Machines Corporation",
        year = 1985,
        summary = "A parallel machine (1985) with 65 536 simple processors; each processed its own data element.",
        details = "The CM-1 data model resembled arrays: one instruction was executed on all the elements at once. " +
            "Languages such as *Lisp were created for the machine.",
        rare = true,
    ),

    // ---------- Eastern bloc and China ----------

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Pravetz-8D",
        originalTitle = "Pravetz 8D",
        author = "Pravetz (Bulgaria)",
        year = 1985,
        summary = "The Bulgarian Pravetz-8D computer: an Oric Atmos clone on the 6502, produced in Bulgaria in the 1980s.",
        details = "The Pravetz series was produced in Bulgaria in the 1980s. The 8D model is a clone of the Oric Atmos, and the Pravetz 82 is a clone of the Apple II.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Galeb",
        originalTitle = "PEL Varaždin Galeb",
        author = "PEL Varaždin (Yugoslavia)",
        year = 1981,
        summary = "The Yugoslav computer Galeb (1981) on the 6502, made by PEL Varaždin.",
        details = "Galeb was produced by the firm PEL Varaždin (Varaždin, Croatia). The monitor was monochrome, 96×48 pixels; " +
            "the 16 KB ROM held BASIC and a machine-code monitor. Output stopped at about 250 machines in 1984, when the Orao replaced it.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Orao",
        originalTitle = "PEL Varaždin Orao",
        author = "PEL Varaždin (Yugoslavia)",
        year = 1984,
        summary = "The Yugoslav computer Orao (1984) on the 6502, the successor of the Galeb.",
        details = "The Orao was the standard school computer in Croatia and Vojvodina from 1985 to 1991. It had 16 KB of ROM and 16 KB of RAM, expandable to 32 KB.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Pecom 64",
        originalTitle = "Pecom 64",
        author = "Elektronska Industrija Niš (Yugoslavia)",
        year = 1985,
        summary = "The Yugoslav computer Pecom 64 (1985) on the CDP1802B (RCA 1802) with 32 KB of RAM and colour graphics.",
        details = "Pecom 64 was produced by the firm Elektronska Industrija in Niš for schools and homes. " +
            "The video output was built on the CDP1869 and CDP1870 chips: 40×24 text and up to eight colours.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Tesla PMD 85",
        originalTitle = "Tesla PMD 85",
        author = "Tesla (Czechoslovakia)",
        year = 1985,
        summary = "The Czechoslovak computer Tesla PMD 85 (1985) on the MHB8080A, a Czechoslovak copy of the Intel 8080, at 2.048 MHz.",
        details = "The PMD 85 was produced by Tesla Piešťany and Tesla Bratislava until 1989. The 85-1, 2A and 3 models differed in the amount of RAM, up to 64 KB.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Robotron KC 85",
        originalTitle = "VEB Robotron KC 85",
        author = "VEB Robotron (GDR)",
        year = 1984,
        summary = "The East German computer (1984) on a Z80-compatible processor, with cassettes and BASIC.",
        details = "The KC 85 was produced for schools and enterprises of the GDR. Programs were loaded from a tape recorder.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Robotron Z1013",
        originalTitle = "VEB Robotron Z1013",
        author = "VEB Robotron (GDR)",
        year = 1985,
        summary = "A GDR hobby-kit computer (1985) on the U880, an East German clone of the Z80.",
        details = "The buyer received a ready-made board with a tested circuit, a flat keyboard and a manual, but without a power supply or a case. " +
            "The Z1013.16 and Z1013.64 variants came out in 1987 and 1988.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "Videoton TVC",
        originalTitle = "Videoton TVC",
        author = "Videoton (Hungary)",
        year = 1986,
        summary = "The Hungarian home computer (1986) on the Z80, the most widespread home platform in Hungary.",
        details = "The TVC was sold through schools and shops, and supported BASIC and saving to cassettes.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Eastern bloc and China",
        title = "CEC-I",
        originalTitle = "CEC-I (China Education Computer)",
        author = "Huaming Computer (China, per sources)",
        year = 1986,
        summary = "A Chinese Apple II clone (1986 and later) on the 6502A with 64 KB of memory: an educational machine.",
        details = "Sources disagree on the name of the maker and on the date: 1986 or November 1987. Chinese characters were added as a separate module.",
        rare = true,
    ),

    // ---------- Soviet machines ----------

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "Vector-06C",
        originalTitle = "Vektor-06Ts",
        author = "Soviet industry",
        year = 1987,
        summary = "The Soviet home computer Vector-06C (1987) on a domestic clone of the Intel 8080.",
        details = "The machine is compatible with the 8080 processor and worked with cassettes and floppy disks. Educational and game programs were written on it.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "Radio-86RK",
        originalTitle = "Radio-86RK",
        author = "The magazine “Radio” (hobby kit)",
        year = 1986,
        summary = "A hobby-kit computer (1986) on the KR580VM80, described in the magazine “Radio”.",
        details = "The circuit and the programs were published in the magazine, so many people built the machine themselves. " +
            "The design became a classic of Soviet amateur radio.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Soviet machines",
        title = "Iskra-1030",
        originalTitle = "Iskra-1030",
        author = "Soviet industry",
        year = 1989,
        summary = "The Soviet IBM PC/XT-compatible computer “Iskra-1030” (1989) on an Intel 8086 clone at 4.77 MHz.",
        details = "Designed in Leningrad (Elektronmash), it was produced at factories in Smolensk and Kursk. " +
            "It was also installed in the computing labs of some institutes.",
        rare = true,
    ),

    // ---------- Processors ----------

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel 80286",
        originalTitle = "Intel 80286",
        author = "Intel",
        year = 1982,
        summary = "The 16-bit Intel processor (1982) with protected mode; the basis of the IBM PC AT.",
        details = "Protected mode allowed multitasking and isolation of programs, but going back to real mode was difficult.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel 80386",
        originalTitle = "Intel 80386",
        author = "Intel",
        year = 1985,
        summary = "The first 32-bit processor of the x86 family (1985).",
        details = "The 386 introduced paged memory and virtual machines. DOS programs ran on it in V86 mode.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Motorola 68020",
        originalTitle = "Motorola 68020",
        author = "Motorola",
        year = 1984,
        summary = "The 32-bit Motorola processor (1984), the basis of the Mac II and the Sun-3.",
        details = "The 68020 had fully 32-bit registers and a bus, as well as an instruction cache. The first powerful workstations were built on it.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Zilog Z8000",
        originalTitle = "Zilog Z8000",
        author = "Zilog",
        year = 1979,
        summary = "The 16-bit Zilog processor (1979) that did not gain wide use.",
        details = "The Z8000 was designed as a response to the 8086 and the 68000. Despite a good architecture, it came too late and remained a niche product.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "Intel 8051",
        originalTitle = "Intel MCS-51 (8051)",
        author = "Intel",
        year = 1980,
        summary = "The Intel single-chip microcontroller (1980), the basis of a huge number of embedded systems.",
        details = "The 8051 is still popular today. Programs for it were written in assembler, and later in C.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "RCA 1802",
        originalTitle = "RCA CDP1802 COSMAC",
        author = "RCA",
        year = 1976,
        summary = "The RCA processor (1976) in CMOS: used in space, in game and educational computers.",
        details = "The CDP1802 was resistant to radiation and ran on a low voltage. It was used in the COMX-35 and the Pecom.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "ARM1",
        originalTitle = "Acorn ARM1",
        author = "Acorn Computers, Steve Furber and Sophie Wilson",
        year = 1985,
        summary = "The first ARM processor (1985), created at Acorn for the future Archimedes computers.",
        details = "The ARM1 had a simple RISC architecture and few transistors. Its descendants work today in phones and embedded devices.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "KR580VM80",
        originalTitle = "KR580VM80 (Soviet analogue of the Intel 8080)",
        author = "Soviet industry",
        year = 1980,
        summary = "The Soviet analogue of the Intel 8080 (1980): the basis of the Radio-86RK and many hobby machines.",
        details = "The processor is compatible with the 8080 instruction set and was widely used in Soviet microcomputers.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "KR1810VM86",
        originalTitle = "KR1810VM86 (Soviet analogue of the Intel 8086)",
        author = "Soviet industry",
        year = 1985,
        summary = "The Soviet analogue of the Intel 8086 (1985), on which domestic PCs were built.",
        details = "The KR1810VM86 is compatible with the 8086 instruction set. It was used in domestic IBM-compatible machines.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Processors",
        title = "K1801VM1",
        originalTitle = "K1801VM1 (analogue of the LSI-11)",
        author = "Soviet industry",
        year = 1984,
        summary = "A Soviet processor (1984) compatible with the PDP-11: the basis of the BK-0010 and the educational DVK systems.",
        details = "The K1801VM1 made it possible to run PDP-11 software, so an entire class of educational machines appeared in the USSR.",
        rare = true,
    ),

    // ---------- Peripherals and communications ----------

    entry(
        kind = Kind.DEVICE,
        section = "Peripherals and communications",
        title = "Hayes Smartmodem",
        originalTitle = "Hayes Smartmodem",
        author = "Hayes Microcomputer Products",
        year = 1981,
        summary = "A modem controlled by AT commands (1981): the AT command set is still in use today.",
        details = "The commands start with AT and control dialling and communication modes. " +
            "The modems of this series set the standard for BBSs and 80s communications.",
        rare = true,
    ),
)
