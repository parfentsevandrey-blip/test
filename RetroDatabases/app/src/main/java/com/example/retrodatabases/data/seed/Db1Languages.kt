package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of the 1980s and their ancestors: from mass-market to almost forgotten. */
val db1Languages: List<RecordEntity> = listOf(

    // ---------- Imperative and system ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Pascal",
        originalTitle = "Pascal",
        author = "Niklaus Wirth",
        year = 1970,
        summary = "A strict teaching language with types, blocks and procedures; it taught people to write neatly.",
        details = "Wirth created Pascal in 1970 as a language for teaching programming. " +
            "In the 80s it became the main language of university courses, and Turbo Pascal (1983) made it mass-market on the PC. " +
            "The compiler checked types before the program ran, which was a big step for those years.",
        example = """
            program Average;
            var
              a, b, sum: integer;
            begin
              a := 7;
              b := 5;
              sum := a + b;
              writeln('Sum = ', sum);
            end.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "C",
        originalTitle = "C",
        author = "Dennis Ritchie",
        year = 1972,
        summary = "A systems programming language, in which Unix was rewritten.",
        details = "Ritchie created C in 1972, and in 1973 the Unix kernel was rewritten in it. " +
            "Kernighan and Ritchie's book “The C Programming Language” (1978) became the textbook of the decade. " +
            "The ANSI C standard (C89) was adopted in 1989. The language gave portability: one source could be built on different machines.",
        example = """
            #include <stdio.h>

            int main(void)
            {
                int i;
                for (i = 1; i <= 5; i++)
                    printf("%d\n", i * i);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Ada",
        originalTitle = "Ada",
        author = "Jean Ichbiah and a team commissioned by the US Department of Defense",
        year = 1983,
        summary = "A language for large defence systems: packages, strong types, exceptions and parallel tasks.",
        details = "Ada was created to order for the US Department of Defense. The 1983 standard received the number MIL-STD-1815, " +
            "in honour of the year of Ada Lovelace's birth. The language was heavy, but it offered packages with a separate interface, " +
            "exception handling and parallel tasks built into the language.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Modula-2",
        originalTitle = "Modula-2",
        author = "Niklaus Wirth",
        year = 1978,
        summary = "A successor to Pascal with modules: the interface and the implementation are separated and compiled separately.",
        details = "Wirth developed Modula-2 in the late 1970s at ETH Zurich. A module consists of an interface description " +
            "and an implementation, so large programs were built in parts. The language was taught in Europe, " +
            "and the Medos-2 operating system was written in it.",
        example = """
            DEFINITION MODULE Stack;
              PROCEDURE Push(x: INTEGER);
              PROCEDURE Pop(): INTEGER;
            END Stack.
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Oberon",
        originalTitle = "Oberon",
        author = "Niklaus Wirth, Jürg Gutknecht",
        year = 1987,
        summary = "A simplified successor to Modula-2 with extended types; the operating system was written entirely in it.",
        details = "Wirth and Gutknecht created the language together with the Oberon System operating system. The language is smaller than Modula-2: " +
            "objects appear through record extension, not through separate constructs.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Mesa",
        originalTitle = "Mesa",
        author = "Xerox PARC team",
        year = 1977,
        summary = "Xerox PARC's language for systems programming: the software of the Alto and Star workstations was written in it.",
        details = "Mesa was created for large systems with modules and strict checking of interfaces. " +
            "Much of the software of Xerox workstations is written in it.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Concurrent Pascal",
        originalTitle = "Concurrent Pascal",
        author = "Per Brinch Hansen",
        year = 1975,
        summary = "Pascal with parallel processes and monitors: shared data is protected by the structure of the language.",
        details = "Brinch Hansen introduced monitors in 1975. The experimental operating system Solo (1976) was written in Concurrent Pascal, " +
            "and parallelism was checked directly by the compiler.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "PL/M",
        originalTitle = "PL/M",
        author = "Intel",
        year = 1973,
        summary = "Intel's language for the 8080 and 8086 processors: Pascal-like syntax and low-level capabilities.",
        details = "Created for programming Intel microprocessors. CP/M, the first mass-market OS for 8-bit machines, was written in PL/M.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "BCPL",
        originalTitle = "BCPL",
        author = "Martin Richards",
        year = 1967,
        summary = "A simple language for systems programming, the ancestor of the B and C languages.",
        details = "Richards created BCPL in Cambridge. The language was compiled for different machines through intermediate code, " +
            "so it was easy to port. B (Thompson) and then C grew out of BCPL.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "B",
        originalTitle = "B",
        author = "Ken Thompson",
        year = 1969,
        summary = "A typeless language for the PDP-7 minicomputer, an intermediate step from BCPL to C.",
        details = "Thompson simplified BCPL and created B for the PDP-7, where an early version of Unix ran. " +
            "Then Ritchie added types and got C.",
        rare = true,
    ),

    // ---------- Scientific and business ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Scientific and business",
        title = "FORTRAN",
        originalTitle = "FORTRAN",
        author = "John Backus's team, IBM",
        year = 1957,
        summary = "The oldest language for scientific calculations; in the 80s it was used for physics, mechanics and meteorology.",
        details = "FORTRAN (FORmula TRANslator) appeared at IBM in 1957. The language was optimised for numbers and arrays, " +
            "so numerical libraries of the 80s were often written in it.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scientific and business",
        title = "COBOL",
        originalTitle = "COBOL",
        author = "CODASYL committee, with Grace Hopper's participation",
        year = 1959,
        summary = "A business language of banks and insurers: data descriptions and phrases resembling English.",
        details = "COBOL was created by the CODASYL committee in 1959. In the 80s millions of lines of COBOL ran in banks " +
            "and government institutions; the COBOL-74 and COBOL-85 standards consolidated the language.",
        example = """
                   IDENTIFICATION DIVISION.
                   PROGRAM-ID. HELLO.
                   PROCEDURE DIVISION.
                       DISPLAY 'HELLO, WORLD'.
                       STOP RUN.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scientific and business",
        title = "ALGOL 60",
        originalTitle = "ALGOL 60",
        author = "International committee",
        year = 1960,
        summary = "A model for future languages: blocks, recursion and the BNF notation for syntax.",
        details = "The 1960 report defined the structure of block-structured languages. The Backus–Naur form (BNF) for describing syntax " +
            "appeared right here. ALGOL-60 was also used in the USSR: there were translators for the BESM-6.",
        example = """
            begin
              integer i, s;
              s := 0;
              for i := 1 step 1 until 10 do
                s := s + i
            end
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scientific and business",
        title = "ALGOL 68",
        originalTitle = "ALGOL 68",
        author = "IFIP committee",
        year = 1968,
        summary = "A complex language with a flexible type system and expressions; powerful, but hard to implement.",
        details = "ALGOL 68 was designed as the successor of ALGOL 60. The rich type system and expressions that double as statements " +
            "were powerful, but the compilers turned out bulky, and the language never became mass-market.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scientific and business",
        title = "PL/I",
        originalTitle = "PL/I",
        author = "IBM",
        year = 1964,
        summary = "IBM's general-purpose language: it was meant to replace FORTRAN, COBOL and ALGOL all at once.",
        details = "It turned out complicated: many constructs and exceptions. The language lived on IBM mainframes and in the 80s remained " +
            "in large corporate systems.",
        rare = true,
    ),

    // ---------- Object-oriented and parallel ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Simula 67",
        originalTitle = "Simula 67",
        author = "Ole-Johan Dahl, Kristen Nygaard",
        year = 1967,
        summary = "The first language with classes, objects and inheritance; the ancestor of Smalltalk and C++.",
        details = "The language was created in Norway for modelling systems. Classes, objects and virtual procedures, " +
            "which Simula introduced first, later became the basis of object-oriented programming.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Smalltalk-80",
        originalTitle = "Smalltalk-80",
        author = "Alan Kay and the Xerox PARC team",
        year = 1980,
        summary = "A program is a set of objects that keep state and communicate by messages.",
        details = "Smalltalk-80 was released at Xerox PARC in 1980. It made OOP practical: classes, inheritance, " +
            "windows and a mouse. In the early 80s C++ (Stroustrup, 1983) and Objective-C appeared; Smalltalk set the model for " +
            "an interactive development environment.",
        example = """
            "Create an account and put 100 in it"
            | account |
            account := BankAccount new.
            account deposit: 100.
            account balance printNl.
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Occam",
        originalTitle = "Occam",
        author = "INMOS, David May",
        year = 1983,
        summary = "A language of parallel processes for transputers: processes and channels are described right in the language.",
        details = "Occam is based on Hoare's CSP model. A program is a set of processes that exchange data over channels; " +
            "parallelism here is part of the language, not a library.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Erlang",
        originalTitle = "Erlang",
        author = "Joe Armstrong, Ericsson",
        year = 1986,
        summary = "A language for telephone systems: lightweight processes, messages and restarting of failing parts.",
        details = "Erlang was created inside Ericsson for reliable telecommunications systems. It was released publicly only in 1998, " +
            "but the idea of “let it crash and restart” already worked in the 80s.",
        rare = true,
    ),

    // ---------- Functional and logic ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Lisp",
        originalTitle = "Lisp",
        author = "John McCarthy",
        year = 1958,
        summary = "Programs are lists and functions. In the 80s Lisp machines and Common Lisp were at their peak.",
        details = "McCarthy created Lisp in 1958 for symbol processing and AI tasks. Common Lisp (specification 1984) " +
            "unified the dialects; Lisp machines were expensive, but they set the style of development.",
        example = """
            (defun factorial (n)
              (if (<= n 1)
                  1
                  (* n (factorial (- n 1)))))
            (factorial 5)  ; => 120
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Scheme",
        originalTitle = "Scheme",
        author = "Gerald Sussman, Guy Steele",
        year = 1975,
        summary = "A minimalist dialect of Lisp with lexical scoping; a teaching language of MIT.",
        details = "Scheme was created at MIT in 1975. The language became the basis of the book SICP (1985), " +
            "one of the best-known textbooks of the 80s.",
        example = """
            (define (fact n)
              (if (= n 0) 1 (* n (fact (- n 1)))))
            (fact 5)  ; => 120
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "ML",
        originalTitle = "ML, Standard ML",
        author = "Robin Milner, University of Edinburgh",
        year = 1973,
        summary = "A functional language with type inference and modules; the ancestor of OCaml and F#.",
        details = "ML appeared at the University of Edinburgh as a language for proofs. Type inference without extra annotations " +
            "and the module system made it a model for next-generation languages.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Miranda",
        originalTitle = "Miranda",
        author = "David Turner",
        year = 1985,
        summary = "A lazy purely functional language, the direct predecessor of Haskell.",
        details = "Miranda appeared in 1985 as a commercial language with convenient pattern matching. " +
            "Its ideas went into Haskell, created in 1990.",
        example = """
            fac 0 = 1
            fac n = n * fac (n - 1)
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Prolog",
        originalTitle = "Prolog",
        author = "Alain Colmerauer, Philippe Roussel",
        year = 1972,
        summary = "A program is facts and rules; computation is the search for a proof.",
        details = "Prolog was devised at the University of Marseille in 1972. In 1982 the Japanese “Fifth Generation Computers” project " +
            "made logic programming fashionable. In the 80s Prolog was used in expert systems and natural-language parsing.",
        example = """
            parent(anna, boris).
            parent(boris, vera).

            grandparent(X, Z) :- parent(X, Y), parent(Y, Z).

            % ?- grandparent(anna, vera).   → true
        """,
    ),

    // ---------- Educational and exotic ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "BASIC",
        originalTitle = "BASIC",
        author = "John Kemeny, Thomas Kurtz",
        year = 1964,
        summary = "A simple language for beginners: in the 80s almost all home computers were programmed in it.",
        details = "Kemeny and Kurtz created BASIC at Dartmouth College. Dialects were in the ROM of the ZX Spectrum, the Commodore 64 " +
            "and the Apple II; BASICA came with the IBM PC, and in the USSR it was in the BK-0010.",
        example = """
            10 PRINT "HELLO"
            20 GOTO 10
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "Logo",
        originalTitle = "Logo",
        author = "Wally Feurzeig, Seymour Papert, Cynthia Solomon",
        year = 1967,
        summary = "A turtle language: commands move an on-screen turtle and draw patterns.",
        details = "Logo was created to teach children programming. Papert described the ideas in the book “Mindstorms” (1980): " +
            "a child learns to think about processes by controlling a turtle. In the 80s Logo appeared on school computers.",
        example = """
            REPEAT 4 [FORWARD 100 RIGHT 90]
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "Forth",
        originalTitle = "Forth",
        author = "Charles Moore",
        year = 1970,
        summary = "A stack language: few keywords, compact code, fast work on weak machines.",
        details = "Moore created Forth in the late 1960s for controlling telescopes. A program is a dictionary of words, " +
            "and each word works with the stack. In the 80s Forth was valued in robotics and control, where memory and speed " +
            "were worth their weight in gold.",
        example = """
            : SQUARE  DUP * ;
            5 SQUARE .      \ prints 25
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "APL",
        originalTitle = "APL",
        author = "Kenneth Iverson",
        year = 1962,
        summary = "A language for arrays: one line replaces a whole loop, and code is written with special symbols.",
        details = "Iverson described the notation in the book “A Programming Language” (1962). APL programmers worked on a keyboard " +
            "with mathematical symbols and wrote very short code.",
        example = """
            +/⍳10   ⍝ sum of the numbers from 1 to 10: 55
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "Icon",
        originalTitle = "Icon",
        author = "Ralph Griswold",
        year = 1977,
        summary = "A string-processing language: searching and iterating over results are built-in generators.",
        details = "Griswold created Icon at the University of Arizona. An expression can return several values in turn, " +
            "so a search in text is written briefly.",
        example = """
            every write(find("a", "banana"))   # prints 2, 4 and 6
        """,
        rare = true,
    ),

    // ---------- Scripting and description languages ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "AWK",
        originalTitle = "AWK",
        author = "Aho, Weinberger, Kernighan",
        year = 1977,
        summary = "A language for processing tables: a pattern and an action are given for each line.",
        details = "The name of the language is made from the authors' surnames. In the 80s AWK was the standard Unix tool " +
            "for reports on text data.",
        example = """
            awk '{ sum += $2 } END { print sum }' data.txt
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "Perl",
        originalTitle = "Perl",
        author = "Larry Wall",
        year = 1987,
        summary = "A scripting language for reports and text processing with regular expressions.",
        details = "Perl 1.0 came out in 1987. Its convenience for working with text made it a favorite of system administrators, " +
            "and later the basis of the early web programs.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "REXX",
        originalTitle = "REXX",
        author = "Mike Cowlishaw, IBM",
        year = 1979,
        summary = "IBM's readable scripting language for VM/CMS, later for OS/2.",
        details = "Cowlishaw created REXX at IBM in 1979. The language replaced complicated command files and was understandable " +
            "to people without programming experience.",
        example = """
            /* REXX: sum from 1 to 10 */
            total = 0
            do i = 1 to 10
              total = total + i
            end
            say 'Sum =' total
        """,
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "SQL",
        originalTitle = "SQL (SEQUEL)",
        author = "Donald Chamberlin, Raymond Boyce, IBM",
        year = 1974,
        summary = "A query language: you describe which data you need, not how to get it.",
        details = "SEQUEL appeared at IBM in 1974. Commercial DBMSs of the 80s implemented the language in their own ways; " +
            "in 1986 the ANSI SQL-86 standard appeared, and in 1987 ISO adopted it.",
        example = """
            SELECT title, year
            FROM records
            WHERE kind = 'LANGUAGE'
            ORDER BY year;
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "PostScript",
        originalTitle = "PostScript",
        author = "John Warnock, Charles Geschke (Adobe)",
        year = 1982,
        summary = "A stack-based page description language: the printer runs a program that draws the page.",
        details = "Adobe created PostScript in 1982. Lines, text and fonts were set by commands, so one page printed identically " +
            "on any compatible printer.",
        example = """
            72 72 moveto
            200 200 lineto
            stroke
            showpage
        """,
    ),
)
