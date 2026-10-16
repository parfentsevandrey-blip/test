package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1430 to 1440: oil glazes at Ghent, notaries at a trial, a motet for a cathedral, a voyage past a cape and a traveller's habit of copying inscriptions. */
val db61Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.PRACTICE,
        section = "Epigraphy",
        title = "Cyriac of Ancona copies inscriptions into his notebooks",
        originalTitle = "Commentarii",
        author = "Cyriac of Ancona",
        year = 1435,
        summary = "Cyriac of Ancona made a habit of copying Greek and Latin inscriptions on his journeys, and his notebooks kept the texts for scholars who could not see the stones.",
        details = "He travelled in the Aegean and in Greece during the 1430s and 1440s, and his notebooks record texts on stones that have since been lost or damaged. Accounts differ on the order of his journeys and on how many inscriptions the notebooks hold.",
        rare = true,
    ),

    entry(
        databaseId = 61,
        kind = Kind.PRACTICE,
        section = "Painting",
        title = "Oil glazes on the Ghent altarpiece, finished in 1432",
        originalTitle = "Het Lam Gods",
        author = "Hubert and Jan van Eyck",
        year = 1432,
        summary = "The Ghent altarpiece, dated 1432, was painted in thin transparent layers of oil, which built up depth and clarity in its colours.",
        details = "Vasari later credited Jan van Eyck with inventing oil painting, and historians now say that painters used oil before him, so the dispute is over what the van Eycks refined rather than what they invented. The altarpiece was begun by Hubert van Eyck, who died in 1426, and its inscription names both brothers.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.PRACTICE,
        section = "Court records",
        title = "The notaries' minutes of Joan of Arc's trial at Rouen, 1431",
        originalTitle = "Proces de condamnation de Jeanne d'Arc",
        author = "Guillaume Manchon, Guillaume Colles and Nicolas Taquel, notaries",
        year = 1431,
        summary = "The notaries of Joan of Arc's trial at Rouen kept minutes of each session in 1431, and the record survives in Latin and in French.",
        details = "The trial ran from January to May 1431, and the notaries wrote down the questions, answers and rulings as they were given. The record was read again at the rehabilitation trial of 1455 to 1456, which set aside the verdict of 1431.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.PRACTICE,
        section = "Music",
        title = "Dufay's Nuper rosarum flores, a motet for the consecration of Florence cathedral",
        originalTitle = "Nuper rosarum flores",
        author = "Guillaume Dufay",
        year = 1436,
        summary = "Dufay's motet Nuper rosarum flores was sung at the consecration of Florence cathedral in 1436, and some scholars read its proportions as those of the dome.",
        details = "The consecration took place in March 1436, and Charles Warren's study of 1973 argued that the motet's sections follow the proportions of the building. Other scholars reject that reading, so the link between the music and the dome remains disputed.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.PRACTICE,
        section = "Navigation",
        title = "Prince Henry's yearly voyages, with Gil Eanes past Cape Bojador in 1434",
        originalTitle = "Cabo Bojador",
        author = "The household of Prince Henry of Portugal, with Gil Eanes",
        year = 1434,
        summary = "In 1434 Gil Eanes sailed past Cape Bojador, a headland that earlier voyages had failed to pass, on one of the yearly voyages sent out from Prince Henry's household.",
        details = "The year 1434 is the one usually given for Eanes's passage, and Zurara's chronicle of Guinea, written in the 1450s, is the main account of these voyages. The passage is usually counted as the first Portuguese round of the cape, and it followed the annual sending of ships from the prince's household.",
    ),

)
