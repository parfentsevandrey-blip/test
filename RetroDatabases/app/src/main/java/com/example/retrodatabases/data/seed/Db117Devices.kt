package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 870 to 880, a placed record: the vertical windmill of Sistan, placed at 873, the year of Ya'qub ibn al-Layth's takeover of Nishapur. */
val db117Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.DEVICE,
        section = "Sistan",
        title = "The vertical windmills of Sistan, with sails set around an upright shaft",
        originalTitle = "Asiyab",
        author = "Unknown millwrights of Sistan",
        year = 873,
        summary = "Vertical windmills with sails set around an upright shaft were used in Sistan, and later Arabic and Persian writers describe mills of this kind there.",
        details = "This is a placed record: the first use of these mills is not dated, so 873 is only the year of an anchor, Ya'qub ibn al-Layth's seizure of Nishapur, and not the year of any mill. Sistan was the home of Ya'qub's Saffarid state, and the mills are placed there because the anchor belongs to the same ruler.",
        rare = true,
    ),

)
