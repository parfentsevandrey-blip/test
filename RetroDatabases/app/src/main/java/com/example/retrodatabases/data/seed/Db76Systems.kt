package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1281 to 1288: Guo Shoujing's Shoushi calendar, an English statute for Wales, a statute for merchants' debts, Yuan paper money and a Croatian law code. */
val db76Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.SYSTEM,
        section = "Calendars",
        title = "The Shoushi calendar of Guo Shoujing, a Yuan calendar of 365.2425 days",
        originalTitle = "Shoushi li",
        author = "Guo Shoujing and the Yuan astronomers",
        year = 1281,
        summary = "Guo Shoujing's Shoushi calendar set the solar year at 365.2425 days and was put into use by the Yuan court in the early 1280s.",
        details = "Work began in 1276, on the commission of Kublai Khan, and accounts differ on whether the calendar was promulgated in 1280 or in 1281. Sources also give different numbers of observation stations, 26 in one account and 27 in another, and the year length matches the value that the Gregorian calendar of 1582 adopted.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.SYSTEM,
        section = "Wales",
        title = "The Statute of Rhuddlan, Edward I's English government of conquered Wales",
        originalTitle = "Statutum Walliae",
        author = "Edward I of England",
        year = 1284,
        summary = "The Statute of Rhuddlan, issued by Edward I in 1284, set up English government in conquered Wales and created new shires there.",
        details = "The statute placed the lands taken from the Welsh princes under English sheriffs and courts, keeping some Welsh law for civil matters while English law governed crime. Historians differ on how much Welsh law survived in practice, and the statute is usually dated to March 1284.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.SYSTEM,
        section = "Credit",
        title = "The Statute of Merchants, a register for recording merchants' debts in England",
        originalTitle = "Statutum de mercatoribus",
        author = "Edward I of England and his council",
        year = 1285,
        summary = "The Statute of Merchants of 1285 let a creditor record a debt before town officials, so that the debtor's goods and then his lands could be taken if the debt went unpaid.",
        details = "It is often seen as an early step towards a register of credit, a written record that gave the creditor firm proof of the debt. Historians differ on how widely merchants outside the large towns used the statute.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.SYSTEM,
        section = "Money",
        title = "The Zhiyuan chao, the Yuan government's paper money issued from 1287",
        originalTitle = "Zhiyuan chao",
        author = "The Yuan court of Kublai Khan",
        year = 1287,
        summary = "The Zhiyuan chao is a paper currency that the Yuan government issued from 1287 and that circulated across its empire.",
        details = "The notes were printed from carved plates, and the Yuan issued more than one paper currency during its rule. Historians differ on how well the notes kept their value, and 1287 is the year of issue usually given for this currency.",
    ),

    entry(
        databaseId = 76,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Vinodol Code, a Croatian law of 1288 written in Glagolitic script",
        originalTitle = "Vinodolski zakonik",
        author = "The assembly of Vinodol",
        year = 1288,
        summary = "The Vinodol Code is a law text of 1288 written in Croatian in the Glagolitic script, and it is one of the oldest surviving law codes in a Slavic language.",
        details = "It sets out rules for courts, fines, debts and the affairs of towns in the Vinodol district on the Adriatic coast. The year 1288 is the date usually given for the statute, and historians debate how far it recorded older custom rather than new rules.",
        rare = true,
    ),

)
