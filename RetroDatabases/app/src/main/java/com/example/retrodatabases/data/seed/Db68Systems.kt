package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1361 to 1367: four new universities in Italy, Poland, Austria and Hungary, and the Statutes of Kilkenny. */
val db68Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Pavia, given its studium generale in 1361",
        originalTitle = "Studium generale of Pavia",
        author = "Emperor Charles IV, at the petition of Galeazzo II Visconti",
        year = 1361,
        summary = "In 1361 Charles IV gave Pavia the status of a studium generale, which made the town a seat of university study.",
        details = "No day or month is recorded in the usual accounts, so 1361 is the year given. Some local histories push the teaching of law at Pavia back to 825, and that early date is disputed.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Krakow, chartered in 1364",
        originalTitle = "Studium Generale Cracoviense",
        author = "King Casimir III of Poland",
        year = 1364,
        summary = "King Casimir III of Poland chartered a studium generale at Krakow in 1364, the first university in the kingdom.",
        details = "The usual date of the charter is 12 May 1364. The school was refounded in 1400 under Queen Jadwiga, so the founding of 1364 is sometimes described as a first foundation rather than a continuous one.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Vienna, founded by Duke Rudolf IV in 1365",
        originalTitle = "Studium generale Viennense",
        author = "Duke Rudolf IV of Austria",
        year = 1365,
        summary = "Duke Rudolf IV founded a studium generale at Vienna in 1365, a rival to the older school at Prague.",
        details = "The usual date of the foundation charter is 12 March 1365, and the duke died later in the same year. The school gave the Habsburg lands a university of their own, alongside the older school at Prague.",
    ),

    entry(
        databaseId = 68,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Pecs, authorized by Pope Urban V in 1367",
        originalTitle = "Studium Generale Quinqueecclesiense",
        author = "Pope Urban V and King Louis I of Hungary",
        year = 1367,
        summary = "A studium generale was authorized for Pecs in 1367 by a papal bull issued at Viterbo.",
        details = "The bull is dated 1 September 1367 in the usual accounts and survives only as a copy, and it excluded a theology faculty. Accounts differ on who founded the school, with some crediting Urban V and others King Louis I, and they name different first rectors.",
        rare = true,
    ),

    entry(
        databaseId = 68,
        kind = Kind.SYSTEM,
        section = "Laws",
        title = "The Statutes of Kilkenny, laws to keep the English apart from the Irish",
        originalTitle = "Statuta de Kilkenny",
        author = "The parliament of Ireland at Kilkenny",
        year = 1366,
        summary = "The Statutes of Kilkenny, passed in 1366, tried to keep English settlers in Ireland separate from the native Irish.",
        details = "The statutes forbade marriage and fostering across the divide and tried to stop the English from using Irish speech and dress. Their effect was limited, and later Irish history treats them as a sign of the colonial divide rather than as a working system.",
    ),

)
