package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1140 to 1150: one verified instrument, the organistrum shown in a pilgrimage manuscript of about 1140. The decade has few dated devices, so this file is short. */
val db90Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.DEVICE,
        section = "Music",
        title = "The organistrum, a bowed drone instrument shown in the Codex Calixtinus",
        originalTitle = "Organistrum",
        author = "Compilers of the Codex Calixtinus at Santiago de Compostela",
        year = 1140,
        summary = "The organistrum, a crank-turned instrument that bows a wheel against strings, is shown in the Codex Calixtinus, a Santiago manuscript of about 1140.",
        details = "The manuscript's pictures are dated around 1140, and a carving on the Portico de la Gloria at Santiago is a later picture of the same instrument. A treatise, Quomodo organistrum construatur, is attributed to Odo of Cluny, who died in 942, but the attribution is doubtful and the text survives only in a far later copy.",
        rare = true,
    ),

)
