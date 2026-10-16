package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1460 to 1470: a pawn bank, relay posts, painted account covers, a Venetian printing privilege and a quarantine island. */
val db58Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.PRACTICE,
        section = "Lending",
        title = "Monte di Pieta, the pawn bank of Perugia",
        originalTitle = "Monte di Pieta",
        author = "Michele Carcano and the Franciscan preachers",
        year = 1462,
        summary = "The Monte di Pieta at Perugia lent money against pledges at low or no interest, a charity that became a model for later pawn banks.",
        details = "Most accounts date the Perugia monte to 1462, after Michele Carcano's sermons of 1461 against Jewish lenders. Claims to be the first such bank are disputed, since earlier attempts at Ancona and Ascoli are named, and papal approval for the Perugian bank came only in 1467.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.PRACTICE,
        section = "Relays",
        title = "Relay horses at royal stations, Louis XI's post",
        originalTitle = "Postes royales",
        author = "Louis XI of France",
        year = 1464,
        summary = "Louis XI set up royal relay stations where post horses were changed, so that messages could travel faster than a single rider.",
        details = "The royal post is usually dated to 1464, and its horses were changed at fixed stations along the main roads. Historians differ on how far the relays reached in the first years and how far the service was open to people outside the royal household.",
        rare = true,
    ),

    entry(
        databaseId = 58,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "Painted covers of Siena's Biccherna account books",
        originalTitle = "Tavolette di Biccherna",
        author = "The Biccherna office of the Sienese commune",
        year = 1468,
        summary = "Siena's Biccherna office kept its finance registers under painted wooden covers, and a cover of 1468 by Benvenuto di Giovanni shows the commune's finances in peace and war.",
        details = "Accounts date Benvenuto di Giovanni's painted cover to 1468, and the covers are now kept in Siena's State Archive. The series is older, with accounts giving 1258 or 1263 for its first painted cover, so the year used is that of this dated example.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.PRACTICE,
        section = "Printing rights",
        title = "Venetian printing privilege for Johann of Speyer, 1469",
        originalTitle = "Privilegio",
        author = "The Venetian government",
        year = 1469,
        summary = "The Venetian state gave Johann of Speyer the sole right to print in its territory for five years, the earliest documented printing privilege granted by a European government.",
        details = "The Primary Sources on Copyright (1450 to 1900) commentary dates the grant to 18 September 1469 and regards it as the earliest documented European case of a government printing privilege. Accounts differ on whether Johann was already printing before the grant, so no start date is given here for his press.",
    ),

    entry(
        databaseId = 58,
        kind = Kind.PRACTICE,
        section = "Health",
        title = "Lazzaretto Nuovo, Venice's quarantine island, from 1468",
        originalTitle = "Lazzaretto Nuovo",
        author = "The Republic of Venice",
        year = 1468,
        summary = "The Republic of Venice turned a monastic island in the northern lagoon into the Lazzaretto Nuovo, where the crews and cargo of plague-hit ships were isolated.",
        details = "The Historywalks Venice chronology dates the conversion of the island to 1468, and says that a hospital alone was not enough because the contacts of the sick also had to be held apart; it dates the older Lazzaretto Vecchio to 1423. Crews of arriving ships were kept on the island while their cargo was decontaminated. The site's superlative, that this was the first permanent plague quarantine station in the world, is not confirmed here and is not claimed.",
    ),

)
