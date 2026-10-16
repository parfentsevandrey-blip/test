package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1340 to 1350: Pegolotti's merchant handbook, Richard de Bury's book on books, the Song history, a Paris plague report, Ibn al-Khatib's treatise and al-Umari's geography. */
val db70Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Commerce",
        title = "Pegolotti's merchant handbook, the Pratica della mercatura",
        originalTitle = "Pratica della mercatura",
        author = "Francesco Balducci Pegolotti",
        year = 1340,
        summary = "Pegolotti's Pratica della mercatura is a Florentine merchant's handbook of about 1340, with tables of weights, coins, exchange rates and trade routes.",
        details = "Pegolotti served the Bardi company abroad, and the handbook lists the measures and currencies a merchant needed for voyages to the Levant and the Black Sea. The date is usually given as about 1340, and accounts differ on the years over which the tables were compiled.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Books",
        title = "The Philobiblon, Richard de Bury's book on the love of books",
        originalTitle = "Philobiblon",
        author = "Richard de Bury, bishop of Durham",
        year = 1345,
        summary = "Richard de Bury completed the Philobiblon in 1345, a Latin treatise on the love of books, on how to collect them and on how a scholar should use them.",
        details = "Richard was bishop of Durham and a great collector of books, and he died in April 1345, so the treatise is dated 1344 or 1345 by accounts that differ on the year. The book is divided into twenty chapters and argues that wisdom is found in books.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Histories",
        title = "The Songshi, the official history of the Song dynasty compiled in 1345",
        originalTitle = "Songshi",
        author = "Toqto'a and the Yuan court historians",
        year = 1345,
        summary = "The Songshi, the official history of the Song dynasty, was compiled under the Yuan chancellor Toqto'a and presented in 1345, after the Liao and Jin histories of 1344.",
        details = "Toqto'a ordered the three histories compiled between 1343 and 1345, and the Songshi runs to 496 chapters. Accounts differ on how much of the work the chief compilers wrote themselves, and the histories are written in Classical Chinese.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "The Paris faculty of medicine's report on the plague, 1348",
        originalTitle = "Compendium de epidemia",
        author = "The masters of the Paris faculty of medicine",
        year = 1348,
        summary = "In 1348 the masters of the Paris faculty of medicine sent King Philip VI a report that blamed the plague on a planetary conjunction of 1345 and on corrupt air.",
        details = "The report was prepared in the autumn of 1348, and its astrological cause was drawn from the learned astronomy of the period. It also gave practical advice on diet, air and avoiding the sick, so it served as a guide as well as a theory.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Ibn al-Khatib's treatise on the dreaded disease, a Granada argument for contagion",
        originalTitle = "Muqni'at al-sa'il 'an al-marad al-hail",
        author = "Lisan al-Din Ibn al-Khatib",
        year = 1349,
        summary = "In 1349 the Granadan vizier Ibn al-Khatib wrote a treatise arguing that plague passes from person to person by contact.",
        details = "The treatise was written as the Black Death reached the Iberian peninsula, and it set out signs that later writers used to argue for contagion. The date is usually given as 1349, and accounts differ on whether the work was finished later in that decade.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.TOOL,
        section = "Geography",
        title = "Al-Umari's Paths of Sight, an encyclopaedic geography of the Mamluk realms",
        originalTitle = "Masalik al-absar fi mamalik al-amsar",
        author = "Ibn Fadl Allah al-Umari",
        year = 1340,
        summary = "Al-Umari's Masalik al-absar is an Arabic encyclopaedia of geography and administration written in Cairo about 1340, and it describes the lands and cities of his age.",
        details = "Al-Umari was a secretary in the Mamluk chancery, and his book includes a report of the Mali ruler Mansa Musa's pilgrimage of 1324. The date is given as about 1340, and accounts differ on how long the work took to compose during the 1330s and 1340s.",
        rare = true,
    ),

)
