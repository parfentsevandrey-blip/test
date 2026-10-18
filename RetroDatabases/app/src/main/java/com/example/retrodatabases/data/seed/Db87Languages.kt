package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1174 to 1178: Chretien's Cliges, a life of Becket in Old French verse, a Chinese account of the south, and Persian, Hebrew and French works. */
val db87Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Nizami's Makhzan al-Asrar, the Treasury of Secrets, a Persian poem of moral tales",
        originalTitle = "Makhzan al-asrar",
        author = "Nizami Ganjavi",
        year = 1174,
        summary = "Nizami's Treasury of Secrets is a Persian poem of moral tales in rhyming couplets, and it is the first of the five poems that make up his Khamsa.",
        details = "Nizami wrote it in the Caucasus in the second half of the twelfth century, and accounts usually give 1174 as the year of composition, although no source fixes it exactly. Its tales were followed by four longer poems, and together the five form the Khamsa, a model for later Persian poets.",
        rare = true,
    ),

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "Hebrew",
        title = "Maimonides's Mishneh Torah, a Hebrew code of Jewish law written in Egypt",
        originalTitle = "Mishneh Torah",
        author = "Moses Maimonides",
        year = 1170,
        summary = "Maimonides's Mishneh Torah is a Hebrew code that arranges all of Jewish law by subject, and it was written in Egypt over about ten years.",
        details = "The code was compiled between 1170 and 1180 while Maimonides lived in Egypt, and it is arranged in fourteen books. Its arrangement by subject set it apart from the Talmud, which follows the arguments of the rabbis from one point to the next.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Marie de France's twelve Lais, short verse tales of about 1170",
        originalTitle = "Lais",
        author = "Marie de France",
        year = 1170,
        summary = "Marie de France's Lais are twelve short verse tales in French that she says she took from Breton lays.",
        details = "Marie names herself only in the prologue, and nothing certain is known of her life, so the collection is dated only roughly. Most accounts place it about 1170, although some give a span from the 1160s to the 1190s.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Chretien de Troyes's Cliges, an Old French romance of Constantinople and Arthur's court",
        originalTitle = "Cliges",
        author = "Chretien de Troyes",
        year = 1176,
        summary = "Chretien de Troyes's Cliges is an Old French romance of a Byzantine prince who is educated at King Arthur's court, written about 1176.",
        details = "Chretien wrote it after Erec and Enide and before his later Arthurian romances, and most scholars date it to about 1176. The story moves between Britain and Constantinople.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Guernes de Pont-Sainte-Maxence's Life of Saint Thomas the Martyr, an Old French verse life of Becket",
        originalTitle = "La Vie de saint Thomas le Martyr",
        author = "Guernes de Pont-Sainte-Maxence",
        year = 1174,
        summary = "Guernes de Pont-Sainte-Maxence wrote an Old French verse life of Thomas Becket in the years soon after the archbishop's murder in 1170.",
        details = "The poem is usually dated to the early 1170s, about 1172 to 1174, and the exact completion year is not fixed. Guernes claims to have drawn on people who had known Becket, which makes the poem a near-contemporary source for the martyrdom.",
        rare = true,
    ),

    entry(
        databaseId = 87,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Zhou Qufei's Lingwai daida, a Chinese account of the southern lands and their trade",
        originalTitle = "Lingwai daida",
        author = "Zhou Qufei",
        year = 1178,
        summary = "Zhou Qufei's Lingwai daida is a Chinese account of the lands beyond the southern passes, with notes on their peoples, goods and foreign trade, written in 1178.",
        details = "The title means answers from beyond the Ling mountains, which lay outside the Song heartland. The date 1178 is the usual date of completion for the book.",
        rare = true,
    ),

)
