package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1110 to 1120: a Latin world description, a Hebrew treatise on measurement and a Chinese classified materia medica. */
val db93Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.TOOL,
        section = "Cosmography",
        title = "Honorius Augustodunensis's Imago mundi, a Latin description of the world",
        originalTitle = "Imago mundi",
        author = "Honorius Augustodunensis",
        year = 1110,
        summary = "Honorius Augustodunensis wrote the Imago mundi about 1110, a Latin description of the world that covers the earth, the heavens and the course of history.",
        details = "The author is also called Honorius of Autun, and the book survives in many manuscripts, which shows how widely it was read. The date of about 1110 is the usual estimate for its writing.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.TOOL,
        section = "Measurement",
        title = "Abraham bar Hiyya's Treatise on Measurement and Calculation, in Hebrew",
        originalTitle = "Hibbur ha-Meshihah ve-ha-Tishboret",
        author = "Abraham bar Hiyya",
        year = 1116,
        summary = "Abraham bar Hiyya, a Jewish scholar of Barcelona, wrote a practical treatise on measurement and calculation in Hebrew, dated to 1116 by some accounts.",
        details = "It covers practical geometry and algebra, including quadratic equations, and it was meant to help Jews in Spain and France measure land. Its Latin version, the Liber embadorum of 1145 credited to Plato of Tivoli, reached western readers, and a Catalan study places the treatise in the first third of the twelfth century, so accounts differ.",
        rare = true,
    ),

    entry(
        databaseId = 93,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "The Zhenghe edition of Tang Shenwei's classified materia medica",
        originalTitle = "Zhenghe xinxiu jingshi zhenglei beiyong bencao",
        author = "Tang Shenwei and the Song court",
        year = 1116,
        summary = "The official Zhenghe-era edition of Tang Shenwei's classified materia medica, a Chinese drug book organized by kind, is dated to 1116.",
        details = "The drugs are grouped by kind and described by their flavour and nature, the two classes that Chinese materia medica used. Huizong-era editions are dated 1108 and 1116, while the first form of the book is usually put in the 1080s, so accounts differ on the dates of the early editions.",
        rare = true,
    ),

)
