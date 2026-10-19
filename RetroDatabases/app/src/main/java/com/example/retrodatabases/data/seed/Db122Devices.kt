package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 820 to 830, a placed record: the observing instruments of al-Ma'mun's observatory in Baghdad, placed at 828, the year its observations began. */
val db122Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.DEVICE,
        section = "Baghdad",
        title = "The observing instruments of al-Ma'mun's observatory in the Shammasiyya quarter of Baghdad",
        originalTitle = "Shammasiyya observatory",
        author = "Astronomers of the caliph al-Ma'mun",
        year = 828,
        summary = "Instruments for observing the sun, moon and planets were in use at the Shammasiyya observatory in Baghdad from about 828, under the caliph al-Ma'mun.",
        details = "This is a placed record. The sources do not date these instruments; 828 is the year the observations began, the anchor used for placement, not the year of the making. Accounts differ on the start, with some giving 828 to 829 and others 829 to 830, and al-Biruni reports that the first batch of observations was discarded because its solstice readings varied too widely.",
        rare = true,
    ),

)
