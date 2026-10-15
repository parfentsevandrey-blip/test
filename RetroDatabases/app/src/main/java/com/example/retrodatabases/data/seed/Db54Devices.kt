package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1500 to 1510: Aldus's italic type, Petrucci's printed music, Leonardo's gears, a half-hour sand-glass, a leather ink ball, Leonardo's flying and distance machines, and a printer's mark. */
val db54Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "Aldus's italic type in the 1501 Virgil",
        originalTitle = "Virgilius in octavo",
        author = "Aldus Manutius",
        year = 1501,
        summary = "Aldus Manutius set a small Virgil at Venice in 1501 in a new italic type, cut by Francesco Griffo, which let a page hold more words than the roman letters of the time.",
        details = "Griffo cut the italic punches for Aldus, and the small octavo Virgil of 1501 is the usual point for the first book set in the type. Printers across Europe copied the italic in the decades that followed.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Music printing",
        title = "Petrucci's music printed with movable type",
        originalTitle = "Harmonice musices Odhecaton A",
        author = "Ottaviano Petrucci",
        year = 1501,
        summary = "Petrucci printed the first book of polyphonic music from movable type at Venice in 1501, a method that let music be copied in many identical books.",
        details = "The Odhecaton gathered songs for several voices, and Petrucci printed the staves and the notes in separate impressions on the same sheet. Earlier music had been printed from woodcut blocks, so the book is named as the first to use movable type for polyphonic music.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Machines",
        title = "Leonardo's gears in the Madrid codex",
        originalTitle = "Codex Madrid",
        author = "Leonardo da Vinci",
        year = 1504,
        summary = "Leonardo's notebook known as the Madrid codex, dated about 1503 to 1505, holds drawings of gear trains and screw mechanisms.",
        details = "The codex is kept in Madrid, and its pages show gears, screws and levers drawn as working mechanisms. The dates are approximate.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The half-hour sand-glass at sea",
        originalTitle = "Ampolleta",
        author = "Spanish and Portuguese pilots",
        year = 1500,
        summary = "The half-hour sand-glass was the usual timekeeper at sea in the sixteenth century, and pilots turned it by hand to measure the watches.",
        details = "Sand-glasses of this kind were the usual clock at sea in the sixteenth century, and the half hour was the unit that pilots counted with them. The glass was turned by hand, so every reading depended on the person who turned it.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The leather ink ball of the printing shop",
        originalTitle = "Ink ball",
        author = "Printing house craftsmen",
        year = 1500,
        summary = "The leather ink ball, a pad of wool covered in leather, inked the type of the hand presses that printed the books of the period.",
        details = "Printers dabbed the balls over the inked type before each impression, and the tool stayed in use throughout the age of the hand press. The date given is a general one, since the tool belonged to every printing house of the period.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Flight",
        title = "Leonardo's flying machine in the Codex on the Flight of Birds",
        originalTitle = "Codice sul volo degli uccelli",
        author = "Leonardo da Vinci",
        year = 1505,
        summary = "Leonardo's notebook on the flight of birds, written about 1505, contains designs for flying machines with wings that a man would work with his arms and legs.",
        details = "The notebook studies how birds hold themselves in the air and sketches the wings and frames of machines that a pilot would work by hand and foot. None of Leonardo's flying designs was built in his lifetime.",
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Measurement",
        title = "Leonardo's distance counter, the odometer",
        originalTitle = "Codex Atlanticus",
        author = "Leonardo da Vinci",
        year = 1504,
        summary = "Leonardo sketched a distance counter on the first folio of the Codex Atlanticus, a machine whose wheel dropped balls into a box as it turned.",
        details = "The gearing turns the count of a ground wheel into a sequence of drops, and the idea is older than Leonardo, going back to a design credited to Heron of Alexandria. The sketch is dated about 1504.",
        rare = true,
    ),

    entry(
        databaseId = 54,
        kind = Kind.DEVICE,
        section = "Printer's marks",
        title = "Aldus's anchor and dolphin device",
        originalTitle = "Aldine device with the motto festina lente",
        author = "Aldus Manutius",
        year = 1502,
        summary = "Aldus Manutius marked his books with an anchor wound about with a dolphin, a printer's device that stood for the motto make haste slowly.",
        details = "The device appears in Aldine books from about 1502, and the motto is a saying that the Roman writer Suetonius attributed to the emperor Augustus. Printers after Aldus copied the idea of a mark that carried a motto.",
    ),

)
