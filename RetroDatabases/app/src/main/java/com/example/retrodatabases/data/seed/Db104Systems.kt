package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1000 to 1004: a Cairo house of knowledge, a Hungarian coronation with a papal crown, and the archbishopric of Gniezno. */
val db104Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.SYSTEM,
        section = "Egypt",
        title = "The Dar al-Ilm, a House of Knowledge with a library at Cairo",
        originalTitle = "Dar al-Ilm",
        author = "Caliph al-Hakim of Cairo",
        year = 1004,
        summary = "The Fatimid caliph al-Hakim founded the Dar al-Ilm, a public library and teaching house at Cairo, in 1004 or 1005, and accounts differ on the year.",
        details = "A contemporary chronicler, as reported on a UCL Digital Egypt page, says the caliph stocked the house from his palace treasury and paid its teachers and staff. Accounts differ on the year, since some give 1004 (AH 395) and others 24 March 1005, and figures for its book stock given by later writers are not reliable.",
        rare = true,
    ),

    entry(
        databaseId = 104,
        kind = Kind.SYSTEM,
        section = "Hungary",
        title = "The coronation of Stephen I of Hungary with a crown sent by Pope Sylvester II",
        originalTitle = "Coronation of Stephen I of Hungary",
        author = "Stephen I of Hungary and Pope Sylvester II",
        year = 1000,
        summary = "Stephen I was crowned king of Hungary on Christmas Day 1000 or on 1 January 1001, with a crown sent to him by Pope Sylvester II.",
        details = "Accounts split on the date, with 25 December 1000 given in some reference works and 1 January 1001 in a 2001 chapter that relies on the chronicle of Hartvic of Gyor. Hungarian tradition ties the papal gift to the Holy Crown of Hungary, and the crown's own history is a separate question.",
    ),

    entry(
        databaseId = 104,
        kind = Kind.SYSTEM,
        section = "Poland",
        title = "The archbishopric of Gniezno, founded in 1000 at the meeting of Otto III and Boleslaw I",
        originalTitle = "The archbishopric of Gniezno, founded in 1000 at the meeting of Otto III and Boleslaw I",
        author = "Otto III and Boleslaw I of Poland",
        year = 1000,
        summary = "Otto III and Boleslaw I met at Gniezno in 1000, and the Polish church was given an archbishopric with Radzim Gaudenty, Adalbert's half-brother, as its first archbishop.",
        details = "Accounts give 11 March 1000 for the meeting, and they disagree on who started the see, since one reference names Boleslaw I and another credits Otto III. Historians debate what the congress decided, including whether Boleslaw was promised a royal crown, and some accounts say the new see displaced the older bishopric of Poznan, while a conference paper counts Poznan among four bishoprics created alongside it.",
        rare = true,
    ),

)
