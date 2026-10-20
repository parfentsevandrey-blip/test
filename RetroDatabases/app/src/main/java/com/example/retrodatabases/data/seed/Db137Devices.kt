package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 670 to 680: Greek fire, the naval incendiary of the Byzantines, devised about 673 and used at the Arab siege. */
val db137Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 137,
        kind = Kind.DEVICE,
        section = "Byzantine",
        title = "Greek fire, the naval incendiary whose formula was kept secret, devised about 673 and used against the Arab fleet",
        originalTitle = "Greek fire",
        author = "Kallinikos, named by Theophanes",
        year = 673,
        summary = "Greek fire was a naval incendiary of the Byzantine Empire whose formula was kept secret, and Theophanes names Kallinikos as its inventor about 673.",
        details = "Theophanes places the invention in 672 or 673 and names Kallinikos, an architect from Heliopolis, as the man who devised it. The fire is first put to use in the Arab siege of Constantinople of 674 to 678, and accounts differ: a Syriac chronicle dates the episode to 669 or 670, and historians disagree on whether Kallinikos invented the weapon or improved an older one.",
    ),

)
