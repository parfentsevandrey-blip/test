package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of the 1980s that were missing from the first edition: assemblers, compilers, shells, communications and typesetting. */
val db1ExtraTools: List<RecordEntity> = listOf(

    // ---------- Compilers and assemblers ----------

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "MASM assembler",
        originalTitle = "Microsoft Macro Assembler (MASM)",
        author = "Microsoft",
        year = 1981,
        summary = "The Microsoft macro assembler (1981) for x86: the standard assembler for PC programs.",
        details = "MASM supported macros and conditional compilation. Games and system programs of the 80s were often written in it.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "Turbo C compiler",
        originalTitle = "Borland Turbo C",
        author = "Borland",
        year = 1987,
        summary = "A fast C compiler from Borland (1987) with an integrated environment for MS-DOS.",
        details = "Turbo C combined the editor, compiler and debugger. The compiler ran very fast on the 8088 and 80286.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "Microsoft C",
        originalTitle = "Microsoft C 1.0",
        author = "Microsoft",
        year = 1983,
        summary = "Microsoft's first C compiler (1983) for MS-DOS: portable code for the PC.",
        details = "Microsoft C competed with Lattice C. Compilers of that era differed in their libraries and language extensions.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "Lattice C compiler",
        originalTitle = "Lattice C",
        author = "Lattice Inc.",
        year = 1982,
        summary = "One of the first C compilers for the PC (1982): popular with CP/M and MS-DOS programmers.",
        details = "Lattice C sold at a moderate price and was one of the first commercial C compilers for the PC.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "Turbo Assembler",
        originalTitle = "Turbo Assembler (TASM)",
        author = "Borland",
        year = 1988,
        summary = "The Borland assembler (1988), compatible with the MASM syntax.",
        details = "TASM supported the MASM syntax. It was used by game and driver developers.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "The m4 macro processor",
        originalTitle = "m4",
        author = "Brian Kernighan, Dennis Ritchie",
        year = 1977,
        summary = "A general-purpose Unix macro processor (1977): it substitutes text by templates. autoconf and the sendmail configuration are built on it.",
        details = "M4 worked with the text of any language. It was used to generate scripts and program skeletons.",
        rare = true,
    ),

    // ---------- Calculators and mathematics ----------

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "The bc calculator",
        originalTitle = "bc",
        author = "Lorinda Cherry, Robert Morris, Bell Labs",
        year = 1975,
        summary = "A Unix arbitrary-precision calculator (1975): a language with variables, functions and any precision.",
        details = "bc used a language similar to C and often sat in shell pipelines. It was used for precise calculations, for example for π.",
        rare = true,
    ),

    // ---------- Command line ----------

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The C shell",
        originalTitle = "C shell (csh)",
        author = "Bill Joy, Berkeley",
        year = 1978,
        summary = "A Unix shell with a C-like syntax (1978): command history, aliases and job control.",
        details = "csh appeared at Berkeley and became an alternative to the Bourne shell. Its syntax was often criticised, but its features were new.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The Korn shell",
        originalTitle = "Korn shell (ksh)",
        author = "David Korn, Bell Labs",
        year = 1983,
        summary = "The Korn shell (1983): combines the Bourne shell syntax with the conveniences of csh.",
        details = "ksh became the standard for system scripts on Unix System V. Many of its features later appeared in bash.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The bash shell",
        originalTitle = "Bourne Again Shell (bash)",
        author = "Brian Fox, GNU project",
        year = 1989,
        summary = "The GNU project's shell (1989): compatible with the Bourne shell and incorporating ideas from csh and ksh.",
        details = "bash became the standard shell of Linux and macOS. Its syntax still underlies many scripts.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The gawk utility",
        originalTitle = "GNU awk (gawk)",
        author = "Paul Rubin and Jay Fenlason, GNU project",
        year = 1988,
        summary = "A free implementation of AWK from the GNU project (1988) with extensions for text and arrays.",
        details = "gawk developed AWK beyond the original version and added extensions to it. It is often used in data-processing scripts.",
        rare = true,
    ),

    // ---------- Communications and file transfer ----------

    entry(
        kind = Kind.TOOL,
        section = "Communications and file transfer",
        title = "The Kermit protocol",
        originalTitle = "Kermit",
        author = "Columbia University",
        year = 1981,
        summary = "A file-transfer protocol and program (1981) for moving files between different computers over a serial port.",
        details = "Kermit ran on almost any machine. It was used to move files between mainframes, PCs and Unix.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Communications and file transfer",
        title = "The XMODEM protocol",
        originalTitle = "XMODEM",
        author = "Ward Christensen",
        year = 1977,
        summary = "A modem file-transfer protocol (1977) with a checksum for each block: the basis of many BBSs.",
        details = "XMODEM transferred a file in 128-byte blocks with verification. YMODEM and ZMODEM came later.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Communications and file transfer",
        title = "The uucp utility",
        originalTitle = "uucp (Unix to Unix copy)",
        author = "David Nowitz, Michael Lesk",
        year = 1978,
        summary = "Transfer of files and mail between Unix machines over telephone lines (1978).",
        details = "uucp ran on a schedule over a modem. uucp networks linked universities and companies before the Internet appeared.",
        rare = true,
    ),

    // ---------- Documents and typesetting ----------

    entry(
        kind = Kind.TOOL,
        section = "Documents and typesetting",
        title = "The LaTeX typesetting system",
        originalTitle = "LaTeX",
        author = "Leslie Lamport",
        year = 1984,
        summary = "A layer over TeX (1984): the structure of a document is set by commands for sections, formulas and references.",
        details = "LaTeX made it possible to write scientific articles and books without knowing low-level TeX. " +
            "It became the standard for scientific publications.",
    ),
)
