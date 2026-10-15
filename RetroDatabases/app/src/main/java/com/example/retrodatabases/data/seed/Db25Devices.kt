package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1790 to 1800: a speaking machine, an automaton, a cipher wheel, a printing method, telegraph parts, a gas lamp and a decimal clock. */
val db25Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Speech",
        title = "Kempelen's speaking machine",
        originalTitle = "Mechanismus der menschlichen Sprache nebst der Beschreibung seiner sprechenden Maschine (1791)",
        author = "Wolfgang von Kempelen",
        year = 1791,
        summary = "A machine of bellows, reeds and a flexible mouth and nose, worked by hand, that spoke words and short phrases by shaping a tube for each sound.",
        details = "Kempelen published his description in Vienna in 1791, after years of work on the sounds of human speech. The machine used a bellows as the lungs, reeds as the vocal cords, and movable parts to shape the vocal tract for each sound.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "Maillardet's draughtsman-writer",
        originalTitle = "Draughtsman-writer automaton (about 1800)",
        author = "Henri Maillardet",
        year = 1800,
        summary = "A brass figure that drew four pictures and wrote three short poems, its actions set by cams and levers inside its body.",
        details = "Museum accounts date the automaton to about 1800, though some give 1798 and others 1810, and a London advertisement of 1807 shows it on display. It reached the Franklin Institute in Philadelphia in 1928 in ruined pieces and was restored and shown again in the 2000s.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Ciphers",
        title = "Jefferson's wheel cipher",
        originalTitle = "Wheel cipher, a spindle of cylinders (about 1795)",
        author = "Thomas Jefferson",
        year = 1795,
        summary = "A spindle of cylinders, each with its alphabet shuffled, encoded a message by spelling it along one row and reading the letters from another row.",
        details = "Jefferson described the device in the early 1790s, and many sources date its design to 1795. Monticello describes a spindle of 36 cylinders and says it may never have been built, while some secondary sites give 26 wheels.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Lighting",
        title = "Lebon's thermolamp",
        originalTitle = "Thermolampe, patent of 1799",
        author = "Philippe Lebon",
        year = 1799,
        summary = "A lamp that burned gas made by heating wood in a closed vessel, patented by Philippe Lebon in 1799 for light and heat.",
        details = "The patent of 1799 covered the lamp and the process of making gas from wood. Lebon's design is usually counted among the early forms of gas lighting.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Didot's stereotype plates",
        originalTitle = "Stereotype casting of printing plates (1797)",
        author = "Firmin Didot",
        year = 1797,
        summary = "A solid printing plate was cast from a mould taken from the set type, so the type could be reused while the plate was printed.",
        details = "Firmin Didot introduced stereotyping in France about 1797, casting each plate in a mould taken from the composed page. William Ged in Scotland had tried stereotyping earlier, in the 1730s, and the French method is dated here to 1797.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Telegraphs",
        title = "Chappe's semaphore arms",
        originalTitle = "Arms and regulator of the optical telegraph (1792)",
        author = "Claude Chappe",
        year = 1792,
        summary = "Two pivoting arms and a central regulator, moved by ropes and levers, formed the signs that each tower displayed for the next tower to read.",
        details = "Chappe tested the arms in 1791 and 1792 before the Paris to Lille line was built. Telegraphers read the positions through telescopes from the next tower, and the regulator set the frame for each sign.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Telegraphs",
        title = "Admiralty shutter station",
        originalTitle = "Shutter telegraph frame (about 1796)",
        author = "British Admiralty",
        year = 1796,
        summary = "A frame of shutters on a tower, opened and shut in patterns that a telescope on the next station could read.",
        details = "The British Admiralty used shutter stations of this kind for naval messages in the mid-1790s, and the record dates the first line to about 1796. Each shutter could be open or shut, so the number of distinct patterns set the size of the code.",
        rare = true,
    ),

    entry(
        databaseId = 25,
        kind = Kind.DEVICE,
        section = "Time",
        title = "Decimal clock dial",
        originalTitle = "Decimal dial for the decimal day (1793)",
        author = "Paris clockmakers",
        year = 1793,
        summary = "A clock face divided into ten hours of a hundred minutes each, made for the decimal day that the Convention adopted in 1793.",
        details = "The Convention ordered decimal time in 1793, so that a day had ten hours and each hour a hundred minutes. Some clockmakers built clocks and dials to the plan, and the system was given up in the years after 1795.",
        rare = true,
    ),
)
