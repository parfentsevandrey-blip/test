package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 350 to 359: two creed-making councils of 359 and a short-lived northern Chinese state of 350. */
val db169Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The councils of Ariminum and Seleucia, two creed-making synods of 359",
        originalTitle = "Synodus Ariminensis et Seleuciensis",
        author = "The emperor Constantius II and the bishops who attended",
        year = 359,
        summary = "Two councils met in 359, one at Ariminum in the west and one at Seleucia in the east, both called by the emperor Constantius II to settle the wording of the creed.",
        details = "The eastern council met at Seleucia in Isauria on 27 September 359, after an earthquake at Nicomedia moved it from its first site. At Ariminum the western bishops first backed the Nicene creed, but in October their delegates accepted a Homoean formula, so the first verdict was undone, and accounts of the numbers of bishops differ.",
        rare = true,
    ),

    entry(
        databaseId = 169,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Ran Min's state of Ran Wei, set up in 350 on the ruins of the Later Zhao",
        originalTitle = "Ran Wei",
        author = "Ran Min",
        year = 350,
        summary = "Ran Min, a general of the Later Zhao, seized power in 350 and set up a short-lived state called Ran Wei in northern China.",
        details = "Ran Min is recorded as ordering a massacre of the Jie people, and accounts differ on the number killed. The state did not last, and its end came within a few years, so 350 is the year of its founding as usually given.",
        rare = true,
    ),

)
