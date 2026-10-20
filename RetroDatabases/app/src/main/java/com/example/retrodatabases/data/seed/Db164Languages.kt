package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 400 to 410: Kumarajiva's Lotus Sutra at Chang'an, the Notitia Dignitatum, the Jerusalem Talmud and Augustine's Confessions. */
val db164Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.LANGUAGE,
        section = "Translation",
        title = "Kumarajiva's Chinese translation of the Lotus Sutra",
        originalTitle = "Saddharmapundarika",
        author = "Kumarajiva",
        year = 406,
        summary = "Kumarajiva completed his Chinese translation of the Lotus Sutra at Chang'an in 406, and it became the text of the sutra that East Asian Buddhists read.",
        details = "The sources give 406 for the translation, which was made at Chang'an after Kumarajiva's arrival there about 401. The Chinese title is Miaofa lianhua jing, and the Lotus is the central sutra of the Tiantai and Nichiren schools, so this wording shaped later readings of the text.",
    ),

    entry(
        databaseId = 164,
        kind = Kind.LANGUAGE,
        section = "Offices",
        title = "The Notitia Dignitatum, a list of the offices of the late Roman empire",
        originalTitle = "Notitia Dignitatum",
        author = "Unknown imperial clerks",
        year = 400,
        summary = "The Notitia Dignitatum lists the civil and military offices of the late Roman empire, East and West, with their staff and insignia, in a text dated about 400.",
        details = "Estimates for the eastern part run from about 395 to about 450, and the western part is usually placed near 420, so 400 is a round date inside that spread rather than a fixed year. Some sections may draw on older material, which keeps the dating open.",
        rare = true,
    ),

    entry(
        databaseId = 164,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "The Jerusalem Talmud, the Talmud of the Land of Israel",
        originalTitle = "Talmud Yerushalmi",
        author = "Rabbis of the Land of Israel",
        year = 400,
        summary = "The Jerusalem Talmud gives the rabbinic discussion of the Mishnah in Hebrew and Aramaic, edited in the Land of Israel in the late fourth or early fifth century.",
        details = "It comments on only part of the Mishnah, and it is shorter and less worked over than the Babylonian Talmud. Accounts date its final editing to the late fourth or early fifth century, so 400 is a round year inside that range.",
        rare = true,
    ),

    entry(
        databaseId = 164,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Augustine's Confessions, a Latin prayer about the author's life in thirteen books",
        originalTitle = "Confessiones",
        author = "Augustine of Hippo",
        year = 400,
        summary = "Augustine wrote his Confessions in Latin in the years around 400, a prayer addressed to God that tells the story of his life and conversion.",
        details = "Augustine wrote the work from about 397 to about 400, and its books tell the story of his life up to his mother's death in book nine. The last books turn to memory, time and the opening of Genesis, and 400 is the usual round date for its completion.",
    ),

)
