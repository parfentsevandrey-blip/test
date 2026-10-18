package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** The Byzantine siphon for Greek fire, placed at the Rus attack on Constantinople of 941, since no dated device of the decade is known. */
val db110Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.DEVICE,
        section = "Byzantium",
        title = "The siphon that projected Greek fire from Byzantine ships in the Rus attack of 941",
        originalTitle = "Greek fire siphon",
        author = "Byzantine naval crews",
        year = 941,
        summary = "Byzantine ships used a siphon to throw Greek fire at the Rus fleet in the attack on Constantinople of 941, a device placed at that year.",
        details = "The sources do not date the siphon itself, so 941 is the placed year: it is the year of the Rus attack on Constantinople, in which the Byzantine navy used Greek fire against the attackers, and not the year the device was made.",
        rare = true,
    ),

)
