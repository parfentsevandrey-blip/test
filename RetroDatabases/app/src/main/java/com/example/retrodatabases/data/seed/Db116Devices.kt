package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 880 to 890, a placed record: a mechanical model of the heavens credited to Abbas ibn Firnas, placed at 887, the year of his death. */
val db116Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.DEVICE,
        section = "Al-Andalus",
        title = "The mechanical model of the heavens credited to Abbas ibn Firnas in Cordoba",
        originalTitle = "The mechanical model of the heavens credited to Abbas ibn Firnas in Cordoba",
        author = "Abbas ibn Firnas",
        year = 887,
        summary = "A mechanical model of the heavens, credited to Abbas ibn Firnas in later accounts, is placed at 887, the year of his death, because no source dates the device.",
        details = "This is a placed record: the sources do not date the device, and 887 is the year of the anchor, the death of Abbas ibn Firnas as commonly given, not the year of the making. Later writers describe a model of the stars and heavens in his house, and a modern scholarly chapter calls the attribution reported rather than confirmed and suggests an early automaton instead.",
        rare = true,
    ),

)
