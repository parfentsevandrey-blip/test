package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practice of 720 to 730: Yi Xing's survey of 724 and 725, which read the Pole Star and gnomon shadows at sites from Jiaozhou to Lake Baikal. */
val db132Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "Yi Xing's survey of 724 and 725, Pole Star heights and gnomon shadows read at sites from Jiaozhou to Lake Baikal",
        originalTitle = "Survey of the Pole Star and the shadows",
        author = "Yi Xing and the Tang observers",
        year = 724,
        summary = "In 724 and 725 Yi Xing led a survey that read the height of the Pole Star and the summer and winter shadows of gnomons at sites from Jiaozhou to the region south of Lake Baikal.",
        details = "The sites ran from about 17 degrees north to about 50 degrees north, and the readings served to find each site's latitude. Accounts differ on the number of sites, thirteen in one description and twenty gnomons in another.",
        rare = true,
    ),

)
