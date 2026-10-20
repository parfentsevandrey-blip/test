package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 530 to 540: the ship mills that Belisarius moored on the Tiber in 537, when the Goths had cut Rome's aqueducts. */
val db151Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.DEVICE,
        section = "Italian",
        title = "The ship mills that Belisarius moored on the Tiber during the Gothic siege of Rome, after the Goths cut the aqueducts in 537",
        originalTitle = "ship mills",
        author = "Belisarius, with Procopius as the recorder of the siege",
        year = 537,
        summary = "When the Goths cut the aqueducts of Rome in 537, Belisarius moored boats carrying grinding mills in the Tiber, where the current turned their wheels to make flour.",
        details = "Procopius describes two vessels moored in the current under a bridge arch, each carrying two mills, with a line of further boats that let the current turn each wheel in turn. Opponents tried to wreck the wheels with logs and corpses sent down the river, and iron chains across the water kept the debris away.",
        rare = true,
    ),

)
