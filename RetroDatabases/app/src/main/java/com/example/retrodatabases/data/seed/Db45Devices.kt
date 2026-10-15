package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1590 to 1600: Davis's backstaff, Hondius's celestial globe, Blagrave's staff, Clavius's astrolabe, Tycho's instruments, a Lyon clock, a crank sawmill and the suanpan. */
val db45Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Davis's backstaff in The Seamans Secrets",
        originalTitle = "The Seamans Secrets",
        author = "John Davis",
        year = 1594,
        summary = "Davis's backstaff, described in The Seamans Secrets of 1594, measured the sun's altitude with the observer's back to the sun, and it came into wide use at sea.",
        details = "The instrument is described in Davis's book of 1594, and accounts differ on how far Davis devised the backstaff and how far he only described it. Its shadow principle let a navigator read the sun's height from a scale without looking into the sun, and sea use of the backstaff lasted into the eighteenth century.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Celestial globes",
        title = "Hondius's celestial globe of 1598",
        originalTitle = "Celestial globe",
        author = "Jodocus Hondius",
        year = 1598,
        summary = "Hondius's celestial globe of 1598 showed twelve new southern constellations that the Dutch had drawn from their voyage observations.",
        details = "Accounts link the new southern figures to the star observations of Pieter Keyser on the Dutch voyages, and the globe is one of the first places where many of them appear. Accounts differ on how many of the twelve first appear on this globe, and earlier globes give other figures.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Staffs",
        title = "Blagrave's Familiar Staff for heights and distances",
        originalTitle = "Baculum familiare",
        author = "John Blagrave",
        year = 1590,
        summary = "Blagrave's Baculum familiare of 1590 described a staff that gave heights, distances and depths from sights along its arms, with no arithmetic needed in the field.",
        details = "A London edition of 1590 is recorded, printed by Hugh Jackson, and the staff served both as a walking aid and as a measuring instrument. The book also claimed uses for gunners and for military work, and the record gives these as the book's own claims.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Astrolabes",
        title = "Clavius's astrolabe in the Astrolabium",
        originalTitle = "Astrolabium",
        author = "Christoph Clavius",
        year = 1593,
        summary = "Clavius's Astrolabium of 1593 explained how to use the astrolabe for time, heights and the sun's place, and it served Jesuit students as a textbook of the instrument.",
        details = "The book was printed at Rome in 1593 and treats the older instrument, with its plates and rete, as a tool for problems of the sphere. Clavius was the Jesuit mathematician of the Roman college, and the book is a companion to his other mathematical writings.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Tycho's instruments in the Mechanica of 1598",
        originalTitle = "Astronomiae instauratae mechanica",
        author = "Tycho Brahe",
        year = 1598,
        summary = "Tycho's Mechanica of 1598 illustrated the instruments of his Uraniborg observatory with their sizes and scales, so that other astronomers could build them.",
        details = "The book was printed at Wandsbek in 1598, after Tycho had left the island of Hven in 1597, and it describes the instruments used at Uraniborg in detail. Tycho argued that careful making and graduation of instruments reduced errors, and the plates record the sizes and the dividing lines.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "The Lyon cathedral clock restored in 1598",
        originalTitle = "Horloge astronomique de Lyon",
        author = "Hugues Levet and Lippius of Basel",
        year = 1598,
        summary = "The astronomical clock of Lyon cathedral was restored in 1598 with the help of a scholar from Basel, so that its dial showed the sky and the calendar once more.",
        details = "Accounts differ on whether 1598 marks the building of the clock or the restoration of an older one, and the more detailed sources name Hugues Levet, a Lyon clockmaker, as the repairer. A clock is recorded in the cathedral from the fourteenth century, so the 1598 work belongs to a longer history.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Sawmills",
        title = "Corneliszoon's crank sawmill, patented in 1593",
        originalTitle = "Windzaag",
        author = "Cornelis Corneliszoon van Uitgeest",
        year = 1593,
        summary = "Corneliszoon van Uitgeest's crank sawmill, patented in 1593, turned a windmill's rotation into the up-and-down strokes of a saw.",
        details = "The crankshaft patent is dated December 1593, while the first raft-mounted mill is dated 1594, and accounts differ on whether that first mill stood at Zaandam or at Alkmaar. The crank was an old part, and the record gives his combination of windmill and crank as the invention.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.DEVICE,
        section = "Bead frames",
        title = "The suanpan in Cheng Dawei's arithmetic of 1592",
        originalTitle = "Suanfa tongzong",
        author = "Cheng Dawei",
        year = 1592,
        summary = "Cheng Dawei's arithmetic of 1592 teaches calculation on the suanpan, the Chinese bead frame, with rules for its calculations.",
        details = "The book was printed in 1592 and is one of the large Ming arithmetic books that describe bead-frame methods in detail. The frame is older than the book, so the record takes the book as the dated form of that description.",
    ),

)
