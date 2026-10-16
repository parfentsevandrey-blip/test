package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1320 to 1330: a Florentine studium, three English colleges and a gold coin of Hungary. */
val db72Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The studium generale of Florence, set up by the commune in 1321",
        originalTitle = "Studium generale",
        author = "The Florentine commune",
        year = 1321,
        summary = "The Florentine commune resolved in 1321 to set up a studium generale, the body that the University of Florence counts as its beginning.",
        details = "Accounts give 1321 for the commune's decision, but the early years are thinly documented. The studium is said to have lapsed in 1324 and was re-established with papal backing in 1349.",
        rare = true,
    ),

    entry(
        databaseId = 72,
        kind = Kind.SYSTEM,
        section = "Colleges",
        title = "Michaelhouse, the Cambridge college founded in 1324",
        originalTitle = "Michaelhouse",
        author = "Hervey de Stanton",
        year = 1324,
        summary = "Michaelhouse was founded at Cambridge in 1324 as a house for scholars, one of the earliest colleges of the university.",
        details = "The founder was Hervey de Stanton, a royal clerk, and the house was merged with King's Hall to form Trinity College in 1546. The year 1324 is the foundation date usually given.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.SYSTEM,
        section = "Colleges",
        title = "University Hall at Cambridge, founded in 1326",
        originalTitle = "University Hall",
        author = "Elizabeth de Clare",
        year = 1326,
        summary = "University Hall was founded at Cambridge in 1326 by Elizabeth de Clare, and it is the origin of the college that later took the name Clare.",
        details = "The hall was set up to house a fellowship of scholars, and it took the name Clare Hall in the 1330s. The year 1326 is the usual foundation date.",
        rare = true,
    ),

    entry(
        databaseId = 72,
        kind = Kind.SYSTEM,
        section = "Colleges",
        title = "Oriel College at Oxford, founded in 1326",
        originalTitle = "Oriel College",
        author = "Adam de Brome",
        year = 1326,
        summary = "Oriel College was founded at Oxford in 1326 by Adam de Brome, almoner to King Edward II, as a house for students and fellows.",
        details = "The royal licence for the foundation was granted in 1324, and the house was founded in 1326, the year usually given for the college's beginning.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "The gold florin of Hungary, first issued in 1325",
        originalTitle = "Florenus aureus",
        author = "Charles Robert of Hungary",
        year = 1325,
        summary = "The gold florin of Hungary was first issued in 1325 under King Charles Robert, a coin modelled on the florin of Florence that became the standard gold money of central Europe.",
        details = "The coin copied the weight and design of the Florentine florin, and the year 1325 is the date usually given for its first issue. Accounts differ on the exact date and place of the first striking.",
    ),

)
