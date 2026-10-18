package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 994 and 996: al-Khujandi's large instrument at Rayy, and Gerbert's armillary sphere, placed at the year of Richer's account. */
val db105Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.DEVICE,
        section = "Rayy",
        title = "The large instrument at Rayy with which al-Khujandi took meridian readings in 994",
        originalTitle = "The large instrument at Rayy with which al-Khujandi took meridian readings in 994",
        author = "Abu Mahmud al-Khujandi",
        year = 994,
        summary = "Al-Khujandi used a large instrument at Rayy in 994 to take meridian readings near the solstices, and from them he found a tilt of the ecliptic of 23 degrees 32 minutes 19 seconds.",
        details = "The same readings gave him a latitude of 35 degrees 34 minutes 38.45 seconds for Rayy. Modern biographies judge the tilt about two arc-minutes too low, and the sources differ on the cause, since one account blames the building for settling between the solstices, while al-Biruni put the fault in the aperture, which sank under the instrument's weight.",
        rare = true,
    ),

    entry(
        databaseId = 105,
        kind = Kind.DEVICE,
        section = "Reims",
        title = "Gerbert's armillary sphere, one of the teaching instruments at Reims",
        originalTitle = "Gerbert's armillary sphere, one of the teaching instruments at Reims",
        author = "Gerbert of Aurillac",
        year = 996,
        summary = "Gerbert of Aurillac built some armillary spheres for teaching at Reims, and an account of his four teaching tools from about 996 names them.",
        details = "The sources do not date the sphere itself, so 996 is a placed year: the approximate date of Richer's history, which lists two armillary spheres among the four teaching tools Gerbert used at Reims. The year belongs to that account and not to the making of the sphere, which is undated.",
        rare = true,
    ),

)
