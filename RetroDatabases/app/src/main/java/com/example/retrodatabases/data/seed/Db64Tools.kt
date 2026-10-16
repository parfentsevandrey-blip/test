package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1400 to 1403: a Korean world map, an almanac, a rutter, Madhava's sines, a Book of Hours and Clavijo's account of Samarkand. */
val db64Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Maps",
        title = "The Kangnido, the Korean world map of 1402",
        originalTitle = "Honil Gangni Yeokdae Gukdo Ji Do",
        author = "Kwon Kun and Yi Hoe",
        year = 1402,
        summary = "The Kangnido is a Korean world map dated 1402, and it is the oldest surviving Korean world map.",
        details = "It was made by Kwon Kun and Yi Hoe, and its surviving copies are kept in Japan, including one at Ryukoku University in Kyoto. Accounts differ on how many copies survive in Japan, and the copies seem to have been revised in the 1470s and 1480s.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Calendars",
        title = "The almanac, a yearly table of moons and feasts",
        originalTitle = "Almanac",
        author = "Astronomers and schoolmasters",
        year = 1400,
        summary = "By about 1400 the almanac, a table for one year with the moons, feasts and days of the calendar, was a common book for those who kept time and church dates.",
        details = "An almanac gave the new and full moons, the saints' days and the dates of movable feasts for the year. Accounts differ on the first almanacs in Europe, and the word's origin is usually traced to Arabic.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Sailing",
        title = "The rutter, written sailing directions for the ports",
        originalTitle = "Rutter",
        author = "Pilots and merchants of the Atlantic and Mediterranean",
        year = 1400,
        summary = "By about 1400 pilots carried rutters, written sailing directions that gave the courses, distances and landmarks between the ports of a coast.",
        details = "A rutter gave its courses as compass points and its distances in days' sail or in leagues, and it was copied for each voyage. Accounts differ on how early the form arose.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Tables",
        title = "Madhava's table of sines, computed by series in Kerala",
        originalTitle = "Madhava's sine table",
        author = "Madhava of Sangamagrama",
        year = 1400,
        summary = "Madhava of Sangamagrama is credited with a table of sines worked out from infinite series, and the Kerala astronomers who followed him used it.",
        details = "Madhava is usually dated to about 1340 to 1425, and the texts that record his work were written after his time, so the date 1400 is a round figure for his working life. Accounts differ on how much of the Kerala work is his own.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Prayer books",
        title = "The Book of Hours, the lay prayer book of about 1400",
        originalTitle = "Book of Hours",
        author = "Makers of illuminated manuscripts",
        year = 1400,
        summary = "By about 1400 the Book of Hours was one of the commonest prayer books of lay readers in Western Europe, arranged by the eight hours of the church day.",
        details = "Books of Hours were copied in large numbers, often with calendars that marked saints' days and the dates of feasts. Historians use those calendars to date the feasts of the period, and accounts differ on how many survive.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.TOOL,
        section = "Travel",
        title = "Clavijo's embassy to the court of Timur at Samarkand",
        originalTitle = "Embajada a Tamorlan",
        author = "Ruy Gonzalez de Clavijo",
        year = 1403,
        summary = "Ruy Gonzalez de Clavijo left Castile in 1403 on an embassy to Timur, and his account of the court at Samarkand survives.",
        details = "The embassy reached Samarkand in 1404 and the travellers returned in 1406. The account was first printed in 1582, long after the journey, and its descriptions of the court are read with care.",
        rare = true,
    ),

)
