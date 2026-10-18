package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1011: a dark chamber in which a pinhole throws an image, as described in Ibn al-Haytham's Book of Optics. */
val db103Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Ibn al-Haytham's dark chamber, a room with a pinhole that throws an image on its wall",
        originalTitle = "Ibn al-Haytham's dark chamber, a room with a pinhole that throws an image on its wall",
        author = "Ibn al-Haytham",
        year = 1011,
        summary = "Ibn al-Haytham describes a darkened room with one small opening, where light from outside throws an inverted image on the opposite wall, in the Book of Optics.",
        details = "Earlier writers had noted images passing through small holes, but his description of the room is among the fullest early ones, and historians differ on how far it anticipates the later camera obscura. The Book of Optics is usually dated 1011 to 1021, and the record gives the year the writing began.",
    ),

)
