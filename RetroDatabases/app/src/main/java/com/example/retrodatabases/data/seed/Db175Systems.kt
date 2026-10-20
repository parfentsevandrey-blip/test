package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 290 to 300: the tetrarchy of Diocletian, set up in 293, and his census and tax units of 297. */
val db175Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 175,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "The tetrarchy of Diocletian, four rulers in two pairs of senior and junior emperors",
        originalTitle = "Tetrarchia",
        author = "Diocletian and Maximian, with the Caesars Galerius and Constantius",
        year = 293,
        summary = "Diocletian set up four rulers in two pairs, two senior emperors and two junior Caesars, to share the defence and government of the empire.",
        details = "The most common date for the naming of the two Caesars is 1 March 293, although some specialists place Galerius's elevation later, on 21 May. The arrangement takes its name from its four rulers, and civil wars later broke it.",
    ),

    entry(
        databaseId = 175,
        kind = Kind.SYSTEM,
        section = "Taxes",
        title = "Diocletian's census and tax units, the land unit and the head unit",
        originalTitle = "Iugatio and capitatio",
        author = "Diocletian's government",
        year = 297,
        summary = "Diocletian's census measured the empire's land and people in two units, the iugum for land and the caput for heads, and the tax was reckoned by them.",
        details = "Accounts differ on the date of the reform, with 287, 297 and 301 each given in some sources. The year 297 is used here, the year given for its implementation in Egypt.",
        rare = true,
    ),

)
