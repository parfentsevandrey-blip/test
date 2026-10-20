package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 240 to 248: Wang Bi's commentary on the Changes, the Zhengshi stone classics in three scripts and Origen's reply to Celsus. */
val db180Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Wang Bi's commentary on the Book of Changes, written in his last decade",
        originalTitle = "Zhouyi zhu",
        author = "Wang Bi",
        year = 240,
        summary = "Wang Bi's commentary on the Book of Changes explains the hexagram texts by general principles rather than by the image and number methods of the Han, and later readers made it the standard reading of the Changes.",
        details = "Wang Bi lived from 226 to 249, so the commentary falls in the last decade of his short life, and 240 is a round date because the year of composition is not fixed. He also wrote a commentary on the Laozi, and both works are central to the learning later called xuanxue, the study of the dark and the mysterious. The Tang official edition of the Changes used his commentary, so his explanations reached later readers in that form.",
    ),

    entry(
        databaseId = 180,
        kind = Kind.LANGUAGE,
        section = "Stone",
        title = "The Zhengshi stone classics, Wei texts cut in three scripts",
        originalTitle = "Zhengshi shijing",
        author = "The Wei state",
        year = 241,
        summary = "The Zhengshi stone classics were texts of the Documents and the Spring and Autumn cut on stone in the Wei state, each glyph shown in ancient, small seal and clerical script.",
        details = "The set was carved in the Zhengshi reign period of 240 to 249, and 241 is the year given for it. It carried part of the Documents and the Spring and Autumn Annals with part of the Gongyang commentary, and only a few fragments survive, among them a Spring and Autumn slab held in the Luoyang Museum. Accounts differ on when the stones came back to light, between the Qing period and the 1920s.",
        rare = true,
    ),

    entry(
        databaseId = 180,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Origen's reply to Celsus, an eight-book Greek defence of the Christian faith",
        originalTitle = "Kata Kelsou",
        author = "Origen",
        year = 248,
        summary = "Origen's Against Celsus answers, point by point, the attack that the critic Celsus made on Christianity, and it is one of the fullest surviving replies of its kind.",
        details = "Origen wrote it in the 240s, and 248 is the usual date for its completion, written at the request of his patron Ambrose. The book it answers, Celsus's True Word, survives mainly through the quotations that Origen gives, so the reply is also the chief source for the attack.",
        rare = true,
    ),

)
