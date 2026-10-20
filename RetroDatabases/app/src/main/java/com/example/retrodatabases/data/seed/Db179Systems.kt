package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 250 to 260: Gallienus's mobile cavalry reform, placed at 260, and the Gallic Empire of Postumus. */
val db179Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.SYSTEM,
        section = "Army",
        title = "Gallienus's mobile cavalry reform of the 260s",
        originalTitle = "Gallienic reform",
        author = "Gallienus and his commanders",
        year = 260,
        summary = "Gallienus reorganized the field army around mobile horse units, a change that historians date loosely to the 260s and that is placed here at 260.",
        details = "The sources do not date the reform to a year, so 260 is the placed year: it is the year Gallienus became sole ruler after his father Valerian was taken, the anchor for the reform and not the date of the reform itself. Some historians link the change to the fighting at Milan in 268, and modern scholars question how far the older account of the reform holds up.",
        rare = true,
    ),

    entry(
        databaseId = 179,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Gallic Empire of Postumus, a breakaway Roman state from 260",
        originalTitle = "Imperium Galliarum",
        author = "Postumus and the Gallic army",
        year = 260,
        summary = "Postumus, a commander on the Rhine frontier, set up a Gallic Empire in 260 that held Gaul, Britain and Spain for fourteen years.",
        details = "Postumus broke with Gallienus after Valerian's capture, and 260 is the usual date of the break. The Gallic Empire struck its own coins, and it ended when Aurelian won it back in 274.",
    ),

)
