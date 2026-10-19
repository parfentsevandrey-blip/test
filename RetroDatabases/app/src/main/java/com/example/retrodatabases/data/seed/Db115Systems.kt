package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 890 to 900: the Kletorologion of Philotheos, a Byzantine court list of ranks, and the Zaydi state that Yahya al-Hadi founded in Yemen. */
val db115Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 115,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Kletorologion of Philotheos, a Byzantine court list of offices and ranks",
        originalTitle = "Kletorologion",
        author = "Philotheos",
        year = 899,
        summary = "The Kletorologion of Philotheos, a list of the Byzantine court offices in order of precedence, is dated 899.",
        details = "It sets out the offices of the imperial court in order of rank and gives their places at court ceremonies. The date 899 is the one given in the work, and the list shows the court's ranking rather than a sequence of events.",
        rare = true,
    ),

    entry(
        databaseId = 115,
        kind = Kind.SYSTEM,
        section = "Yemen",
        title = "The Zaydi state that Yahya al-Hadi founded in Yemen",
        originalTitle = "Al-Hadi ila al-Haqq",
        author = "Yahya al-Hadi ila al-Haqq",
        year = 897,
        summary = "Yahya al-Hadi ila al-Haqq founded the Zaydi state in Yemen in 897, a religious state whose imams ruled parts of the highlands for centuries.",
        details = "Yahya died at Sa'dah in 911, and his rule never united the whole country. Accounts differ on the end of the imamate, giving 1962, the year of the republican revolution, or 1970, the year the North Yemen civil war ended.",
        rare = true,
    ),

)
