package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 780 to 790: Lu Yu's Classic of Tea, usually dated to about 780, and the Hadrianum that Pope Hadrian I sent to Charlemagne about 785. */
val db126Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.PRACTICE,
        section = "Chinese",
        title = "Lu Yu's Classic of Tea, the Chajing, a book on the growing, making and brewing of tea, usually dated to about 780",
        originalTitle = "Chajing",
        author = "Lu Yu",
        year = 780,
        summary = "Lu Yu's Chajing, the Classic of Tea, sets out how tea is grown, made and brewed, and is usually dated to about 780.",
        details = "It describes the tools used to make tea and the water and fire used to brew it, and later writers treated it as the standard text on the subject. The date of about 780 is the usual one, and the text may have been revised after that.",
    ),

    entry(
        databaseId = 126,
        kind = Kind.PRACTICE,
        section = "Latin",
        title = "The Hadrianum, the Roman sacramentary of Mass prayers that Pope Hadrian I sent to Charlemagne about 785",
        originalTitle = "Hadrianum",
        author = "Pope Hadrian I",
        year = 785,
        summary = "Pope Hadrian I sent Charlemagne a Roman sacramentary of Mass prayers about 785, a book that the Frankish churches later found deficient and supplemented.",
        details = "One account places the dispatch in 785, and others give 785 or 786 for its arrival at the Frankish court. A supplement was added to supply the Sunday texts the book lacked, and scholars still argue over whether Alcuin or Benedict of Aniane wrote it.",
        rare = true,
    ),

)
