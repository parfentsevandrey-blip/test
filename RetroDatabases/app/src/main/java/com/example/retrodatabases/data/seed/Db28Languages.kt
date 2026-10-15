package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1760 to 1770: English grammars, a French dictionary, elocution, logic and an alphabet reform. */
val db28Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Priestley's Rudiments of English Grammar",
        originalTitle = "The Rudiments of English Grammar",
        author = "Joseph Priestley",
        year = 1761,
        summary = "A short English grammar for schools, with rules for the parts of speech and an account of style.",
        details = "Priestley, a dissenting minister and teacher who later isolated oxygen in 1774, published the grammar in London in 1761. It was written for pupils and set out the parts of speech and their forms, with observations on style. It was one of several English grammars of the 1760s that taught the language to schoolchildren.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Lowth's Short Introduction to English Grammar",
        originalTitle = "A Short Introduction to English Grammar",
        author = "Robert Lowth",
        year = 1762,
        summary = "A grammar of English that states rules of correct usage and sets a prescriptive style that later school grammars followed.",
        details = "Lowth, later Bishop of London, published the book in 1762. It gives rules of correct usage with examples of errors, and its prescriptive tone was copied by many later grammars. Critics have argued that some of its rules were borrowed from Latin grammar rather than taken from the usage of English speakers.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The fourth edition of the French Academy's dictionary",
        originalTitle = "Dictionnaire de l'Academie francaise, quatrieme edition",
        author = "Academie francaise",
        year = 1762,
        summary = "A French dictionary compiled by the members of the Academy, which fixes the accepted words and senses of the language.",
        details = "The Academy began its dictionary in the 1630s, and the fourth edition appeared in 1762 after decades of revision by its members. Its entries record accepted words and their senses, so writers and printers used it as the standard reference for French.",
    ),

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Elocution",
        title = "Sheridan's lectures on elocution",
        originalTitle = "A Course of Lectures on Elocution",
        author = "Thomas Sheridan",
        year = 1762,
        summary = "A course of lectures on speech that treats pauses, stress and tone as parts of a system that can be taught by rule.",
        details = "Sheridan, an actor and teacher, delivered lectures on reading and speaking and published the course in 1762. He argued that the sense of a sentence depends on its pauses and tones, and that these could be taught as rules. The course shows how spoken English was taught to adults in the eighteenth century.",
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Logic and signs",
        title = "Lambert's Neues Organon on the signs of thought",
        originalTitle = "Neues Organon",
        author = "Johann Heinrich Lambert",
        year = 1764,
        summary = "A two-volume treatise on logic whose parts cover thought, truth, the signs of thought and appearance.",
        details = "Lambert published the Neues Organon in 1764 as a treatise on logic in two volumes. Its section on signs treats the symbols and terms by which thoughts are represented in reasoning. It is one of several eighteenth-century attempts to treat logic with exact symbols.",
        rare = true,
    ),

    entry(
        databaseId = 28,
        kind = Kind.LANGUAGE,
        section = "Alphabets",
        title = "Franklin's scheme for a new alphabet",
        originalTitle = "A Scheme for a New Alphabet and a Reformed Mode of Spelling",
        author = "Benjamin Franklin",
        year = 1768,
        summary = "A proposal for an alphabet with a letter for each sound of English, so that words could be spelled as they are pronounced.",
        details = "Franklin drafted the scheme in 1768 and proposed new letters for the sounds that the old alphabet could not show. The reform was never adopted, but the proposal is an early case of a phonetic alphabet made for a single language.",
        rare = true,
    ),
)
