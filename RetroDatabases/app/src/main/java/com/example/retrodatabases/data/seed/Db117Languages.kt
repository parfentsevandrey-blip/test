package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 870 to 880: Otfrid's rhymed gospel harmony of about 871, al-Ya'qubi's history of about 872 and the Gwalior tablet of 876. */
val db117Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.LANGUAGE,
        section = "Old High German",
        title = "Otfrid's Evangelienbuch, a gospel harmony in rhymed Old High German verse",
        originalTitle = "Liber evangeliorum",
        author = "Otfrid of Weissenburg",
        year = 871,
        summary = "Otfrid of Weissenburg's gospel harmony, one of the earliest long poems in rhymed German, was finished in the 860s or early 870s, and its Vienna manuscript is dated about 871.",
        details = "Otfrid, a monk of Weissenburg in Alsace, retold the four Gospels in rhymed long lines arranged in five books, and he dedicated the work to Louis the German, king of East Francia from 843 to 876. Modern estimates place its completion between 863 and 871, and the Vienna manuscript is dated about 871, the year used here. The dedications also name Bishop Solomon of Constance and two monks of St Gall.",
        rare = true,
    ),

    entry(
        databaseId = 117,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "al-Ya'qubi's History, an Arabic universal history from the Creation to about 872",
        originalTitle = "Tarikh al-Ya'qubi",
        author = "Ahmad ibn Abi Ya'qub al-Ya'qubi",
        year = 872,
        summary = "Al-Ya'qubi's Arabic history runs from the Creation to his own time and is usually dated to about 872, which makes it one of the earliest surviving Arabic universal histories.",
        details = "The work begins with the Creation and the early prophets, and its later part follows the Islamic caliphs from the rise of the Abbasids onward. The date of about 872 is the one usually given for its completion, so the year is approximate.",
        rare = true,
    ),

    entry(
        databaseId = 117,
        kind = Kind.LANGUAGE,
        section = "India",
        title = "The Gwalior tablet of 876, an Indian inscription that writes its numbers with a zero",
        originalTitle = "Chaturbhuj temple inscription",
        author = "The Gurjara-Pratihara court at Gwalior",
        year = 876,
        summary = "The Chaturbhuj temple tablet at Gwalior, dated 876 CE, grants land and garlands and writes its numbers 270 and 50 with a zero, and it is generally treated as the earliest dated zero in India.",
        details = "The tablet is dated to year 933 of a local era that began in 57 BCE, which is 876 CE, and it sets aside 270 hastas of land for a flower garden with money for 50 garlands a day. The zeros in 270 and 50 are why it is cited, but it is not the earliest zero anywhere, because the Bakhshali manuscript may hold an older one whose date is disputed.",
        rare = true,
    ),

)
