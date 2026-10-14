package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of the 1980s that were missing from the first edition: object-oriented, parallel, Lisp dialects and exotic ones. */
val db1ExtraLanguages: List<RecordEntity> = listOf(

    // ---------- Object-oriented and parallel ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "C++",
        originalTitle = "C++",
        author = "Bjarne Stroustrup, Bell Labs",
        year = 1983,
        summary = "C with classes (1979), renamed C++ in 1983: object-oriented programming built on C.",
        details = "Stroustrup extended C with classes, virtual functions and operator overloading. The first edition of the book “The C++ Programming Language” appeared in 1985. " +
            "The language became the main tool of commercial development from the late 80s.",
        example = """
            #include <iostream>

            class Counter {
            public:
                void increment() { ++value; }
                int get() const { return value; }
            private:
                int value = 0;
            };

            int main() {
                Counter c;
                c.increment();
                c.increment();
                std::cout << c.get() << "\n";
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Objective-C",
        originalTitle = "Objective-C",
        author = "Brad Cox, Tom Love",
        year = 1984,
        summary = "C with Smalltalk-style objects (1984): messages instead of direct function calls.",
        details = "Cox and Love added the Smalltalk message syntax to C. NeXT chose the language, and later it became the main language for Apple.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Eiffel",
        originalTitle = "Eiffel",
        author = "Bertrand Meyer",
        year = 1986,
        summary = "An object-oriented language with design by contract: preconditions and postconditions are part of the language.",
        details = "Eiffel was the practical embodiment of “design by contract”, which Meyer introduced for this language. The language was used in the financial sector.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Self",
        originalTitle = "Self",
        author = "David Ungar, Randall Smith",
        year = 1987,
        summary = "Prototype-based object programming (1987): objects are created by copying, without classes.",
        details = "Self influenced JIT compilation and the prototypes of JavaScript. The language was experimental, but much of it became standard.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Beta",
        originalTitle = "BETA",
        author = "Madsen, Møller-Pedersen, Nygaard (Aarhus University)",
        year = 1983,
        summary = "An object-oriented language (1983) in which classes, procedures and coroutines are combined into one construct.",
        details = "Beta is the successor of Simula. It has “patterns” that unite the concepts of classes and procedures.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "CLU",
        originalTitle = "CLU",
        author = "Barbara Liskov and colleagues, MIT",
        year = 1974,
        summary = "A language of data abstractions (1974): the first iterators and abstract types in a language.",
        details = "CLU introduced abstract data types and iterators. Its ideas influenced Ada and later languages with abstractions.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Object-oriented and parallel",
        title = "Linda",
        originalTitle = "Linda",
        author = "David Gelernter, Yale",
        year = 1985,
        summary = "A coordination model (1985): processes exchange data through a shared “tuple space”.",
        details = "Linda is not a language in the usual sense, but a set of operations on top of a language. " +
            "Its idea became the basis of many parallel systems.",
        rare = true,
    ),

    // ---------- Functional and logic ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Common Lisp",
        originalTitle = "Common Lisp",
        author = "Guy Steele and colleagues",
        year = 1984,
        summary = "Common Lisp (1984): a unified Lisp assembled from the Maclisp, Zetalisp and other dialects.",
        details = "The book “Common Lisp: The Language” (1984) set a common language for the whole Lisp family. It was used in AI, expert systems and CAD.",
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Franz Lisp",
        originalTitle = "Franz Lisp",
        author = "John Foderaro, Keith Sklower, Berkeley",
        year = 1980,
        summary = "Lisp for Unix (1980) from Berkeley: one of the first Lisps to run on minicomputers under Unix.",
        details = "Franz Lisp was used in universities for research and for teaching AI.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Zetalisp",
        originalTitle = "Zetalisp",
        author = "MIT and Symbolics",
        year = 1981,
        summary = "A Lisp dialect for the MIT and Symbolics Lisp machines (1981): the Flavors object system and windows.",
        details = "Zetalisp became the basis of the programming environment on Lisp machines. It was valued for interactivity and debugging.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "T (Scheme dialect)",
        originalTitle = "T",
        author = "Jonathan Rees, Norman Adams, Yale",
        year = 1984,
        summary = "A minimalist dialect of Scheme (1984) from Yale: lexical scoping and simplicity.",
        details = "T showed that Scheme could be made very small and still practical. The language became a teaching tool for compiler research.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Hope",
        originalTitle = "Hope",
        author = "Rod Burstall, David MacQueen, Donald Sannella",
        year = 1980,
        summary = "A purely functional language from Edinburgh (1980) with pattern matching and lazy lists.",
        details = "Hope became one of the predecessors of Miranda and Haskell. The language was studied at universities in Britain.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "FP",
        originalTitle = "FP",
        author = "John Backus",
        year = 1977,
        summary = "Backus's functional language (1977): programs are built from higher-order functions without variables.",
        details = "Backus proposed FP in the lecture “Can Programming Be Liberated from the von Neumann Style?”. " +
            "The idea influenced functional programming in the 80s.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Parlog",
        originalTitle = "Parlog",
        author = "Keith Clark, Steve Gregory, Imperial College",
        year = 1986,
        summary = "A parallel Prolog (1986): several goals are proved at the same time.",
        details = "Parlog belongs to the same body of work on parallel logic as the Japanese ICOT project, and builds on the ideas of Concurrent Prolog.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "KL1",
        originalTitle = "KL1",
        author = "The Japanese ICOT “Fifth Generation Computer” project",
        year = 1985,
        summary = "The parallel logic language of the Japanese project (1985) for the multiprocessor PIM machines.",
        details = "KL1 was built on parallel logic programs. The ICOT project was one of the largest experiments in logic programming.",
        rare = true,
    ),

    // ---------- Imperative and system ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Modula-3",
        originalTitle = "Modula-3",
        author = "Luca Cardelli, James Donahue and colleagues, DEC SRC",
        year = 1988,
        summary = "The successor of Modula-2 (1988) with objects, exceptions and threads built into the language.",
        details = "Modula-3 was created at the DEC SRC research centre. The language was chosen for systems programming, but it did not gain wide use.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Imperative and system",
        title = "Euclid",
        originalTitle = "Euclid",
        author = "Butler Lampson and colleagues, Xerox PARC",
        year = 1977,
        summary = "A language for verifiable programs (1977): checks and specifications are built into the code.",
        details = "Euclid was designed for systems programming with the ability to prove correctness. Its ideas influenced later members of the Pascal family.",
        rare = true,
    ),

    // ---------- Educational and exotic ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Educational and exotic",
        title = "QuickBASIC",
        originalTitle = "Microsoft QuickBASIC",
        author = "Microsoft",
        year = 1985,
        summary = "A compiled BASIC from Microsoft (1985): an integrated environment and fast code instead of an interpreter.",
        details = "QuickBASIC combined the editor with the compiler and the debugger. It replaced the slow BASIC interpreter on MS-DOS.",
    ),

    // ---------- Scripting and description languages ----------

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "Scribe",
        originalTitle = "Scribe",
        author = "Brian Reid, Carnegie Mellon",
        year = 1980,
        summary = "A document markup language (1980): the structure of the text is set by commands, not by formatting.",
        details = "Scribe influenced LaTeX. Its idea of separating content from presentation lives on in modern markup languages.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "HyperTalk",
        originalTitle = "HyperTalk",
        author = "Bill Atkinson, Apple",
        year = 1987,
        summary = "The scripting language of HyperCard (1987): programs are written right on the cards, in English.",
        details = "HyperTalk let non-programmers build applications out of cards and buttons. The language influenced the idea of scripting in interfaces.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "Clipper",
        originalTitle = "Clipper",
        author = "Nantucket Corporation",
        year = 1985,
        summary = "A compiler for the dBase language (1985): dBase programs were turned into executable files.",
        details = "Clipper compiled dBase programs into standalone executable files, which ran faster than the interpreter.",
        rare = true,
    ),

    entry(
        kind = Kind.LANGUAGE,
        section = "Scripting and description languages",
        title = "Tcl",
        originalTitle = "Tcl (Tool Command Language)",
        author = "John Ousterhout, Berkeley",
        year = 1988,
        summary = "An embeddable scripting language (1988): everything is a string, and commands can be added from a C program.",
        details = "Tcl was created to control design tools. Later, together with Tk, it gave a fast way to build interfaces from scripts.",
        rare = true,
    ),
)
