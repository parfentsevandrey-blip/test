package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1970 to 1980: the first microprocessors, a floppy disk, a sealed hard disk, calculators, home video, and the first personal computers. */
val db8Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Microprocessors",
        title = "Intel 4004",
        originalTitle = "Intel 4004, the first microprocessor",
        author = "Intel; design credited to Federico Faggin and Ted Hoff",
        year = 1971,
        summary = "Intel's 4-bit microprocessor, with about 2,300 transistors, made for the Busicom calculator and announced in November 1971.",
        details = "It put a whole central processor on one chip, which made the calculator cheaper and the microprocessor an idea that other companies could copy. Its successors, the 8008 and the 8080, followed within three years.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Storage",
        title = "IBM 23FD floppy disk",
        originalTitle = "IBM 23FD, the 8-inch floppy disk",
        author = "IBM",
        year = 1971,
        summary = "IBM's 8-inch floppy disk, introduced in 1971, the first flexible magnetic medium that could be carried from one machine to another.",
        details = "It was first used as a read-only medium for loading programs into System/370 machines. Later floppies could be read and written, and the format spread to the small computers of the late 1970s.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Microprocessors",
        title = "Intel 8008",
        originalTitle = "Intel 8008, an 8-bit microprocessor",
        author = "Intel",
        year = 1972,
        summary = "Intel's 8-bit microprocessor of 1972, with about 3,500 transistors, the first 8-bit chip of the line that led to the 8080.",
        details = "It was designed for a terminal maker and then offered to other customers, and its speed kept it out of many tasks. Its design was the starting point for the 8080, two years later.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Calculators",
        title = "HP-35",
        originalTitle = "Hewlett-Packard HP-35 scientific pocket calculator",
        author = "Hewlett-Packard",
        year = 1972,
        summary = "The first handheld scientific calculator, launched by Hewlett-Packard in 1972 at about $395, which worked in reverse Polish notation.",
        details = "Its logarithms and trigonometric functions were built in, so an engineer no longer had to carry tables or a slide rule. The machine was a favourite of engineers, and it sold in large numbers.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Home video",
        title = "Magnavox Odyssey",
        originalTitle = "Magnavox Odyssey home video game system",
        author = "Magnavox, designed by Ralph Baer",
        year = 1972,
        summary = "The first home video game console, released by Magnavox in 1972, which played simple games on a television with plastic overlays.",
        details = "Ralph Baer designed the system over several years. Its games were simple, and the plastic overlays fixed to the television screen supplied the colour and the playing field.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Storage",
        title = "IBM 3340 Winchester disk",
        originalTitle = "IBM 3340 disk storage with sealed heads and platters",
        author = "IBM",
        year = 1973,
        summary = "IBM's disk drive of 1973, with its heads and platters sealed in one unit, the design that gave sealed hard disks the common name Winchester.",
        details = "The project's code name was Winchester, after the 30-30 rifle, and the name stuck for years. The sealed unit kept dust out, which let the heads fly closer to the disk and store more data on each platter.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Microprocessors",
        title = "Intel 8080",
        originalTitle = "Intel 8080, an 8-bit microprocessor",
        author = "Intel",
        year = 1974,
        summary = "Intel's 8-bit microprocessor of 1974, with about 6,000 transistors, which powered the Altair 8800 and many early microcomputers.",
        details = "Its instruction set was the base of the 8085 and of the Z80, which is why so many machines of the late 1970s share its ideas. The chip ran at about two megahertz.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Microprocessors",
        title = "Motorola 6800",
        originalTitle = "Motorola MC6800, an 8-bit microprocessor",
        author = "Motorola",
        year = 1974,
        summary = "Motorola's 8-bit microprocessor of 1974, a rival to the Intel 8080, with about 4,100 transistors.",
        details = "Its design was the ancestor of the 6809, and it was used in several early machines, including terminals and industrial controllers.",
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "Apple I",
        originalTitle = "Apple Computer Apple I",
        author = "Steve Wozniak and Steve Jobs",
        year = 1976,
        summary = "Steve Wozniak's single-board computer, sold by Apple in 1976 for $666.66 as a kit for hobbyists, with a MOS 6502 processor.",
        details = "Only a few hundred were made. It had no case, keyboard or display of its own, and buyers connected a terminal, which is why the machine is remembered as a board rather than as a computer.",
    ),

    entry(
        databaseId = 8,
        kind = Kind.DEVICE,
        section = "Personal computers",
        title = "TRS-80 Model I",
        originalTitle = "Tandy Radio Shack TRS-80 Model I",
        author = "Tandy Corporation and Radio Shack",
        year = 1977,
        summary = "Radio Shack's first personal computer, released in 1977 at $599, with a Zilog Z80 processor and a keyboard built into the case.",
        details = "It was sold through the chain's own stores, which put a computer on display where ordinary customers could see it. Its screen showed text only, and its disk drive and printer were add-ons.",
    ),
)
