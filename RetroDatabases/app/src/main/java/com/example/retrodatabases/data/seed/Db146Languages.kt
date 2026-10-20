package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 580 to 590: Yan Zhitui's Family Instructions of about 589 and the Bugut inscription in Sogdian script, dated from about 582 to 584. */
val db146Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Yan Zhitui's Family Instructions for the Yan Clan, a handbook of learning and conduct written in his last years",
        originalTitle = "Yanshi jiaxun",
        author = "Yan Zhitui",
        year = 589,
        summary = "Yan Zhitui's handbook for his household sets out rules of learning, careful writing and conduct for a scholar family, written in the last years of his life.",
        details = "The work is usually dated to about 589, in the author's last years, but the year of completion is not fixed. Accounts differ on its size, with one translation giving twenty chapters and another source giving twenty-six, so the record does not use a chapter count.",
    ),

    entry(
        databaseId = 146,
        kind = Kind.LANGUAGE,
        section = "Turkic",
        title = "The Bugut inscription, a stone in Sogdian script that honors a Turkic khagan on the Mongolian steppe",
        originalTitle = "Bugut inscription",
        author = "Unnamed writers at a Turkic court",
        year = 582,
        summary = "A stone from the Mongolian steppe bears a Sogdian-script text that honors a Turkic khagan, dated to about 582 with other accounts giving 584.",
        details = "The stone was found at the Bugut site in Ikh-Tamir sum, Arkhangai Province, Mongolia, and three of its faces carry Sogdian script in fair condition. Scholars read the fourth face in different ways, and accounts differ on the date, from about 581 or 582 to 584, so the earlier year is used here.",
        rare = true,
    ),

)
