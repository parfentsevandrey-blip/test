package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 630 to 640: the battles of Yarmouk and al-Qadisiyya in 636, the Fourth Council of Toledo in 633 and the Ekthesis of 638. */
val db141Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.SYSTEM,
        section = "Arab",
        title = "The battle of Yarmouk in 636, the Byzantine defeat that opened Syria to the Arab conquest",
        originalTitle = "Ma'rakat al-Yarmuk",
        author = "The Byzantine and Arab armies",
        year = 636,
        summary = "The battle of Yarmouk, fought on the river of that name in 636, broke the Byzantine army in Syria and opened the province to Arab conquest.",
        details = "The fight took place on the Yarmouk, a tributary of the Jordan, and most accounts date it to the summer of 636. The sources give widely different numbers for the two armies, so the size of each force is uncertain.",
    ),

    entry(
        databaseId = 141,
        kind = Kind.SYSTEM,
        section = "Arab",
        title = "The battle of al-Qadisiyya in 636, which broke the Sasanian army and opened Iraq to the Arab conquest",
        originalTitle = "Ma'rakat al-Qadisiyya",
        author = "The Arab and Sasanian armies",
        year = 636,
        summary = "The battle of al-Qadisiyya, fought on the edge of the Iraqi plain in 636, broke the Sasanian army and opened Iraq to the Arab conquest.",
        details = "The Arab army under Sa'd ibn Abi Waqqas defeated the Sasanian forces, whose commander Rustam is said to have fallen in the fighting. Most accounts date the battle to 636, though some give 637, and the sources differ on the size of both armies.",
    ),

    entry(
        databaseId = 141,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "The Fourth Council of Toledo in 633, which issued canons for the church and the kingdom of the Visigoths",
        originalTitle = "Concilium Toletanum IV",
        author = "Isidore of Seville and the bishops of Visigothic Spain",
        year = 633,
        summary = "The Fourth Council of Toledo, held in 633 under King Sisenand, issued canons on the church and on the office of the king.",
        details = "Isidore of Seville presided over the council, whose canons cover the bishops, the clergy and the king's authority. The council is usually dated to 633, the year used here.",
        rare = true,
    ),

    entry(
        databaseId = 141,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Ekthesis of 638, the imperial statement of faith that taught one will in Christ and banned debate over his energies",
        originalTitle = "Ekthesis tes pisteos",
        author = "Heraclius, with Patriarch Sergius of Constantinople",
        year = 638,
        summary = "The Ekthesis was an imperial statement of faith issued in 638 that taught Christ had one will and forbade debate over his energies.",
        details = "It was meant to end the quarrel over whether Christ had one or two wills and energies, but it was condemned at the Lateran synod of 649 in Rome. Accounts usually date it to 638, the year used here, and the dispute it tried to settle had already been opposed by Sophronius's letter of 634.",
        rare = true,
    ),

)
