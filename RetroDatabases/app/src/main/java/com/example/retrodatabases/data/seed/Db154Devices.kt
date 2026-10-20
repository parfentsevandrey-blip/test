package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 500 to 510: the south-pointing chariot attributed to Zu Chongzhi, placed at 500, the year of his death. */
val db154Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.DEVICE,
        section = "Chinese",
        title = "The south-pointing chariot attributed to Zu Chongzhi, a cart whose figure kept its arm pointing south as the cart turned",
        originalTitle = "Zhinan che",
        author = "Zu Chongzhi, according to later Chinese accounts",
        year = 500,
        summary = "Later Chinese accounts credit Zu Chongzhi with a south-pointing chariot, a cart whose figure kept its arm pointing south however the cart turned.",
        details = "No source dates this chariot, so 500 is a placed year: it is the year Zu Chongzhi, who lived from 429 to 500, died, and it is the anchor for the device, not the date of its making. The gearing that kept the figure steady is described only in later accounts, and historians disagree about how it worked.",
        rare = true,
    ),

)
