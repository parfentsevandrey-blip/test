package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1580 to 1590: balances, a stocking frame, a bookwheel, a dip needle, a surveying jewel, a bead chain, a press in Lima and a scrying mirror. */
val db46Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Balances",
        title = "Galileo's hydrostatic balance, La Bilancetta",
        originalTitle = "La Bilancetta",
        author = "Galileo Galilei",
        year = 1586,
        summary = "Galileo's small hydrostatic balance, written about 1586, weighs a solid in air and in water so that its weight in water can be read off.",
        details = "Galileo wrote La Bilancetta about 1586, and it describes a small balance for finding the weight of a body in water. Accounts differ on the date of its first printing, which is often given as the Bologna edition of his works of the mid-1650s, after his death, while one listing claims an earlier printing.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Knitting",
        title = "William Lee's stocking frame",
        originalTitle = "The stocking frame",
        author = "William Lee",
        year = 1589,
        summary = "Lee's stocking frame of 1589 was one of the first machines to knit stockings, forming each loop by a mechanism rather than by hand.",
        details = "Most accounts place Lee's frame at Calverton, near Nottingham, and date it to 1589. Queen Elizabeth is said to have refused him a patent for fear that the machine would take work from hand knitters, and accounts differ on the year of that refusal.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Books",
        title = "Ramelli's bookwheel",
        originalTitle = "Le diverse et artificiose machine",
        author = "Agostino Ramelli",
        year = 1588,
        summary = "Ramelli's book of machines, printed in 1588, shows a revolving wheel of reading desks that brings several open books to one reader in turn.",
        details = "Ramelli printed Le diverse et artificiose machine in Paris in 1588, and its plates include the wheel of desks. The wheel let one reader turn among many books without leaving the spot.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Magnetism",
        title = "Norman's dip needle",
        originalTitle = "The Newe Attractive",
        author = "Robert Norman",
        year = 1581,
        summary = "Norman's book of 1581 reported that a magnetized needle dips toward the ground, and it described an instrument for measuring the dip.",
        details = "The Newe Attractive was printed in London in 1581, and Norman is usually credited with discovering the dip of the magnetic needle. His instrument balanced a needle on a horizontal axis so that the dip could be read from a scale.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Blagrave's Mathematical Jewel",
        originalTitle = "The Mathematical Jewel",
        author = "John Blagrave",
        year = 1585,
        summary = "Blagrave's Mathematical Jewel of 1585 described an instrument for problems of the sphere and of surveying, and it was written as an English guide for practical men.",
        details = "The book was published in London in 1585 and explains how to make and use the instrument it describes. It is one of the English instrument books of the later sixteenth century.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Mechanics",
        title = "Stevin's endless chain of beads",
        originalTitle = "De Beghinselen der Weeghconst",
        author = "Simon Stevin",
        year = 1586,
        summary = "Stevin's endless chain of beads, hung over a triangle in his statics of 1586, proves that a weight on a slope is held by a force in the ratio of the slope's height to its length.",
        details = "Stevin argued that a closed chain of beads hung over a triangle cannot move, so the weights on its two sides must be in the ratio of their heights to their lengths. The argument appears in De Beghinselen der Weeghconst, which was printed at Leiden in 1586.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The first printing press in Lima",
        originalTitle = "The first printing press in Lima",
        author = "Antonio Ricardo",
        year = 1584,
        summary = "The first printing press in Lima came into use in 1584 and printed a Christian doctrine for the peoples of the Andes.",
        details = "Antonio Ricardo is named as the printer, and the Doctrina christiana of 1584 is usually given as the first book printed in Lima. The press was an early step in printing in Spanish South America, and the first Lima books were religious texts.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.DEVICE,
        section = "Scrying",
        title = "Dee's obsidian mirror",
        originalTitle = "Dee's obsidian mirror",
        author = "John Dee",
        year = 1582,
        summary = "Dee's black obsidian mirror, of Mexican origin, was used in scrying sessions in which Edward Kelley reported visions in the early 1580s.",
        details = "The mirror is now in the British Museum, and Dee's sessions with Kelley are recorded in his diaries from 1582. Accounts differ on how Dee came to own the mirror, so the record names only its use.",
    ),

)
