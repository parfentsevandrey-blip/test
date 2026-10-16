package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1332 to 1339: an electoral declaration at Rhense, a Japanese code, a southern Indian kingdom and two papal universities. */
val db71Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "The electors' declaration at Rhense that an elected king needs no papal approval",
        originalTitle = "Licet iuris",
        author = "The electors of the Holy Roman Empire",
        year = 1338,
        summary = "In July 1338 the electors of the Empire met at Rhense and declared that a king they had chosen needed no papal approval to hold his office.",
        details = "The declaration takes its name from its opening words, Licet iuris, and it was made at Rhense on the Rhine near Koblenz. It is usually dated to July 1338 and is one of the early acts in the law of royal election.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The Kenmu code of seventeen articles, issued by Ashikaga Takauji",
        originalTitle = "Kenmu shikimoku",
        author = "Ashikaga Takauji",
        year = 1336,
        summary = "The Kenmu shikimoku, a code of seventeen articles, was issued in 1336 by Ashikaga Takauji to set out the rules of his new government.",
        details = "The code is dated to the third year of the Kenmu era, which is 1336, and it takes its name from that era. Its articles set out rules for warrior government and for the courts.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.SYSTEM,
        section = "India",
        title = "The Vijayanagara kingdom of southern India, traditionally founded in 1336",
        originalTitle = "Vijayanagara",
        author = "Harihara and Bukka, the founding brothers",
        year = 1336,
        summary = "Vijayanagara, a Hindu kingdom of southern India, is traditionally dated to 1336, when its founders set up the city of that name on the Tungabhadra river.",
        details = "The traditional date is 1336, but some historians place the founding in the 1340s, so accounts differ on the year. The capital's ruins lie near the modern town of Hampi.",
    ),

    entry(
        databaseId = 71,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The university of Cahors, set up under Pope John XXII",
        originalTitle = "Studium of Cahors",
        author = "Pope John XXII",
        year = 1332,
        summary = "The university of Cahors was set up under Pope John XXII, who was born in the town, with faculties of law, medicine and theology.",
        details = "The founding is usually dated to 1332, while one chronology gives a papal bull of June 1331 and places the opening of the medical chair in 1332, so the dates are disputed. John XXII was born in the town and is credited with the creation of its university.",
        rare = true,
    ),

    entry(
        databaseId = 71,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The university of Grenoble, created by a papal bull of 1339",
        originalTitle = "Studium of Grenoble",
        author = "Pope Benedict XII",
        year = 1339,
        summary = "The university of Grenoble was created in 1339 by a bull of Pope Benedict XII.",
        details = "The usual accounts date the original university to 1339 and credit the bull of Benedict XII with its creation. The year is the one most often given, and it is used here without a span of dates.",
        rare = true,
    ),

)
