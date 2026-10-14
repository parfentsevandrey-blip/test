package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices and methods of the 1980s: style, design, quality, metrics, processes, optimization, data. */
val db1Practices: List<RecordEntity> = listOf(

    // ---------- Methods and style ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Structured programming",
        originalTitle = "Structured programming",
        author = "Dijkstra, Dahl, Hoare",
        year = 1972,
        summary = "A program is built from sequence, branching and loops, without arbitrary jumps.",
        details = "The Böhm–Jacopini theorem (1966) showed that any algorithm can be written with three basic constructs. " +
            "The book “Structured Programming” (1972) made this approach the norm. In the 80s Pascal and C encouraged blocks, " +
            "nested if and while, and procedures: code began to be read from top to bottom.",
        example = """
            { Pascal: count spaces and letters }
            while not eof do
            begin
              read(ch);
              if ch = ' ' then
                spaces := spaces + 1
              else
                letters := letters + 1
            end;
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Goto considered harmful",
        originalTitle = "Go To Statement Considered Harmful",
        author = "Edsger Dijkstra",
        year = 1968,
        summary = "The goto statement is harmful: the code no longer shows how the program reached the current point.",
        details = "Dijkstra's short letter to Communications of the ACM (1968) became one of the best-known texts about programming. " +
            "In the 80s a loop was exited with break, and error handling went through a single exit point. " +
            "Later the Linux kernel kept goto for releasing resources: there it really is appropriate.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Stepwise refinement",
        originalTitle = "Stepwise refinement",
        author = "Niklaus Wirth",
        year = 1971,
        summary = "A program is written top-down: a general outline first, then each step is refined into code.",
        details = "Wirth described the method in the article “Program Development by Stepwise Refinement” (1971). " +
            "Each step stays checkable, and extra details are postponed.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Unix pipelines",
        originalTitle = "Pipes and filters",
        author = "Douglas McIlroy",
        year = 1973,
        summary = "Each program does one job, and programs are connected through text streams.",
        details = "McIlroy proposed pipelines back in 1964, and they appeared in Unix in 1973. The Unix philosophy, described in the " +
            "Bell System Technical Journal (1978), advises doing one thing and doing it well, and treating text as a universal interface.",
        example = """
            cat words.txt | sort | uniq -c | sort -rn | head -10
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Literate programming",
        originalTitle = "Literate programming",
        author = "Donald Knuth",
        year = 1984,
        summary = "The program is written as a document for people, and the code and documentation are extracted from it automatically.",
        details = "Knuth described the approach in a 1984 article and created the WEB system. One file holds the reasoning and the pieces of code; " +
            "the tangle utility assembles the program, and weave produces the printed description. " +
            "A similar idea lives today in notebooks such as Jupyter.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Hungarian notation",
        originalTitle = "Hungarian notation",
        author = "Charles Simonyi",
        year = 1977,
        summary = "A prefix in a variable name hints at its type: lpszName is a pointer to a string, iCount is an integer.",
        details = "The convention was proposed by Charles Simonyi, who worked at Xerox PARC and then at Microsoft. " +
            "In the 80s it was used everywhere in Windows and Win32 API code. The plus: the type is visible in the name. " +
            "The minus: when the type changes, the prefix starts lying.",
        example = """
            LPSTR  lpszName;     /* lp: long pointer, sz: zero-terminated string */
            int    iCount;       /* i: integer */
            BOOL   bVisible;     /* b: boolean */
            HWND   hwndMain;     /* hwnd: window handle */
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Methods and style",
        title = "Elements of programming style",
        originalTitle = "The Elements of Programming Style",
        author = "Brian Kernighan, P. J. Plauger",
        year = 1974,
        summary = "Code is read more often than it is written: short functions, clear names, honest comments.",
        details = "The book analysed real programs and showed how to rewrite tangled code: remove needless jumps, give variables clear names, " +
            "split a long function. In the 80s these rules became the basis of internal company standards.",
    ),

    // ---------- Design ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Modules and information hiding",
        originalTitle = "Modular programming, information hiding",
        author = "David Parnas",
        year = 1972,
        summary = "The system is divided into modules by the decisions that may change. Only the interface sticks out.",
        details = "Parnas showed that decomposition by steps of an algorithm breaks when things change, while decomposition by hidden " +
            "decisions does not. In the 80s this became the basis of separate compilation and of Modula-2 and Ada. " +
            "The same idea lives on in modules and classes with private fields.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Model-View-Controller",
        originalTitle = "MVC",
        author = "Trygve Reenskaug",
        year = 1979,
        summary = "The data, its display and the handling of input are separated, so the same data can be shown in different ways.",
        details = "Reenskaug formulated the idea in 1979 while working on Smalltalk at Xerox PARC. In the 80s MVC became the basis of the " +
            "graphical interfaces of Smalltalk, and later of many GUI libraries and web frameworks. The model is tested without windows, " +
            "and the interface changes without rewriting the logic.",
        example = """
            Model       — data and rules: an account, a balance, a list of records
            View        — display of data: a window, a table, a chart
            Controller  — input: a click, a key press, a command
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Design by contract",
        originalTitle = "Design by Contract",
        author = "Bertrand Meyer",
        year = 1986,
        summary = "A procedure declares what must hold before the call (require) and what it guarantees afterwards (ensure).",
        details = "Meyer introduced the approach together with the Eiffel language (the first version, 1986). A contract divides responsibility: " +
            "the calling code must satisfy the precondition, and the procedure must deliver the postcondition. " +
            "The same thought lives today in assertions and unit tests.",
        example = """
            withdraw (amount: INTEGER)
                require
                    amount > 0
                    amount <= balance
                do
                    balance := balance - amount
                ensure
                    balance_reduced: balance = old balance - amount
                end
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Structured analysis",
        originalTitle = "Structured analysis, DFD",
        author = "Tom DeMarco; Yourdon and Constantine",
        year = 1978,
        summary = "First describe how data flows through the system, and only then write code.",
        details = "DeMarco's book “Structured Analysis and System Specification” (1978) and Yourdon and Constantine's “Structured Design” (1979) " +
            "set the requirements language of the 80s: data flow diagrams (DFD), the data dictionary and mini-specifications of processes.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Design",
        title = "Nassi–Shneiderman diagrams",
        originalTitle = "Nassi–Shneiderman diagrams",
        author = "Isaac Nassi, Ben Shneiderman",
        year = 1973,
        summary = "An algorithm is drawn as nested rectangles without arrows: arbitrary jumps are impossible.",
        details = "The notation appeared in 1973 as an answer to flowcharts with arrows, which encouraged goto. Each block is an action, " +
            "a branch or a loop, and the nesting is visible by width.",
    ),

    // ---------- Quality and testing ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Software inspections",
        originalTitle = "Software inspections",
        author = "Michael Fagan, IBM",
        year = 1976,
        summary = "A group checks the code formally by roles and a checklist. They look for defects, not for culprits.",
        details = "Fagan's method (IBM, 1976) is a structured review: planning, overview, individual checking, a meeting that records defects, " +
            "rework and follow-up. According to IBM, inspections found a notable share of errors more cheaply than tests. " +
            "It is a direct ancestor of modern code review.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Black-box and white-box testing",
        originalTitle = "Black-box and white-box testing",
        author = "Glenford Myers",
        year = 1979,
        summary = "Black box: tests from the specification; white box: tests from the structure of the code.",
        details = "Myers, in the book “The Art of Software Testing” (1979), separated two approaches. Black-box tests check that the program " +
            "does what the description says. White-box tests cover the branches and paths inside the code.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Boundary value analysis",
        originalTitle = "Boundary value analysis",
        author = "Glenford Myers",
        year = 1979,
        summary = "Errors most often sit on the boundaries of ranges, so the boundaries themselves are tested.",
        details = "If a field accepts values from 1 to 100, check 0, 1, 2, 99, 100 and 101. This covers the inside of the range, its edges " +
            "and the values beyond them. The technique is simple and catches off-by-one errors well.",
        example = """
            Range: 1..100
            Tests:  0 (below the boundary), 1 (lower boundary), 2,
                    99, 100 (upper boundary), 101 (above the boundary)
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Hoare logic",
        originalTitle = "Hoare logic",
        author = "Charles Antony Richard Hoare",
        year = 1969,
        summary = "The triple {P} S {Q}: if P holds before the statement S, then Q holds after it. This is how correctness is proved.",
        details = "Hoare proposed an axiomatic way of proving programs (1969). In the 80s it remained an academic tool, " +
            "but its ideas went into design by contract and static analysis.",
        example = """
            { x >= 0 }   x := x + 1   { x >= 1 }
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Quality and testing",
        title = "Cleanroom",
        originalTitle = "Cleanroom software engineering",
        author = "Harlan Mills and colleagues, IBM",
        year = 1987,
        summary = "The program is written without debugging on a computer: strict verification by reasoning and statistical testing.",
        details = "The method was proposed at IBM in 1987. Errors are sought by proofs and inspections, and reliability is estimated " +
            "from statistics of tests that repeat real usage scenarios.",
        rare = true,
    ),

    // ---------- Metrics ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Metrics",
        title = "Halstead metrics",
        originalTitle = "Software science",
        author = "Maurice Halstead",
        year = 1977,
        summary = "A program is measured by the number of operators and operands: from these the “volume” and “difficulty” are computed.",
        details = "Halstead published “Elements of Software Science” (1977). The metrics were computed by simple counts, and many criticised them, " +
            "but it was the first quantitative approach to code complexity.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Metrics",
        title = "Cyclomatic complexity",
        originalTitle = "Cyclomatic complexity",
        author = "Thomas McCabe",
        year = 1976,
        summary = "The number of independent paths in the program's graph shows how many tests are needed for full coverage.",
        details = "McCabe proposed measuring complexity by the control-flow graph. A value above 10 was often considered a sign of a function " +
            "that is hard to understand and test.",
        rare = true,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Metrics",
        title = "Function points",
        originalTitle = "Function points",
        author = "Allan Albrecht, IBM",
        year = 1979,
        summary = "The size of a system is measured by the functions it offers the user, not by lines of code.",
        details = "The method was first presented in 1979 and published in 1983. Inputs, outputs, queries, files and interfaces are counted, " +
            "each type multiplied by a weight coefficient.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Metrics",
        title = "COCOMO",
        originalTitle = "COCOMO",
        author = "Barry Boehm",
        year = 1981,
        summary = "Labour is estimated from the size of the code in thousands of lines and from project factors.",
        details = "The model is described in the book “Software Engineering Economics” (1981). The basic formula: effort = a·(KLOC)^b, " +
            "and the coefficients depend on the type of project.",
    ),

    // ---------- Processes and teams ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "Waterfall model",
        originalTitle = "Waterfall model",
        author = "Winston Royce",
        year = 1970,
        summary = "Development goes strictly through phases: requirements, design, code, tests, maintenance. There is no going back.",
        details = "Royce's article (1970) described this order, but the author himself warned that without returning to earlier phases " +
            "a project risks failure. In the 80s the model was consolidated by standards, for example the American DOD-STD-2167 (1985).",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "Spiral model",
        originalTitle = "Spiral model",
        author = "Barry Boehm",
        year = 1986,
        summary = "Development goes in loops. Each loop: objectives, risk assessment, a prototype and a plan for the next loop.",
        details = "Boehm described the spiral model in 1986. Unlike the waterfall, risks are analysed explicitly here, and each loop " +
            "may start with a prototype.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "Rapid prototyping",
        originalTitle = "Rapid prototyping",
        author = "Practice of the 80s",
        year = 1984,
        summary = "First a working mock-up of the interface is built, then the system is reworked according to the customer's reaction.",
        details = "With the appearance of graphical interfaces (Macintosh, 1984) and fourth-generation languages (4GL), a screen mock-up " +
            "became quick to make. The customer sees an almost finished program and corrects the requirements before they turn into expensive code.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "The Mythical Man-Month",
        originalTitle = "The Mythical Man-Month",
        author = "Fred Brooks",
        year = 1975,
        summary = "Adding people to a late project makes it even later.",
        details = "Brooks, who led the development of IBM OS/360, described a law that every manager knows: work cannot be divided " +
            "evenly among people, because the costs of communication and coordination grow. Hence the rules: small teams and clear interfaces.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "Chief programmer team",
        originalTitle = "Chief Programmer Team",
        author = "Terry Baker, Harlan Mills, IBM",
        year = 1972,
        summary = "The chief programmer writes the key code and makes the decisions, while the team takes on the rest.",
        details = "IBM's idea of the early 70s: a chief programmer, an assistant, a project administrator, a source librarian and a tester. " +
            "In the 80s the approach was used in large contracts, but it was criticised for depending on one specialist.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Processes and teams",
        title = "Egoless programming",
        originalTitle = "Egoless programming",
        author = "Gerald Weinberg",
        year = 1971,
        summary = "Code is not an extension of its author's personality: it is calmly checked, criticised and rewritten.",
        details = "The term appeared in Weinberg's book “The Psychology of Computer Programming” (1971). In the 80s this idea became " +
            "the cultural basis of reviews: the code is criticised, not the people, and a mistake becomes a reason to improve the process.",
    ),

    // ---------- Optimization and memory ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Optimization and memory",
        title = "Cycle-level optimization",
        originalTitle = "Cycle-level optimization",
        author = "Game and demoscene programmers",
        year = 1981,
        summary = "Critical sections are written in assembler and counted in cycles: every processor cycle mattered.",
        details = "In the 80s games for the IBM PC, ZX Spectrum, Commodore 64 and Amiga were written in assembler. Typical techniques: " +
            "unrolled loops, tables instead of computation, direct access to video memory, and string instructions that do the work in one pass.",
        example = """
            ; Clearing the 80x25 text screen: 2000 cells of 2 bytes each
            mov ax, 0B800h        ; video memory segment (colour text)
            mov es, ax
            xor di, di            ; offset 0
            mov ax, 0720h         ; 07 — attribute (white on black), 20 — space
            mov cx, 2000          ; number of words
            rep stosw             ; store AX into ES:DI, CX times
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Optimization and memory",
        title = "Fixed-point arithmetic",
        originalTitle = "Fixed-point arithmetic",
        author = "Practice of the 80s",
        year = 1980,
        summary = "Fractions are stored as integers with an implicit scale: the coprocessor was expensive and not in every PC.",
        details = "The Intel 8087 coprocessor (1980) was expensive and not in every computer. Fractional values were written as integers: " +
            "the high bits are the integer part, the low bits the fraction. Addition stays ordinary, and after multiplication the result is shifted.",
        example = """
            #include <stdio.h>

            typedef int fix8;                  /* 8.8 format: the high 8 bits are the integer part */
            #define FX_ONE 256                 /* 1.0 */
            #define FX_MUL(a, b) (((long)(a) * (b)) >> 8)

            int main(void)
            {
                fix8 x = 384;                  /* 1.5 */
                fix8 y = FX_MUL(x, x);         /* 2.25, that is 576 */

                printf("%d\n", y);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Optimization and memory",
        title = "Double buffering",
        originalTitle = "Double buffering, page flipping",
        author = "Practice of the 80s",
        year = 1985,
        summary = "A frame is drawn in an invisible buffer and shown whole: no flicker and no torn picture.",
        details = "The Amiga and the Atari ST (1985), as well as the EGA and VGA video modes, could hold several video pages. " +
            "While one page is shown, the next frame is drawn in the other, and then the video memory pointer is switched.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Optimization and memory",
        title = "Overlays",
        originalTitle = "Overlays",
        author = "MS-DOS practice",
        year = 1981,
        summary = "A program is bigger than the available memory: its parts are loaded from disk in turn into the same place.",
        details = "MS-DOS addressed 640 KB, and large programs — compilers, CAD systems, editors — did not fit into it. " +
            "They were split into overlays: only the current part lay in memory. Turbo Pascal had a ready-made Overlay module for this.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Optimization and memory",
        title = "Garbage collection",
        originalTitle = "Garbage collection",
        author = "John McCarthy",
        year = 1958,
        summary = "The environment itself finds and frees memory that nothing refers to any more.",
        details = "The garbage collector was invented for Lisp (1958). In the 80s it was used by Smalltalk and Lisp machines, while PC programmers " +
            "called malloc and free by hand and often made mistakes: memory leaks and dangling pointers were the plague of the era.",
    ),

    // ---------- Data and storage ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Data and storage",
        title = "Relational model",
        originalTitle = "Relational model",
        author = "Edgar F. Codd",
        year = 1970,
        summary = "Data lives in tables, links are expressed by key values, and a query describes the desired result.",
        details = "Codd's article “A Relational Model of Data for Large Shared Data Banks” (1970) separated the logic of data from the way it is stored. " +
            "In the 80s relational DBMSs displaced hierarchical and network databases: design became simpler and queries more flexible.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Data and storage",
        title = "Entity-relationship model",
        originalTitle = "Entity-Relationship model",
        author = "Peter Pin-Shan Chen",
        year = 1976,
        summary = "A subject area is described by entities, their attributes and relationships, and then translated into tables.",
        details = "Chen's article “The Entity-Relationship Model — Toward a Unified View of Data” (1976) set the language of database design. " +
            "An ER diagram on paper is a short path to a table schema.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Data and storage",
        title = "Normal forms",
        originalTitle = "Normal forms: 1NF, 2NF, 3NF, BCNF",
        author = "Edgar F. Codd",
        year = 1970,
        summary = "Tables are split so that each fact is stored in one place and is not duplicated.",
        details = "Codd introduced the first normal form (1970), the second and third (1971), and the Boyce–Codd normal form (1974). " +
            "Duplicates seem harmless until, when a client's address changes, hundreds of rows have to be edited.",
        example = """
            -- Bad: the client's address is repeated in every order
            CREATE TABLE orders_flat (
                order_id       INTEGER PRIMARY KEY,
                client_name    TEXT,
                client_address TEXT,
                item           TEXT
            );

            -- Better: the client is stored only once
            CREATE TABLE clients (
                client_id INTEGER PRIMARY KEY,
                name      TEXT,
                address   TEXT
            );

            CREATE TABLE orders (
                order_id  INTEGER PRIMARY KEY,
                client_id INTEGER REFERENCES clients (client_id),
                item      TEXT
            );
        """,
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Data and storage",
        title = "B-trees for indexes",
        originalTitle = "B-tree",
        author = "Rudolf Bayer, Edward McCreight",
        year = 1970,
        summary = "A balanced tree for indexes: a search takes a few disk reads even with millions of records.",
        details = "Bayer and McCreight proposed the B-tree in 1970 (the article appeared in 1972). The node was made the size of a disk block: " +
            "one read — one node, and the height of the tree stays small.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Data and storage",
        title = "Transactions and ACID",
        originalTitle = "Transactions, ACID",
        author = "Jim Gray; Theo Haerder and Andreas Reuter",
        year = 1981,
        summary = "A group of operations is executed in full or not at all. This is how bank transfers work.",
        details = "Jim Gray described the concept of a transaction in 1981. The acronym ACID for the four properties (atomicity, consistency, " +
            "isolation, durability) was proposed by Haerder and Reuter in 1983. Without transactions a transfer could be debited but not credited.",
    ),

    // ---------- Culture and openness ----------

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Shareware",
        originalTitle = "Shareware",
        author = "Authors of PC-Talk and PC-Write",
        year = 1982,
        summary = "The program is given out to try for free, and you pay if you like it. Copies travelled on floppies and BBSs.",
        details = "PC-Talk (1982) and PC-Write (1983) were distributed this way: a copy could be passed to a friend, and the author asked " +
            "for payment if the program proved useful. It was an alternative to expensive boxed packages.",
    ),

    entry(
        kind = Kind.PRACTICE,
        section = "Culture and openness",
        title = "Free software: GNU and GPL",
        originalTitle = "GNU, GPL, copyleft",
        author = "Richard Stallman",
        year = 1983,
        summary = "The program can be read, changed and passed on. Derived works also remain free.",
        details = "The GNU project was announced in 1983, the “GNU Manifesto” was published in 1985, and the first GNU GPL licence appeared in 1989. " +
            "The idea of copyleft: the licence does not let the source code of a derived work be closed. For the 80s this was unusual.",
    ),
)
