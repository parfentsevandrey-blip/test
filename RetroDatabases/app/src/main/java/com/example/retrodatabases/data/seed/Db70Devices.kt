package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1340 to 1347: bombards at Quesnoy, a Paduan tower clock, the guns at Crecy, the catapults of Caffa and a bell for Siena's tower. */
val db70Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.DEVICE,
        section = "Guns",
        title = "The bombards at the siege of Quesnoy, named in a chronicle of 1340",
        originalTitle = "Bombarda",
        author = "An unknown chronicler of the siege",
        year = 1340,
        summary = "A chronicle of a siege at Quesnoy in 1340 names bombards, and historians differ on whether it describes true guns or some other engine.",
        details = "Quesnoy lies in the old county of Hainaut, and contemporary documents use the word bombard only several years after the siege. The early mention is therefore disputed, and it is one of the arguments about when guns first reached Europe.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.DEVICE,
        section = "Clocks",
        title = "A tower clock at Padua, put up in 1344",
        originalTitle = "Orologio",
        author = "Jacopo, a clockmaker of Padua, by one account",
        year = 1344,
        summary = "A mechanical tower clock is said to have been set up at Padua in 1344, and accounts differ on its maker and on the building that carried it.",
        details = "One account credits a clockmaker called Jacopo, while another places an astronomical clock on the Palazzo del Capitano in the same year. The astronomical clock of Giovanni de' Dondi is a separate device of later date, so the 1344 date is approximate and disputed.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.DEVICE,
        section = "Guns",
        title = "Two or three vase-shaped guns the English may have fielded at Crecy in 1346",
        originalTitle = "Pot de fer",
        author = "The army of Edward III",
        year = 1346,
        summary = "The English army may have brought two or three small vase-shaped guns to the battle of Crecy in 1346, and historians differ on whether they fired in the battle or mainly frightened the enemy.",
        details = "The guns are known from later accounts and from the vase-shaped pot de fer, so their number and role are uncertain. The battle was fought on 26 August 1346 and is often cited as an early field use of guns in western Europe, a claim that is also disputed.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.DEVICE,
        section = "Siege engines",
        title = "The catapults that threw plague corpses over the walls of Caffa in 1346",
        originalTitle = "Historia de morbo",
        author = "Gabriele de' Mussi",
        year = 1346,
        summary = "At the siege of Caffa in 1346 the attackers are said to have thrown bodies dead of plague over the walls with catapults, according to the account of Gabriele de' Mussi.",
        details = "Mussi wrote his account after the plague had spread through the Mediterranean, and it is the chief source for the story, so historians differ on how far it can be trusted. The account describes the engines only in general terms and gives no measure of how far the bodies were thrown.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.DEVICE,
        section = "Bells",
        title = "The first bell hung in the Torre del Mangia at Siena, 1347",
        originalTitle = "Campana",
        author = "Riccardo di Tingo and his son Agostino of Florence",
        year = 1347,
        summary = "Siena's Torre del Mangia received its first bell in 1347, cast by the Florentine founders Riccardo di Tingo and his son Agostino.",
        details = "The tower was completed around the time of the Black Death, and its bell gave the city a public signal. A clock for the tower is recorded for 1360, so the bell is the earlier public timekeeping device on the site.",
    ),

)
