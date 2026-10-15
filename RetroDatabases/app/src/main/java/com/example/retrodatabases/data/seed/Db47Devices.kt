package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1570 to 1580: Besson's engraved machines, the Strasbourg clock, a planisphere, a surveying instrument, a meridian line, artificial limbs, an assay furnace and a book of simple machines. */
val db47Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Machines",
        title = "Besson's Theatrum of machines and instruments, 1578",
        originalTitle = "Theatrum instrumentorum et machinarum",
        author = "Jacques Besson",
        year = 1578,
        summary = "Besson's Theatrum of 1578 gathered engraved plates of machines and instruments, drawn so that a reader could see how each one was built.",
        details = "Besson, a French mathematician and engineer, published the book at Lyon in 1578. It is a common source for the machine designs of the late sixteenth century, and its plates show the mechanisms in enough detail to copy them.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The astronomical clock of Strasbourg Cathedral, completed in 1574",
        originalTitle = "Astronomische Uhr",
        author = "Conrad Dasypodius and Isaac Habrecht",
        year = 1574,
        summary = "The Strasbourg Cathedral clock was completed in 1574, and its dial carried astronomical and calendar faces.",
        details = "Conrad Dasypodius, a mathematician at Strasbourg, is credited with the design, and Isaac Habrecht with the making, although one record also names Josias Habrecht. The work is dated between about 1572 and 1574, and the clock was built to show the heavens as well as the time.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Planispheres",
        title = "Guidobaldo del Monte's planisphere, the Planisphaerium of 1579",
        originalTitle = "Planisphaerium",
        author = "Guidobaldo del Monte",
        year = 1579,
        summary = "Del Monte's Planisphaerium of 1579 described the planisphere, a flat instrument that shows the sky by stereographic projection.",
        details = "Del Monte was a mathematician of Pesaro, and the book was printed there in 1579. It proves the construction on which the planisphere rests, a projection in which circles on the sphere stay circles on the plane, and the instrument was used for showing the stars for centuries.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "Digges's theodelitus, set out in his Pantometria of 1571",
        originalTitle = "A geometrical practise, named Pantometria",
        author = "Leonard Digges and Thomas Digges",
        year = 1571,
        summary = "The theodelitus, a surveying instrument for measuring angles in the field, is set out in the first part of Pantometria, a book printed in London in 1571.",
        details = "Thomas Digges printed the book in 1571 from the manuscript of his father Leonard, and accounts differ on the year of Leonard's death. The instrument is set out in the part called Longimetra, and accounts differ on how far it was Leonard's own invention, since some later writers credit him with the theodolite itself.",
        rare = true,
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Danti's meridian line in San Petronio, Bologna, of 1575",
        originalTitle = "Meridiana di San Petronio",
        author = "Egnatio Danti",
        year = 1575,
        summary = "Danti's meridian line of 1575 ran across the floor of San Petronio at Bologna, and a hole in the wall let the noon sun mark its position on the line.",
        details = "Danti, a mathematician and cosmographer, laid the line to check the sun's noon position against the calendar in use at the church. The line still shows how the noon sun was used as a measuring device in the sixteenth century.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Surgery",
        title = "Pare's artificial hands and legs in his collected works of 1575",
        originalTitle = "Les oeuvres de M. Ambroise Parree",
        author = "Ambroise Pare",
        year = 1575,
        summary = "Pare's collected works of 1575 describe artificial hands and legs for men who had lost a limb, with drawings of how the hinged parts moved.",
        details = "Pare was surgeon to the kings of France, and a Paris volume of 1575 gathers his writings, which include his descriptions of artificial limbs. Accounts differ on how far his drawings show limbs that were worn in practice.",
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Assaying",
        title = "Ercker's assay furnace in his book on ores, 1574",
        originalTitle = "Beschreibung allerfurnemisten mineralischen Ertz und Bergkwercksarten",
        author = "Lazarus Ercker",
        year = 1574,
        summary = "Ercker's book of 1574 described the furnaces and tools used to assay ores, the tests that found how much silver or gold an ore held.",
        details = "Ercker, the chief assayer for the Bohemian mints, printed the book at Prague in 1574 for miners and assayers who had to test their ores. The book describes the furnaces and tools of assaying in some detail, and the assay furnace is the device this record names.",
        rare = true,
    ),

    entry(
        databaseId = 47,
        kind = Kind.DEVICE,
        section = "Simple machines",
        title = "Del Monte's Mechanicorum, the book of simple machines, 1577",
        originalTitle = "Mechanicorum liber",
        author = "Guidobaldo del Monte",
        year = 1577,
        summary = "Del Monte's Mechanicorum of 1577 treated the simple machines, such as the lever and the pulley, by the rules of balance and of the centre of gravity.",
        details = "The book was printed at Pesaro in 1577, and it is a Renaissance revival of the rules of Archimedes applied to machines. Its treatment of the centre of gravity is among its best-known parts, and the work is a standard source for the mechanics of the period.",
        rare = true,
    ),

)
