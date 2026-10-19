package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Device of 831: the marble quadrant of Mount Qasiyun near Damascus, a placed record dated by al-Ma'mun's second observing program. */
val db121Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.DEVICE,
        section = "Syria",
        title = "The marble quadrant of Mount Qasiyun near Damascus, in al-Ma'mun's observing program",
        originalTitle = "The marble quadrant of Mount Qasiyun near Damascus, in al-Ma'mun's observing program",
        author = "Khalid ibn Abd al-Malik al-Marwarrudhi and al-Ma'mun's observers",
        year = 831,
        summary = "A marble quadrant on Mount Qasiyun near Damascus, which al-Biruni describes with an inner radius of five metres, is linked to al-Ma'mun's second observing program.",
        details = "This is a placed record: no date is given for the quadrant itself, so 831 is the year the second observing program on Mount Qasiyun began, and accounts give that program as running from 831 to 832, from 831 to 833 or from 832 to 833. Al-Biruni, writing later, gives the inner radius of a marble quadrant at the site as five metres, and the program's instruments also included an armillary sphere, a mural quadrant, a gnomon about five metres tall and an azimuthal quadrant.",
        rare = true,
    ),

)
