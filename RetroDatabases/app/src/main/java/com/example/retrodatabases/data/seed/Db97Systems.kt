package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1071 to 1079: an examination reform, a papal list, a town charter, an observatory and a solar calendar of Persia. */
val db97Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.SYSTEM,
        section = "Examinations",
        title = "The Song examinations of 1071, which dropped poetry for the classics and policy essays",
        originalTitle = "Xining 4 examination reform (keju gaige)",
        author = "Wang Anshi and the Song court",
        year = 1071,
        summary = "In 1071 the Song court dropped the poetry and rhapsody sections of the state examinations and set the classics and policy essays in their place.",
        details = "The change took effect in the second month of Xining 4, which is 1071, and most literati accepted it. Su Shi was among the critics, while the wider reform package around it drew heavier opposition.",
    ),

    entry(
        databaseId = 97,
        kind = Kind.SYSTEM,
        section = "Papacy",
        title = "The Dictatus papae, twenty-seven statements of papal power of 1075",
        originalTitle = "Dictatus papae",
        author = "Gregory VII",
        year = 1075,
        summary = "The Dictatus papae is a list of twenty-seven short statements of papal claims, entered in the register of Gregory VII in 1075.",
        details = "The statements say that the Roman church was founded by the Lord alone, that the pope alone may use the imperial insignia, and that no one may judge the pope. Historians debate how far the list was meant to be used, since it survives only as an entry in the papal register.",
    ),

    entry(
        databaseId = 97,
        kind = Kind.SYSTEM,
        section = "Aragon",
        title = "The Fuero de Jaca of 1077, a written charter of town rights in Aragon",
        originalTitle = "Fuero de Jaca",
        author = "Sancho Ramirez of Aragon",
        year = 1077,
        summary = "King Sancho Ramirez of Aragon granted the town of Jaca a fuero in 1077, a written charter that set out the customs and privileges of its people.",
        details = "The Fuero de Jaca is one of the earliest surviving town charters of the Iberian kingdoms, and 1077 is the year usually given for it. Scholars still debate how far its rules were applied in practice.",
        rare = true,
    ),

    entry(
        databaseId = 97,
        kind = Kind.SYSTEM,
        section = "Persia",
        title = "The Jalali calendar of Malik-Shah, a solar calendar of 1079",
        originalTitle = "Taqvim-i Jalali",
        author = "Malik-Shah I, with Omar Khayyam and the Isfahan astronomers",
        year = 1079,
        summary = "The Jalali calendar was a solar calendar of 365 days with eight leap days in each 33 years, set up for the Seljuk sultan Malik-Shah in 1079.",
        details = "Omar Khayyam and the astronomers of the Isfahan observatory worked on it, and its mean year of 365.2424 days is close to the solar year. Its year began at the spring equinox, and accounts differ on whether 1079 marks the proposal or the year the calendar took effect. The Persian calendar that grew from it is still used in Iran and Afghanistan.",
    ),

    entry(
        databaseId = 97,
        kind = Kind.SYSTEM,
        section = "Persia",
        title = "The Isfahan observatory of Malik-Shah, staffed by Khayyam's astronomers",
        originalTitle = "The Isfahan observatory of Malik-Shah, staffed by Khayyam's astronomers",
        author = "Malik-Shah's astronomers under Omar Khayyam",
        year = 1074,
        summary = "The observatory at Isfahan, set up under Malik-Shah, was worked by Omar Khayyam's team from about 1074 to 1079 to measure the solar year and prepare the new calendar.",
        details = "Its work is usually dated from about 1074 to 1079, and the surviving records are sparse, mostly showing heavy spending and good equipment. Its measurements gave a solar year of about 365.24219858 days, and the work halted after Malik-Shah died in 1092.",
        rare = true,
    ),

)
