package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 520 to 530: the ivory consular diptych of 521 that announced Justinian's consulship. */
val db152Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.DEVICE,
        section = "Byzantine",
        title = "The ivory consular diptych of 521 that announced Justinian's consulship, two panels now in the Metropolitan Museum of Art",
        originalTitle = "Diptychum consulare",
        author = "Unknown ivory carver at Constantinople",
        year = 521,
        summary = "Two carved ivory panels made at Constantinople in 521 announced Justinian's consulship, and they now form one diptych in New York.",
        details = "A new consul gave out such diptychs to mark the appointment, and the panels may also have invited their recipients to the spectacles that the consul gave in the hippodrome at Constantinople. Justinian held the consulship in 521, about six years before he became emperor, and the panels are dated to that year.",
        rare = true,
    ),

)
