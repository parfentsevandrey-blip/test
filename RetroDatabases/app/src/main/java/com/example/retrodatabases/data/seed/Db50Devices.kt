package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1540 to 1546: paper volvelles, a globe, a fine scale, a matchlock, a pinhole room, a compass needle, a ship's gear and a gunner's quadrant. */
val db50Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Instruments",
        title = "Apian's paper volvelles in the Astronomicum Caesareum",
        originalTitle = "Astronomicum Caesareum",
        author = "Peter Apian",
        year = 1540,
        summary = "Apian's Astronomicum Caesareum of 1540 printed paper wheels that turn on one another, so that a reader could find the places of the sun and moon by turning them.",
        details = "The book was printed at Ingolstadt in 1540 and dedicated to the emperor Charles V, and its movable paper instruments are its best-known feature. A reader turns the wheels to a date and reads the answer against fixed scales printed on the page.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Globes",
        title = "Mercator's terrestrial globe of 1541",
        originalTitle = "Globus terrestris",
        author = "Gerard Mercator",
        year = 1541,
        summary = "Mercator made a terrestrial globe in 1541 at Louvain, a ball on which the lands and seas were drawn for students of geography.",
        details = "The globe was made at Louvain in 1541, when Mercator was an instrument maker and mapmaker in the Low Countries. He later made a celestial globe and his world map of 1569, which uses the projection that bears his name.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Measurement",
        title = "Nunes's nonius, the fine scale of De crepusculis",
        originalTitle = "De crepusculis",
        author = "Pedro Nunes",
        year = 1542,
        summary = "Nunes described in 1542 a set of concentric arcs with graded divisions, which let an observer read fractions of a degree from a sighting.",
        details = "The treatise was printed at Lisbon in 1542, and accounts differ on how many arcs and divisions the device carried. Its modern form is usually credited to Pierre Vernier, who described a sliding scale in 1631, so the two inventions are often linked.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Firearms",
        title = "The matchlock gun at Tanegashima, the first in Japan",
        originalTitle = "Tanegashima teppo",
        author = "Portuguese traders and Japanese smiths",
        year = 1543,
        summary = "Portuguese traders brought matchlock guns to the island of Tanegashima in 1543, and Japanese smiths soon learned to copy the lock.",
        details = "The arrival is usually dated to 1543, and accounts differ on the exact year of the first landing. The island's smiths were soon making their own guns, and the copies spread through the Japanese provinces.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Gemma Frisius's darkened room for watching an eclipse",
        originalTitle = "De radio astronomico et geometrico",
        author = "Gemma Frisius",
        year = 1545,
        summary = "Gemma Frisius's book of 1545 described how a pinhole in a darkened room throws an image of the sun, a safe way to watch an eclipse.",
        details = "The book was printed at Antwerp in 1545, and it describes the pinhole image of a solar eclipse. The principle is the same as that of the camera obscura, so the book is often named among the earliest printed accounts of it.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Magnetism",
        title = "Hartmann's letter on the compass needle, dated 1544",
        originalTitle = "Letter to Duke Albert",
        author = "Georg Hartmann",
        year = 1544,
        summary = "Georg Hartmann, a vicar at Nuremberg, is credited in older accounts with an observation of the magnetic needle in 1544.",
        details = "His surviving record is a letter to Duke Albert, which was not printed until the nineteenth century, and accounts differ on whether it concerns the dip of the needle or its declination from true north. Encyclopedias give 1544 for the observation, but the letter's own date and exact subject are disputed.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The Mary Rose's ship's gear, lost in 1545",
        originalTitle = "Mary Rose",
        author = "Henry VIII's navy",
        year = 1545,
        summary = "The Mary Rose, a warship of Henry VIII's navy, sank off Portsmouth in July 1545 with the gear of its day aboard, and the wreck was raised in 1982.",
        details = "The ship sank in July 1545 during a battle with a French fleet off Portsmouth, and it was raised from the Solent in 1982. Among the objects brought up were instruments used at sea, which show the tools that English sailors carried in the 1540s.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.DEVICE,
        section = "Gunnery",
        title = "Tartaglia's gunner's quadrant, described in his Quesiti",
        originalTitle = "Quesiti et inventioni diverse",
        author = "Niccolo Tartaglia",
        year = 1546,
        summary = "Tartaglia described a gunner's quadrant for setting the angle of a cannon, and accounts differ on whether he invented the instrument or improved an older one.",
        details = "His book of 1546, the Quesiti et inventioni diverse, is a collection of questions and answers that includes his work on artillery. An earlier form of the instrument is older than his book, so the claim to invention is disputed.",
    ),

)
