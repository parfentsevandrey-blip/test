package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1080 to 1090: an English land survey, a Song reform of the central offices and a Carthusian order. */
val db96Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Domesday survey of England, taken by sworn juries in 1086",
        originalTitle = "The Domesday survey of England, taken by sworn juries in 1086",
        author = "William I of England",
        year = 1086,
        summary = "The Domesday survey recorded the land, people and livestock of England in an inquiry ordered at Christmas 1085 and completed in 1086.",
        details = "Commissioners took the answers from sworn juries of each hundred, and the results were copied into two volumes, the Great Domesday and the Little Domesday. A twelfth-century writer compared the finality of its records to the Day of Judgement, and that comparison is the usual explanation of the name.",
    ),

    entry(
        databaseId = 96,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Yuanfeng reform of the Song central offices, 1080 to 1082",
        originalTitle = "Yuanfeng gaizhi",
        author = "Emperor Shenzong of Song",
        year = 1080,
        summary = "Emperor Shenzong began the Yuanfeng reform in 1080 to reorganise the central offices of the Song state on the Tang pattern of Three Departments and Six Ministries.",
        details = "The reform is dated 1080 to 1082 in most accounts, and the new structure was largely in place by 1082. Accounts differ on how far the new offices worked as intended.",
        rare = true,
    ),

    entry(
        databaseId = 96,
        kind = Kind.SYSTEM,
        section = "Monasticism",
        title = "The Carthusian order, founded at the Grande Chartreuse in 1084",
        originalTitle = "Ordo Cartusiensis",
        author = "Bruno of Cologne",
        year = 1084,
        summary = "Bruno of Cologne founded the Carthusian order in 1084 at the Grande Chartreuse, a mountain valley near Grenoble.",
        details = "Bruno and six companions settled in the valley, and the monks lived in separate cells, meeting for worship and for some meals on set days. The date of 1084 is for the first house, from which the order grew.",
    ),

)
