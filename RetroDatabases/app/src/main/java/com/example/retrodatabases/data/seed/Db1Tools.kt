package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of the 1980s: build, compilers, editors, debugging, typesetting and the command line. */
val db1Tools: List<RecordEntity> = listOf(

    // ---------- Build and analysis ----------

    entry(
        kind = Kind.TOOL,
        section = "Build and analysis",
        title = "The make utility",
        originalTitle = "make",
        author = "Stuart Feldman, Bell Labs",
        year = 1976,
        summary = "A recipe file describes which files depend on which and how to build them. Only what has changed is rebuilt.",
        details = "The make utility appeared at Bell Labs in 1976. It compares the modification times of files and runs only the commands that are needed. " +
            "In the 80s the Makefile was the standard for C projects on Unix, and many projects still build this way.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Build and analysis",
        title = "The lint static analyzer",
        originalTitle = "lint",
        author = "Stephen Johnson, Bell Labs",
        year = 1978,
        summary = "Checks C code before it runs and finds suspicious spots that the compiler lets through.",
        details = "Lint caught mismatched argument types, variables used before assignment, and code that could never run. " +
            "In the 80s it was run before a release. It is a direct ancestor of modern static analyzers.",
        example = """
            int average(a, n)
            int *a;
            int n;
            {
                int sum, i;
                for (i = 0; i < n; i++)
                    sum += a[i];       /* lint: sum is used before it is assigned */
                return sum / n;
            }
        """,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Build and analysis",
        title = "The gprof profiler",
        originalTitle = "gprof",
        author = "Graham, Kessler, McKusick (Berkeley)",
        year = 1982,
        summary = "Shows how much time functions take and who calls whom.",
        details = "gprof built a call graph and distributed the running time among the functions. " +
            "Knuth's phrase “premature optimization is the root of all evil” (1974) is about exactly this kind of measurement.",
    ),

    // ---------- Compilers and generators ----------

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "The yacc parser generator",
        originalTitle = "yacc",
        author = "Stephen Johnson, Bell Labs",
        year = 1975,
        summary = "Instead of parsing a language by hand, you describe its grammar and the generator builds the parser.",
        details = "yacc built a parser from a grammar description. Together with lex it became the standard Unix pair for compilers, " +
            "calculators and configuration languages.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "The lex lexical analyzer",
        originalTitle = "lex",
        author = "Mike Lesk and Eric Schmidt, Bell Labs",
        year = 1975,
        summary = "Splits text into tokens using regular expressions; works in tandem with yacc.",
        details = "lex turns a set of patterns into code that finds numbers, identifiers and symbols in the text of a program. " +
            "Its output was passed to the yacc parser.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "Bison",
        originalTitle = "GNU Bison",
        author = "Robert Corbett, GNU project",
        year = 1985,
        summary = "The free equivalent of yacc from the GNU project: the same approach, open source.",
        details = "Bison appeared in 1985 and replaced yacc in many free projects. Compiler parsers were generated with it, GCC's among them.",
        rare = true,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Compilers and generators",
        title = "GCC compiler",
        originalTitle = "GNU C Compiler",
        author = "Richard Stallman, GNU project",
        year = 1987,
        summary = "A free C compiler that runs on dozens of architectures; it displaced many proprietary compilers.",
        details = "GCC appeared in 1987 as part of the GNU project. Its architecture was ported to new processors, " +
            "and by the end of the 80s the compiler had become the standard for Unix systems.",
    ),

    // ---------- Editors and environments ----------

    entry(
        kind = Kind.TOOL,
        section = "Editors and environments",
        title = "Turbo Pascal integrated environment",
        originalTitle = "Turbo Pascal IDE",
        author = "Borland, Anders Hejlsberg",
        year = 1983,
        summary = "An editor, compiler and run command in one program; errors are shown right in the text.",
        details = "Turbo Pascal 1.0 (1983) compiled a program in memory in seconds and showed errors right in the editor. " +
            "The edit-run cycle became fast and comfortable, and this environment set the standard for the PC. " +
            "Later Hejlsberg created Delphi and C#.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Editors and environments",
        title = "The vi editor",
        originalTitle = "vi (ex/vi)",
        author = "Bill Joy, Berkeley",
        year = 1976,
        summary = "The visual Unix editor with command and insert modes; it is still on servers today.",
        details = "The editor was written by Bill Joy. In one mode the keys mean commands, in the other they mean text input. " +
            "The model was controversial, but vi remains an indispensable tool for administrators.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Editors and environments",
        title = "The Emacs editor",
        originalTitle = "Emacs",
        author = "Richard Stallman",
        year = 1976,
        summary = "An extensible editor: the first version was a set of macros, and GNU Emacs (1985) is extended in Lisp.",
        details = "The first Emacs (1976) worked as a set of macros for TECO. GNU Emacs (1985) was rewritten so that the editor became an environment: " +
            "mail, debugging and help are read inside it.",
    ),

    // ---------- Debugging and versions ----------

    entry(
        kind = Kind.TOOL,
        section = "Debugging and versions",
        title = "The GDB debugger",
        originalTitle = "GDB",
        author = "Richard Stallman, GNU project",
        year = 1986,
        summary = "The GNU debugger: breakpoints, memory inspection and stack traces.",
        details = "GDB appeared in 1986 and worked with C programs compiled by GCC. " +
            "Commands such as “break” and “step” became a model for later debuggers.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Debugging and versions",
        title = "SCCS and RCS version control",
        originalTitle = "SCCS, RCS",
        author = "Marc Rochkind (SCCS); Walter Tichy (RCS)",
        year = 1972,
        summary = "Every change to a file is saved: the history can be viewed, compared and rolled back.",
        details = "SCCS (1972, Bell Labs) and RCS (1982, Walter Tichy) were the first widely used version control systems. " +
            "In the 80s the tools kept the history inside the files and marked releases. These ideas lived on in Git.",
    ),

    // ---------- Documents and typesetting ----------

    entry(
        kind = Kind.TOOL,
        section = "Documents and typesetting",
        title = "The TeX typesetting system",
        originalTitle = "TeX",
        author = "Donald Knuth",
        year = 1978,
        summary = "A typesetting system with formulas and high-quality typography; Knuth created it because of the poor typesetting of his book.",
        details = "Knuth started TeX, unhappy with the print quality of his work “The Art of Computer Programming”. " +
            "Formulas and hyphenation in TeX are computed by rules, not by eye, which is why scientific papers around the world are typeset this way.",
    ),

    entry(
        kind = Kind.TOOL,
        section = "Documents and typesetting",
        title = "The troff typesetting system",
        originalTitle = "troff, nroff",
        author = "Joe Ossanna, Bell Labs",
        year = 1973,
        summary = "The Unix typesetting system: text with macro commands was printed on phototypesetters and printers.",
        details = "troff printed the Unix documentation, and the man pages were formatted with it from the 1970s. " +
            "Its syntax looks strange, but working with it was the standard for technical texts.",
        rare = true,
    ),

    // ---------- Command line ----------

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The sed stream editor",
        originalTitle = "sed",
        author = "Lee McMahon, Bell Labs",
        year = 1974,
        summary = "Edits text line by line using patterns, without opening the file in an interactive editor.",
        details = "sed read a data stream, applied commands to each line and printed the result. " +
            "It was valued for automating the editing of large files.",
        example = """
            sed 's/Unix/UNIX/g' notes.txt
        """,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The grep search tool",
        originalTitle = "grep",
        author = "Ken Thompson, Bell Labs",
        year = 1973,
        summary = "Finds lines by regular expressions; the name comes from an editor command in ed.",
        details = "grep appeared in Unix in 1973. The name comes from the g/re/p command of the ed editor: a global search with printing of the lines.",
        example = """
            grep -n "goto" *.c
        """,
    ),

    entry(
        kind = Kind.TOOL,
        section = "Command line",
        title = "The Bourne shell",
        originalTitle = "sh (Bourne shell)",
        author = "Stephen Bourne, Bell Labs",
        year = 1979,
        summary = "The Unix V7 command shell: variables, pipelines and scripts; the ancestor of bash.",
        details = "The shell from the seventh edition of Unix became the standard for scripts. " +
            "Its syntax still underlies bash and other modern shells.",
        example = """
            cat notes.txt | sort | uniq | wc -l
        """,
        rare = true,
    ),

    // ---------- Modelling ----------

    entry(
        kind = Kind.TOOL,
        section = "Modelling",
        title = "CASE tools",
        originalTitle = "CASE (Computer-Aided Software Engineering)",
        author = "The industry of the early 80s",
        year = 1982,
        summary = "Programs that draw diagrams, check the model and generate code and documentation.",
        details = "CASE tools promised to automate development, from DFD and ER diagrams to code. Some ideas took root: " +
            "entity diagrams and code generation from a schema. Many promises did not: heavy repositories and rigid processes put teams off.",
    ),
)
