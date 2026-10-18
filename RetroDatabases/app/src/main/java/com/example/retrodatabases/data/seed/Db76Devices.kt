package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1280 and 1288: Guo Shoujing's simplified armillary sphere and a bronze hand cannon from the Heilongjiang frontier. */
val db76Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Guo Shoujing's simplified armillary sphere, a Yuan instrument for the sky",
        originalTitle = "Jianyi",
        author = "Guo Shoujing",
        year = 1280,
        summary = "Guo Shoujing's simplified armillary sphere, a Yuan instrument with a simpler frame of rings than earlier designs, was made for the observations behind the new calendar.",
        details = "A Chinese science museum gives its date as 1276, the year the calendar work began, while another source puts the instrument at about 1280, and the calendar itself was finished in 1280. The surviving evidence about which of Guo's instruments were made when is thin, so the year given here is an approximation.",
        rare = true,
    ),

    entry(
        databaseId = 76,
        kind = Kind.DEVICE,
        section = "Firearms",
        title = "The Heilongjiang hand cannon, an early bronze gun found near Harbin",
        originalTitle = "Heilongjiang shouchong",
        author = "Unknown makers of the Jin and Yuan frontier",
        year = 1288,
        summary = "The Heilongjiang hand cannon is a small bronze gun found near Harbin that is counted among the earliest surviving firearms, and its date of about 1288 is an estimate.",
        details = "No inscribed date is known, so the date comes from the find context, and the gun is linked with bronze work of the Jin period. It was found in 1970 and is kept at the Heilongjiang Provincial Museum in Harbin, and accounts differ on how early it is.",
        rare = true,
    ),

)
