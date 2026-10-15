package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1960 to 1970: an editor that became Emacs's ancestor, a document formatter and the notation for grammars. */
val db7Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.TOOL,
        section = "Editors",
        title = "TECO",
        originalTitle = "Tape Editor and Corrector, later Text Editor and Corrector",
        author = "Dan Murphy at MIT",
        year = 1963,
        summary = "A text editor with a small command language, written for the PDP-1, which later ran on most DEC machines and became the base of the first Emacs.",
        details = "Murphy named it in 1962, and the work ran into 1963. The name first meant Tape Editor and Corrector, because the PDP-1 had no operating system and stored its source on paper tape. " +
            "The first Emacs was written as TECO macros, and TECO later ran on Unix and on PCs.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.TOOL,
        section = "Document formatting",
        title = "RUNOFF",
        originalTitle = "RUNOFF document formatter for CTSS",
        author = "Jerome Saltzer",
        year = 1964,
        summary = "Jerome Saltzer's document formatter for CTSS. Its descendants, the roff family, formatted the manual pages on Unix for decades.",
        details = "Saltzer wrote RUNOFF in MAD and FAP, and it came with TYPSET, a document editor. Accounts differ on the path to Unix, but the name roff is traced to Doug McIlroy's version.",
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.TOOL,
        section = "Notation",
        title = "Backus–Naur form",
        originalTitle = "Backus normal form, used in the ALGOL 60 report",
        author = "John Backus and Peter Naur",
        year = 1960,
        summary = "The notation for writing a language's grammar as rules that replace names with sequences of symbols, first used for ALGOL 60 and still used to describe languages.",
        details = "The ALGOL 60 report of 1960 used the notation to define the syntax of the language. The name Backus–Naur form came later, and the notation is still used to write grammars for parser generators and language standards.",
    ),
)
