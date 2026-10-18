package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 950 to 960: the Goryeo examination of 958, the Goryeo slave reexamination law of 956, and the Welsh laws placed at 950. */
val db109Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "The Goryeo civil service examination, the gwageo, set up in 958",
        originalTitle = "Gwageo",
        author = "King Gwangjong of Goryeo",
        year = 958,
        summary = "King Gwangjong of Goryeo set up the gwageo, a civil service examination on the Chinese model, in 958 on the advice of a Chinese scholar at his court.",
        details = "Candidates were tested on the Chinese classics and on writing in Chinese, and the examination was held until the Gabo reforms abolished it in 1894. Korean histories name Shuang Ji as the scholar who proposed the examination.",
    ),

    entry(
        databaseId = 109,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "The Goryeo slave reexamination law of 956, which freed people wrongly held as slaves",
        originalTitle = "Nobi angeom beop",
        author = "King Gwangjong of Goryeo",
        year = 956,
        summary = "King Gwangjong of Goryeo ordered in 956 a review of slave status that freed people wrongly held as slaves after the wars of the late Three Kingdoms period.",
        details = "The stated aim was to weaken the great families that held many slaves and to raise the court's revenue. Accounts differ on the year, since some give 958 for the measure and others 956.",
        rare = true,
    ),

    entry(
        databaseId = 109,
        kind = Kind.SYSTEM,
        section = "Wales",
        title = "The laws of Hywel Dda, the Welsh law book ascribed to King Hywel",
        originalTitle = "Cyfraith Hywel",
        author = "Hywel Dda",
        year = 950,
        summary = "Welsh law books ascribed to King Hywel Dda were drawn up, by tradition, at a meeting at Whitland, and 950 is placed here as the year of his death, the anchor for the entry.",
        details = "The codification is not dated securely, and the dates given for the meeting vary, with about 945 among them, so 950 is the year of the anchor and not of the code. Accounts give Hywel's death as 948, 949 or 950, and no manuscript of the law is securely from his own time.",
        rare = true,
    ),

)
