package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1710 to 1720: Swift's plan for English, the Spanish academy, German logic, a Tamil grammar, the Kangxi dictionary and Dutch books in Japan. */
val db33Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Academies",
        title = "Swift's proposal to fix the English tongue",
        originalTitle = "A Proposal for Correcting, Improving and Ascertaining the English Tongue",
        author = "Jonathan Swift",
        year = 1712,
        summary = "Swift asked for a standing body to judge English words and grammar, so that the language would stop drifting.",
        details = "Swift wrote the proposal in 1712 as a letter to Robert Harley, then Lord Treasurer, and it asked for an academy on the French model. No English academy was founded, although the question of how to fix English went on in the dictionaries that followed.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Academies",
        title = "The Royal Spanish Academy and its motto",
        originalTitle = "Real Academia Espanola",
        author = "Juan Manuel Fernandez Pacheco, Marquis of Villena",
        year = 1713,
        summary = "A Madrid academy founded in 1713 to purify, fix and give splendour to the Spanish language, with a motto that names its three tasks.",
        details = "The academy was founded in Madrid in 1713 on the model of the French academy, and its motto Limpia, fija y da esplendor names its three tasks. Its large dictionary, the Diccionario de Autoridades, began to appear in 1726 and cited earlier writers as its authorities.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Philosophy",
        title = "Wolff's logic in German",
        originalTitle = "Vernunfftige Gedancken von den Kraften des menschlichen Verstandes",
        author = "Christian Wolff",
        year = 1713,
        summary = "A German logic that gave German words to the parts of reasoning at a time when learned books were still mostly written in Latin.",
        details = "Wolff's German logic of 1713 set out the rules of reasoning in German, and its terms for ideas, judgments and inferences shaped later German philosophy. Earlier writers such as Thomasius had lectured in German, so historians count Wolff's book as a major step rather than the first use of German in philosophy.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Tamil",
        title = "Ziegenbalg's Tamil grammar",
        originalTitle = "Grammatica Damulica",
        author = "Bartholomaus Ziegenbalg",
        year = 1716,
        summary = "A Latin grammar of everyday Tamil, written by a Lutheran missionary at Tranquebar for Europeans who needed the language.",
        details = "The grammar was printed at Halle in 1716, and the Francke Foundation there is credited with the printing. Accounts differ on priority, since a Lisbon primer of 1554 was the first Tamil text printed in Europe, while one library notice calls this grammar the earliest printed grammar of Tamil.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Chinese writing",
        title = "The Kangxi Dictionary of characters",
        originalTitle = "Kangxi zidian",
        author = "Compiled by a commission under the Kangxi emperor",
        year = 1716,
        summary = "A dictionary of about 47000 characters arranged under 214 radicals, a plan that later Chinese dictionaries widely adopted.",
        details = "The commission finished the dictionary in 1716, and its 47035 characters are grouped under 214 radicals, with the strokes left after each radical as the next key. The radical order has been used in many later dictionaries, and the guide in this batch sorts a small index in the same way.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "Dutch books admitted to Japan in 1720",
        originalTitle = "Rangaku",
        author = "Tokugawa Yoshimune",
        year = 1720,
        summary = "The shogunate eased its ban on foreign books in 1720, which let Dutch-language study, called rangaku, grow in Japan.",
        details = "Yoshimune relaxed the ban on Western books in 1720 and left out books on Christianity, so scholars could read Dutch texts on medicine, astronomy and mathematics. Accounts differ on how far the change was a deliberate policy of learning and how far it was a concession to Dutch traders.",
    ),
)
