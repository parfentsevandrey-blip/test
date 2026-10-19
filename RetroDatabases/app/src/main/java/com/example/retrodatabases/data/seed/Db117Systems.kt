package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 870 to 880: the Treaty of Meerssen, the Tahirid end at Nishapur, Huang Chao's rebellion, Charles the Bald's coronation and the Capitulary of Quierzy. */
val db117Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.SYSTEM,
        section = "Francia",
        title = "The Treaty of Meerssen, which divided the middle kingdom of Lotharingia in 870",
        originalTitle = "Treaty of Meerssen",
        author = "Louis the German and Charles the Bald",
        year = 870,
        summary = "The Treaty of Meerssen, agreed in August 870 by Louis the German and Charles the Bald, divided the middle kingdom of Lotharingia between East and West Francia.",
        details = "Lothar II died in 869 without a legitimate heir, and his uncles met at Meerssen near Maastricht to split his middle kingdom. August 870 is the date usually given for the treaty.",
    ),

    entry(
        databaseId = 117,
        kind = Kind.SYSTEM,
        section = "Khurasan",
        title = "Ya'qub ibn al-Layth takes Nishapur in 873 and ends the Tahirid dynasty of Khurasan",
        originalTitle = "Al-Tahiriyya",
        author = "Ya'qub ibn al-Layth al-Saffar",
        year = 873,
        summary = "Ya'qub ibn al-Layth, a Saffarid ruler from Sistan, took Nishapur in 873, and with it he ended the Tahirid dynasty that had governed Khurasan since 821.",
        details = "The last Tahirid, Muhammad ibn Tahir, had governed from 862, and accounts differ on how the city fell, one describing a siege and another a near-bloodless takeover. The usual date is summer 873, but some accounts date the overthrow to 872.",
        rare = true,
    ),

    entry(
        databaseId = 117,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Huang Chao's rebellion, which began in 874 and took Chang'an in 880",
        originalTitle = "Huang Chao zhi luan",
        author = "Huang Chao and his followers",
        year = 874,
        summary = "Huang Chao's rebellion began in 874 in the east of Tang China, and in 880 his forces took the capital Chang'an and drove the court out.",
        details = "Huang Chao, a former salt smuggler from Shandong, led the rising, which drew on famine, heavy taxes and the weakness of the Tang armies. The rising is usually given as running from 874 to 884, when Huang Chao died and it came to an end.",
    ),

    entry(
        databaseId = 117,
        kind = Kind.SYSTEM,
        section = "Italy",
        title = "Charles the Bald is crowned emperor at Rome in 875, two years before his death",
        originalTitle = "Imperial coronation of Charles the Bald",
        author = "Charles the Bald and Pope John VIII",
        year = 875,
        summary = "Pope John VIII crowned Charles the Bald emperor at Rome in 875, and the imperial title he held lasted until his death in 877.",
        details = "The coronation followed the death of the emperor Louis II in 875, and Charles then led an army into Italy, where he died on his way home in October 877. The coronation gave the West Frankish king the imperial title, and the title lasted only until his death.",
    ),

    entry(
        databaseId = 117,
        kind = Kind.SYSTEM,
        section = "Francia",
        title = "The Capitulary of Quierzy, which let a count's son take over his father's office",
        originalTitle = "Capitulare Carisiacense",
        author = "Charles the Bald",
        year = 877,
        summary = "Charles the Bald issued the Capitulary of Quierzy in June 877, a law that let a count's son take over his father's office under conditions set by the king.",
        details = "It was issued at Quierzy, a royal estate on the Oise, before the king set out for Italy, and its rules for counts and their offices during the king's absence are its most debated part. Historians disagree on how far it made offices hereditary, and it is often read as an early step toward hereditary counties.",
        rare = true,
    ),

)
