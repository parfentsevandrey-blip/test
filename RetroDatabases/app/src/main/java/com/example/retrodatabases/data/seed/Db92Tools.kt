package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1120 to 1130: Adelard's Latin astronomical tables, Lambert's Liber floridus, Hugh's practical geometry and the Xuanhe catalogue of bronzes. */
val db92Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.TOOL,
        section = "Astronomical tables",
        title = "Adelard of Bath's Latin version of al-Khwarizmi's astronomical tables, about 1126",
        originalTitle = "Zij of al-Khwarizmi",
        author = "Adelard of Bath",
        year = 1126,
        summary = "Adelard of Bath made a Latin version of al-Khwarizmi's astronomical tables about 1126, bringing Arabic astronomical tables to Latin readers.",
        details = "The date is taken from an Arabic date in the text, which one account converts to 26 January 1126. A Cambridge manuscript with examples for 1133 and 1134 casts doubt on that date, since those examples may be later additions.",
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.TOOL,
        section = "Encyclopedias",
        title = "Lambert of St Omer's Liber floridus, a Latin book of gathered learning",
        originalTitle = "Liber floridus",
        author = "Lambert of Saint-Omer",
        year = 1120,
        summary = "Lambert of Saint-Omer compiled the Liber floridus about 1120, a Latin book of gathered learning with extracts from older writers and many pictures.",
        details = "The book is arranged as a collection of extracts and diagrams, and its autograph copy is kept at Ghent. The year 1120 is the usual date for its completion, but the book was put together over a longer period, so the year is approximate.",
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Hugh of St Victor's Practica geometriae, a manual of practical measurement",
        originalTitle = "Practica geometriae",
        author = "Hugh of St Victor",
        year = 1125,
        summary = "Hugh of St Victor's Practica geometriae is a Latin manual of practical geometry for measuring land, heights and distances, written about the 1120s.",
        details = "The date is rough: the work is placed in about the same period as the Didascalicon and before Hugh's death in 1141. The year 1125 is used here as a working date, and the record claims no more precision than that.",
        rare = true,
    ),

    entry(
        databaseId = 92,
        kind = Kind.TOOL,
        section = "Reference",
        title = "The Xuanhe Bogu tu, an illustrated catalogue of ancient bronzes of 1123",
        originalTitle = "Xuanhe bogu tu",
        author = "Song court scholars under Emperor Huizong",
        year = 1123,
        summary = "The Xuanhe Bogu tu is an illustrated catalogue of ancient bronze vessels, compiled at the Song court in the Xuanhe era and dated 1123.",
        details = "The Xuanhe era ran from 1119 to 1125, and the catalogue was made under Emperor Huizong, who was also a painter and collector. The year 1123 is the date usually given for its completion.",
        rare = true,
    ),

)
