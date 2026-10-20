package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 380 to 390: Wulfila's Gothic Bible, Vegetius's military epitome, Gregory's Theological Orations, Jerome's Gospel revision and Libanius's speech For the Temples. */
val db166Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.LANGUAGE,
        section = "Gothic",
        title = "Wulfila's Gothic Bible and the alphabet made for it",
        originalTitle = "Gothic Bible",
        author = "Wulfila, with a team of translators in some views",
        year = 380,
        summary = "Wulfila, bishop of the Goths, is credited with a Gothic translation of the Bible and with the Gothic alphabet made to write it in the fourth century.",
        details = "The sources date the translation only to the fourth century, so 380 is a round date used here, and accounts differ on whether it covered the whole Bible or only the New Testament. Newer scholarship doubts that Wulfila worked alone and treats the work as the product of a team, though the alphabet is still credited to him by tradition. Most of the surviving Gothic text is copied in the Codex Argenteus, a sixth-century manuscript probably written in Italy.",
    ),

    entry(
        databaseId = 166,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Vegetius's Epitome of military science, a Latin handbook of Roman warfare",
        originalTitle = "Epitoma rei militaris",
        author = "Flavius Vegetius Renatus",
        year = 383,
        summary = "Vegetius's Epitoma rei militaris is a Latin summary of Roman military science, dated by scholars to a window between about 383 and about 450.",
        details = "Scholars bracket the work between about 383 and about 450, and its dedicatee is named inconsistently, so accounts differ between the late 380s under Theodosius I and the mid-fifth century. The year 383 is used here as the earliest date in that bracket. The book was copied and read in western Europe for centuries, and its chapters on recruits, training, sieges and naval war were used by later writers on war.",
        rare = true,
    ),

    entry(
        databaseId = 166,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Gregory of Nazianzus's five Theological Orations, preached at Constantinople",
        originalTitle = "Logoi theologikoi",
        author = "Gregory of Nazianzus",
        year = 380,
        summary = "Gregory of Nazianzus's five Theological Orations defended the Nicene doctrine of the Trinity in sermons preached at Constantinople around 380.",
        details = "The orations were preached in the church of the Anastasia at Constantinople in 379 and 380, while Gregory led the small Nicene congregation of the city. They came in the years just before the council of 381, which Gregory presided over for a time. The Greek text survives in manuscripts copied over several centuries.",
        rare = true,
    ),

    entry(
        databaseId = 166,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Jerome's revision of the Latin Gospels, made at Rome for Pope Damasus",
        originalTitle = "Evangelia",
        author = "Jerome",
        year = 382,
        summary = "Jerome revised the Latin text of the four Gospels at Rome for Pope Damasus, and his revision became the base of the Gospel text in the Latin Vulgate.",
        details = "Damasus asked Jerome for a corrected Latin text of the Gospels, and the revision is usually dated to 382 and 383, before Jerome left Rome in 385. Jerome checked the Latin against Greek manuscripts, and his Gospel text passed into the Vulgate, the Latin Bible that became standard in the western church. He translated the Old Testament later, from the Hebrew, at Bethlehem.",
    ),

    entry(
        databaseId = 166,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Libanius's Oration For the Temples, a plea to spare the pagan shrines",
        originalTitle = "Pro templis",
        author = "Libanius of Antioch",
        year = 386,
        summary = "Libanius's Oration 30, For the Temples, is a Greek speech of about 386 that asks Theodosius to keep the pagan temples standing.",
        details = "The speech is usually dated to about 386, when imperial policy against pagan shrines was tightening, and it asks the emperor to keep the temples standing. Libanius taught rhetoric at Antioch, and the speech shows a pagan teacher arguing with a Christian emperor. The speech does not name its year, so the date is an inference from its context.",
        rare = true,
    ),

)
