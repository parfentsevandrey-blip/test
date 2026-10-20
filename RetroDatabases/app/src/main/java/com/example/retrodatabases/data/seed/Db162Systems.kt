package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 420 to 430: the Liu Song dynasty, Dadisho's synod of the Persian church, Theodosius II's teaching posts, the Vandal crossing into Africa and the end of the Armenian monarchy. */
val db162Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Liu Song dynasty, founded in 420 by the general Liu Yu at Jiankang",
        originalTitle = "Liu Song",
        author = "Liu Yu, later Emperor Wu of Liu Song",
        year = 420,
        summary = "The Liu Song dynasty began in 420, when the general Liu Yu ended the Eastern Jin and took the throne at Jiankang.",
        details = "Liu Yu was a military commander who took power from the Eastern Jin, and the Liu Song became the first of the Southern dynasties that followed in the south. The dynasty lasted until 479, when the Southern Qi replaced it.",
    ),

    entry(
        databaseId = 162,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The synod of Dadisho' in the Persian church, which struck down an appeal to Roman bishops, dated 424",
        originalTitle = "Synod of Dadisho'",
        author = "Dadisho', catholicos of Seleucia-Ctesiphon",
        year = 424,
        summary = "Dadisho', the catholicos of the Persian church, presided over a synod in 424 whose record survives in the Synodicon Orientale.",
        details = "Dadisho' had taken the office about 421 or 422, and the synod repealed a supposed right of eastern Christians to appeal against the bishop of Seleucia-Ctesiphon to bishops in the Roman lands, a right that no surviving text attests. Some scholars read the repeal as a step toward the independence of the eastern church, while others read it as the restoring of the catholicos's own authority.",
        rare = true,
    ),

    entry(
        databaseId = 162,
        kind = Kind.SYSTEM,
        section = "Education",
        title = "Theodosius II's decree of 425 that set up thirty-one public teaching posts at Constantinople",
        originalTitle = "Public teaching posts of Constantinople",
        author = "Theodosius II",
        year = 425,
        summary = "A decree of 425 set up public teaching posts at Constantinople in grammar, rhetoric, law and philosophy, thirty-one in all, paid from the imperial treasury.",
        details = "Accounts usually date the founding to 425 and credit Theodosius II, and they give thirty-one posts in all. They differ on how many of the posts were Greek and how many Latin, and one tally counts twenty for grammar, eight for rhetoric, two for law and one for philosophy.",
    ),

    entry(
        databaseId = 162,
        kind = Kind.SYSTEM,
        section = "Africa",
        title = "The Vandals cross from Spain into Africa under Gaiseric in 429, the start of their kingdom in North Africa",
        originalTitle = "Vandal crossing into Africa",
        author = "Gaiseric and the Vandals",
        year = 429,
        summary = "Gaiseric led the Vandals from Spain across into North Africa in 429, and their kingdom there began.",
        details = "The crossing is dated 429 in the usual accounts, and the Vandals ruled their African provinces until Justinian's armies took the kingdom in 533 and 534. Their capture of Carthage in 439 came later and is not part of this record.",
    ),

    entry(
        databaseId = 162,
        kind = Kind.SYSTEM,
        section = "Armenia",
        title = "The Persian deposition of the last Arsacid king of Armenia in 428, which ended the Armenian monarchy",
        originalTitle = "Arsacid monarchy of Armenia",
        author = "Bahram V of Persia",
        year = 428,
        summary = "In 428 the Sasanian king Bahram V deposed the last Arsacid king of Armenia, and the monarchy ended in favour of rule by a Persian governor.",
        details = "The Arsacid line had ruled Armenia since the first century, and the Persian takeover replaced the king with a marzban, a governor appointed from Persia. The year 428 is the usual date for the deposition.",
        rare = true,
    ),

)
