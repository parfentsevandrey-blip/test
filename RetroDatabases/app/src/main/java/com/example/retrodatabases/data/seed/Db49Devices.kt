package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1550 to 1560: Cardano's gimbal and cipher grille, della Porta's darkened room and Agricola's mine machines. */
val db49Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Suspensions",
        title = "Cardano's gimbal in De subtilitate",
        originalTitle = "De subtilitate",
        author = "Girolamo Cardano",
        year = 1550,
        summary = "Cardano's suspension of nested rings, described in 1550, keeps a container level while its frame turns.",
        details = "The book was printed at Nuremberg in 1550, and it gave the rings as a way of keeping a container level. Earlier Hellenistic and Han Chinese examples of suspended containers are often cited, so accounts differ on the priority of the idea, while the name gimbal came later.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Ciphers",
        title = "Cardano's cipher grille",
        originalTitle = "De subtilitate",
        author = "Girolamo Cardano",
        year = 1550,
        summary = "Cardano's grille of 1550 hid a message in a card of holes, so that only a reader with the same card could see the letters in their order.",
        details = "The grille appears in De subtilitate, the same book that describes his suspension, and it gave its name to the Cardan grille. The method needs a card with holes in a fixed pattern, turned to four positions over a square of text so that every cell is read once.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Music",
        title = "Vicentino's archicembalo, a keyboard with extra keys",
        originalTitle = "L'antica musica ridotta alla moderna prattica",
        author = "Nicola Vicentino",
        year = 1555,
        summary = "Vicentino's archicembalo, set out in his treatise of 1555, was a keyboard with extra keys for intervals smaller than the usual semitone.",
        details = "The treatise was printed at Rome in 1555, and accounts differ on the number of keys, with many sources giving 31 to the octave. The surviving keyboard on this system is credited to Trasuntino and dated 1606, so the early instruments are known mainly from the printed description.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Mining",
        title = "Agricola's mine machines in De re metallica",
        originalTitle = "De re metallica",
        author = "Georgius Agricola",
        year = 1556,
        summary = "Agricola's De re metallica of 1556 illustrated the machines of mining, such as pumps and hoists worked by water and by treadwheels.",
        details = "The book was printed at Basel in 1556 with woodcuts of the machines, and Agricola described the mines he knew in Saxony and Bohemia. Its woodcuts were copied by later writers on mining, and the machines it shows were built to lift water and ore.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "Juanelo Turriano's Cristallino, a planetary clock",
        originalTitle = "Cristallino",
        author = "Juanelo Turriano",
        year = 1552,
        summary = "Juanelo Turriano's Cristallino, a planetary clock made for Emperor Charles V, is counted among the most admired works of its maker.",
        details = "One timeline dates the work on the Cristallino to 1552 to 1555, before Turriano's crossing to the Iberian Peninsula in 1556, while other accounts tie it to the emperor's years at Yuste. Accounts differ on these dates, so the year given is the start of the timeline's dates.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Globes",
        title = "Mercator's celestial globe of 1551",
        originalTitle = "Celestial globe",
        author = "Gerard Mercator",
        year = 1551,
        summary = "Mercator's celestial globe of 1551, made at Louvain ten years after his terrestrial globe of 1541, shows the stars on a sphere.",
        details = "Oxford's museum of the history of science and an exhibit at Lausanne both describe the globe, and the Lausanne exhibit calls it Mercator's only astronomical work. Its terrestrial companion of 1541 maps the earth, so the pair shows the earth and the heavens as one maker's two globes.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Schissler's gilt-brass compendium of 1556",
        originalTitle = "Gilt-brass compendium",
        author = "Schissler, instrument maker of Augsburg",
        year = 1556,
        summary = "Schissler's gilt-brass compendium of 1556, made at Augsburg, is signed and dated and carries a geographical astrolabe engraved on a rotating disc.",
        details = "The compendium was made at Augsburg, not Nuremberg, and the shop there opened in the early 1550s. A Sotheby's lot description records the instrument as signed and dated 1556, with the geographical astrolabe engraved on a disc that turns.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.DEVICE,
        section = "Astrolabes",
        title = "Gemini's London astrolabe of 1559",
        originalTitle = "London astrolabe",
        author = "Thomas Gemini",
        year = 1559,
        summary = "Gemini's London astrolabe of 1559 follows Gemma Frisius's universal design, so it adapts an existing design rather than inventing a new one.",
        details = "Gemini was Flemish-born, originally named Lambrit, and his London workshop dates from about 1540. The 1559 instrument at Oxford's museum was once described as made for Elizabeth I, but that attribution has shifted, so accounts differ on who commissioned it.",
    ),

)
