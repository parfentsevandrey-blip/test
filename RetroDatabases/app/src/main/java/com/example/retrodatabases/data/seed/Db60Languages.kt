package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1440 to 1450: Korean letters and rhyme standard, Korean Buddhist prose, an organ tablature, Tortelli's spelling of Greek loanwords and a Castilian poem. */
val db60Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Writing systems",
        title = "The Hunminjeongeum letters of Korea",
        originalTitle = "Hunminjeongeum",
        author = "King Sejong and his scholars",
        year = 1446,
        summary = "The Korean letters set out in the Hunminjeongeum of 1446 were 28 signs, and the shapes of its consonant letters followed the mouth, tongue and throat as each sound is made.",
        details = "The Sejong Sillok records the letters as finished in 1443, while the book that explained them to the public is dated 1446, so accounts differ on which year marks their creation. The explanation is written in classical Chinese, and the letters were meant to let ordinary people write their own language.",
    ),

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Rhymes",
        title = "The Dongguk Jeongun, a standard for Sino-Korean readings",
        originalTitle = "Dongguk Jeongun",
        author = "Sejong's scholars",
        year = 1447,
        summary = "The Dongguk Jeongun of 1447 gave a standard Korean reading for Chinese characters, arranged by rhyme and written in the new letters.",
        details = "Accounts differ on its date: one gives a finished text in September 1446, another gives 1447 for its compilation, and the printed edition is usually dated 1448. The project began as an adaptation of a Ming-era rhyme book, so that Chinese characters could be read with Korean sounds.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "The Ileborgh organ tablature of 1448",
        originalTitle = "Organ tablature by Adam Ileborgh",
        author = "Adam Ileborgh",
        year = 1448,
        summary = "Adam Ileborgh's organ tablature of 1448 wrote keyboard music with letters for the keys and signs for the length of each note.",
        details = "The book survives in Stuttgart and is among the earliest surviving German organ books written in tablature. Its notation differs from the staff notation used for voices, so a player needed the tablature itself to read the music.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Korean prose",
        title = "Seokbo sangjeol, the Buddha's life in Hangul",
        originalTitle = "Seokbo sangjeol",
        author = "Joseon court compilers",
        year = 1447,
        summary = "The Seokbo sangjeol of 1447 retold the life of the Buddha in Korean written with the new letters, so that a religious text could be read in the vernacular.",
        details = "The text was compiled from earlier Buddhist sources and written in Hangul, the letters of the Hunminjeongeum. This record dates it to 1447, and the question of its printing method is left open here.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Spelling",
        title = "Tortelli's spelling of words taken from Greek",
        originalTitle = "De orthographia dictionum e Graecis",
        author = "Giovanni Tortelli",
        year = 1447,
        summary = "Giovanni Tortelli's De orthographia set out how Latin should spell the words taken from Greek, in a dictionary dedicated to Pope Nicholas V.",
        details = "The preface addresses Nicholas V as pope, and he reigned from 1447, so the work is no earlier than that year; no source gives its exact date, and the record uses 1447 as the earliest date. Tortelli served as papal librarian under Nicholas, and the author's own manuscript survives in the Vatican Library, while the printed editions came later.",
        rare = true,
    ),

    entry(
        databaseId = 60,
        kind = Kind.LANGUAGE,
        section = "Castilian verse",
        title = "Juan de Mena's Laberinto de Fortuna, in Castilian verse",
        originalTitle = "Laberinto de Fortuna",
        author = "Juan de Mena",
        year = 1444,
        summary = "Juan de Mena's Laberinto de Fortuna, a Castilian poem of about three hundred stanzas, is usually dated to 1444, the year it was submitted to the king.",
        details = "The date of 1444 is the usual one, and a 2024 study by Luis Gomez Canseco gives 22 February 1444 as the day the poem was submitted to the king, so the record uses that year. The first printed edition is uncertain: a Salamanca incunable of probably 1481 to 1488 is tentatively called the editio princeps, and Hernan Nunez's glossed edition is dated 1499.",
        rare = true,
    ),

)
