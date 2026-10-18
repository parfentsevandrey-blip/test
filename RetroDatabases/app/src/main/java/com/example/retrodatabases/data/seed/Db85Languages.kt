package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1190 to 1197: Maimonides's Guide, Gerald of Wales's Journey, Nizami's Haft Paykar, two Latin books of the court and an Occitan treatise on poetry. */
val db85Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Judeo-Arabic",
        title = "Maimonides's Guide for the Perplexed, written in Judeo-Arabic",
        originalTitle = "Dalalat al-ha'irin",
        author = "Moses Maimonides",
        year = 1190,
        summary = "Maimonides completed his Guide for the Perplexed in 1190, a philosophical guide to the Hebrew Bible for readers puzzled by its apparent contradictions.",
        details = "He wrote it in Judeo-Arabic, which is Arabic written in Hebrew letters, and dedicated it to his pupil Joseph ibn Aqnin. The usual date for the Arabic text is 1190, and Samuel ibn Tibbon's Hebrew translation of 1204 carried the book to readers who could not read Arabic.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Gerald of Wales's Journey through Wales, a Latin account of 1191",
        originalTitle = "Itinerarium Kambriae",
        author = "Gerald of Wales",
        year = 1191,
        summary = "Gerald of Wales finished his Latin Journey through Wales in 1191, an account of preaching the Third Crusade through the Welsh countryside.",
        details = "Gerald toured Wales in 1188 with Archbishop Baldwin of Canterbury to recruit men for the crusade, and the book describes the people, places and customs he met on the way. The usual date for the book is 1191, and Gerald followed it with his Description of Wales in 1194.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Nizami's Haft Paykar, the Seven Beauties, a Persian verse poem",
        originalTitle = "Haft Paykar",
        author = "Nizami Ganjavi",
        year = 1197,
        summary = "Nizami completed the Haft Paykar in 1197, a Persian verse poem in which the Sasanian king Bahram Gur hears seven stories told by princesses in seven palaces.",
        details = "Each of the seven princesses lives in a palace dome of a different colour, and each tells the king a story, so the poem gathers seven tales within one frame. It is the fourth of the five long poems that make up Nizami's Khamsa, or Quintet, and 1197 is the usual date for its completion.",
    ),

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Walter Map's Courtiers' Trifles, a Latin book of court satire",
        originalTitle = "De nugis curialium",
        author = "Walter Map",
        year = 1190,
        summary = "Walter Map's Latin book of court anecdotes and satire was written about 1190 by a clerk who had served at the English royal court.",
        details = "The book is arranged in five distinctions and mixes gossip, tales and sharp comments on courtiers, monks and the church. Its date is not settled: scholars usually place its writing between the early 1180s and about 1193, and parts may have been added later.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Richard of Devizes's chronicle of the first years of Richard I",
        originalTitle = "Chronicon de rebus gestis Ricardi primi",
        author = "Richard of Devizes",
        year = 1192,
        summary = "Richard of Devizes wrote a Latin chronicle of the first years of Richard I, much of it about the Third Crusade, in or about 1192.",
        details = "The author was a monk of St Swithun's priory at Winchester, and the chronicle covers roughly the first three years of the king's reign, from 1189 to about 1192. Scholars still debate the exact date of composition, which is usually given as about 1192 or a little later.",
        rare = true,
    ),

    entry(
        databaseId = 85,
        kind = Kind.LANGUAGE,
        section = "Occitan",
        title = "Raimon Vidal's Razos de trobar, an early treatise on Occitan poetry",
        originalTitle = "Razos de trobar",
        author = "Raimon Vidal de Besalu",
        year = 1190,
        summary = "Raimon Vidal's Razos de trobar is an early treatise on the grammar and composition of troubadour poetry in Occitan.",
        details = "Vidal came from Besalu in Catalonia and wrote in Occitan, the language of the troubadours, setting out rules for grammar, rhyme and style. The date is disputed: the usual spans run from about 1190 to about 1213, so 1190 marks the start of that range.",
        rare = true,
    ),

)
