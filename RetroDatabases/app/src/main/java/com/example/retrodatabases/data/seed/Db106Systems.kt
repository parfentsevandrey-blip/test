package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 980 to 990: a regency in Germany, a royal election in France, a Chola accession in India, a Kievan baptism and a Vietnamese dynasty. */
val db106Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.SYSTEM,
        section = "Germany",
        title = "Theophanu's regency for her son Otto III, from the death of Otto II in 983",
        originalTitle = "Regentschaft der Theophanu",
        author = "Theophanu",
        year = 983,
        summary = "After the death of Otto II in December 983, the Byzantine-born empress Theophanu governed the German realm as regent for their three-year-old son Otto III.",
        details = "Otto II died at Rome on 7 December 983, and Theophanu's regency ran until her death in June 991, a span of about eight years. The regency kept the Ottonian realm together while her son grew up.",
    ),

    entry(
        databaseId = 106,
        kind = Kind.SYSTEM,
        section = "France",
        title = "Hugh Capet is chosen king of the Franks, and his line rules France for centuries",
        originalTitle = "Hugo Capet",
        author = "Hugh Capet",
        year = 987,
        summary = "Hugh Capet was chosen king of the Franks in 987, after the death of Louis V, the last Carolingian king, and the Capetian line he founded ruled France for centuries.",
        details = "Louis V died in May 987 without an heir, and the magnates chose Hugh, the son of Hugh the Great, that summer. His line held the French crown in direct descent until 1328.",
    ),

    entry(
        databaseId = 106,
        kind = Kind.SYSTEM,
        section = "India",
        title = "Rajaraja I comes to the Chola throne of southern India",
        originalTitle = "Rajaraja Cholan",
        author = "Rajaraja I",
        year = 985,
        summary = "Rajaraja I began his reign over the Chola kingdom of southern India in 985 and extended Chola power across much of the peninsula.",
        details = "His reign is usually dated from 985 to 1014, and the Brihadisvara temple at Thanjavur, completed about 1010, is among the best-known monuments of his time. Chola inscriptions from his reign survive in large numbers and are a main source for the state he built.",
        rare = true,
    ),

    entry(
        databaseId = 106,
        kind = Kind.SYSTEM,
        section = "Rus",
        title = "The baptism of Vladimir of Kiev, dated 988 in the Primary Chronicle",
        originalTitle = "Kreshchenie Rusi",
        author = "Vladimir of Kiev",
        year = 988,
        summary = "The Primary Chronicle dates the baptism of Vladimir of Kiev, and the Christian conversion of his realm that followed, to 988.",
        details = "The year is the chronicle's date, and some historians put the baptism a year earlier. The new church brought the Byzantine rite and books in Church Slavonic to Kiev.",
    ),

    entry(
        databaseId = 106,
        kind = Kind.SYSTEM,
        section = "Vietnam",
        title = "Le Hoan takes the imperial title and founds the Early Le dynasty of Dai Viet",
        originalTitle = "Tien Le",
        author = "Le Hoan",
        year = 980,
        summary = "Le Hoan took power in Dai Viet in 980 and assumed the imperial title, founding the Early Le dynasty, which lasted until 1009.",
        details = "A Song invasion in 981 was turned back, and Dai Viet stayed independent of the Song court under this house. The dynasty ended in 1009.",
        rare = true,
    ),

)
