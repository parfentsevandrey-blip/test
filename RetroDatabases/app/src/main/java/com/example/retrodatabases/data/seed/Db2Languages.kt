package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of the 1990s: the mainstream ones of the decade and some almost forgotten ones. */
val db2Languages: List<RecordEntity> = listOf(

    // ---------- Object-oriented ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Object-oriented",
        title = "Java",
        originalTitle = "Java (first called Oak)",
        author = "James Gosling and the Sun Microsystems team",
        year = 1995,
        summary = "A language that compiles to bytecode for a virtual machine, so one program runs on many systems.",
        details = "Work began in 1991 under the name Oak, and the language was renamed Java in 1995. " +
            "The promise was write once, run anywhere: javac produced bytecode, and the Java Virtual Machine ran it on Windows, Solaris or Macintosh. " +
            "There was garbage collection instead of manual memory management, and no pointers. " +
            "Netscape Navigator began running Java applets in 1995, which made the language famous within a year.",
        instructions = """
            1. Save the example as Hello.java.
            2. Compile it: javac Hello.java
            3. Run it: java Hello
        """,
        example = """
            public class Hello {
                public static void main(String[] args) {
                    java.util.Vector names = new java.util.Vector();
                    names.addElement("world");
                    names.addElement("Java 1.0");
                    for (int i = 0; i < names.size(); i++)
                        System.out.println("Hello, " + names.elementAt(i) + "!");
                }
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Object-oriented",
        title = "C++98 (ISO standard)",
        originalTitle = "C++",
        author = "Bjarne Stroustrup and the ISO C++ committee",
        year = 1998,
        summary = "The first ISO standard for C++: templates, the Standard Template Library and exceptions in one document.",
        details = "Stroustrup had been building C++ since 1979, when it was still C with Classes. " +
            "The Standard Template Library, written mainly by Alexander Stepanov and Meng Lee at Hewlett-Packard, entered the standard in 1998. " +
            "Containers and algorithms became generic, and programmers stopped writing their own linked lists for every project.",
        example = """
            #include <iostream>
            #include <map>
            #include <string>

            int main() {
                std::map<std::string, int> age;
                age["Ann"] = 31;
                age["Bob"] = 27;
                for (std::map<std::string, int>::iterator it = age.begin(); it != age.end(); ++it)
                    std::cout << it->first << " " << it->second << "\n";
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Object-oriented",
        title = "Delphi",
        originalTitle = "Delphi (Object Pascal)",
        author = "Anders Hejlsberg and the Borland team",
        year = 1995,
        summary = "Object Pascal with a visual form designer and native code, so programs start quickly.",
        details = "Hejlsberg had designed Turbo Pascal earlier. Delphi 1.0 (1995) brought the same fast compiler to Windows, " +
            "with components dropped onto a form and code attached to their events. " +
            "The compiler produced native machine code, so an application could be built in seconds.",
        example = """
            procedure TForm1.ButtonHelloClick(Sender: TObject);
            begin
              ShowMessage('Hello, ' + Edit1.Text);
            end;
        """,
    ),

    // ---------- Web and scripting ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "Perl 5",
        originalTitle = "Practical Extraction and Report Language",
        author = "Larry Wall",
        year = 1994,
        summary = "A text language for scripts: regular expressions, hashes and CGI programs in one place.",
        details = "Wall began Perl in 1987 for report processing. Version 5 (1994) added references, modules and objects. " +
            "Perl became the usual language of CGI scripts, the first server-side programs on the web. " +
            "Its motto was that there is more than one way to do it, and the CPAN archive of modules took shape in 1995.",
        example = """
            my %count;
            while (<STDIN>) {
                <DOLLAR>count{<DOLLAR>_}++ for split;
            }
            print "<DOLLAR>_ <DOLLAR>count{<DOLLAR>_}\n" for sort keys %count;
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "Python",
        originalTitle = "Python 1.x",
        author = "Guido van Rossum",
        year = 1994,
        summary = "A readable scripting language in which indentation marks the blocks; the name comes from Monty Python.",
        details = "Van Rossum began Python in 1989 at CWI in Amsterdam, and version 1.0 appeared in 1994. " +
            "Blocks are marked by indentation, so the code looks like a clear outline. " +
            "By version 1.5 the language had modules, exceptions and a growing standard library. " +
            "The name comes from the BBC show Monty Python's Flying Circus, not from the snake.",
        example = """
            # Written in the Python 1.5 style: no list comprehensions and no f-strings
            import sys
            total = 0
            for line in sys.stdin.readlines():
                fields = line.split(',')
                if len(fields) > 1:
                    total = total + float(fields[1])
            sys.stdout.write("total = %.2f\n" % total)
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "Ruby",
        originalTitle = "Ruby",
        author = "Yukihiro Matsumoto",
        year = 1995,
        summary = "A scripting language in which every value is an object, and blocks make iteration short.",
        details = "Matsumoto, known as Matz, began Ruby in 1993 and released it in 1995. " +
            "The design borrows from Perl, Smalltalk and Lisp. " +
            "A block passed to a method, such as each, lets a loop read like a sentence.",
        example = """
            [1, 2, 3].each { |n| puts n * n }
            prices = { "tea" => 2, "coffee" => 3, "cake" => 5 }
            total = 0
            prices.each { |item, price| total += price }
            puts "items: #{prices.size}, total: #{total}"
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "PHP/FI",
        originalTitle = "Personal Home Page Tools / Form Interpreter",
        author = "Rasmus Lerdorf",
        year = 1995,
        summary = "Tags inside HTML pages that run on the server: the first easy way to make a page dynamic.",
        details = "Lerdorf wrote small tools in 1994 to track visitors to his home page. In 1995 they were released as Personal Home Page Tools, " +
            "and a form interpreter was added, so the package became PHP/FI. " +
            "In 1997 Andi Gutmans and Zeev Suraski rewrote the core, and PHP 3 (1998) became the base of PHP as we know it.",
        example = """
            <?php
            <DOLLAR>visits = array("Ann" => 31, "Bob" => 27);
            echo "<table border=1>\n";
            foreach (<DOLLAR>visits as <DOLLAR>name => <DOLLAR>age) {
                echo "<tr><td><DOLLAR>name</td><td><DOLLAR>age</td></tr>\n";
            }
            echo "</table>\n";
            ?>
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "JavaScript",
        originalTitle = "Mocha, then LiveScript",
        author = "Brendan Eich at Netscape",
        year = 1995,
        summary = "A small scripting language that Netscape put into the browser so that pages could react to the reader.",
        details = "Eich designed the first version in 1995. The language went through the names Mocha and LiveScript, " +
            "and it became JavaScript in December 1995, when Netscape and Sun announced the name together. " +
            "Microsoft answered with JScript, and the standard ECMAScript followed in 1997.",
        instructions = """
            1. Save the example as check.js.
            2. Run it with Node.js: node check.js (the code uses only features of the 1995 language).
            3. In a 1996 browser, replace console.log with document.write.
        """,
        example = """
            function isEmail(text) {
                return /^[^@\s]+@[^@\s]+\.[a-z]+$/i.test(text);
            }
            var tests = ["ann@example.com", "not an email", "bob@site.org"];
            for (var i = 0; i < tests.length; i++) {
                console.log(tests[i] + " -> " + isEmail(tests[i]));
            }
        """,
    ),

    // ---------- Visual and event-driven ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Visual and event-driven",
        title = "Visual Basic 4",
        originalTitle = "Visual Basic 4.0",
        author = "Microsoft",
        year = 1995,
        summary = "Draw a window with the mouse, then write the code that runs when a button is clicked.",
        details = "Visual Basic 1.0 appeared in 1991 and version 4 in 1995, when it added 32-bit programs for Windows 95. " +
            "Each form and control had properties and events, and a program was a set of event handlers. " +
            "Visual Basic made desktop programs possible for people with little programming training, and a large share of business software in the 90s was written with it.",
        example = """
            Private Sub cmdHello_Click()
                MsgBox "Hello, " & txtName.Text
            End Sub
        """,
    ),

    // ---------- Embedded and games ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Embedded and games",
        title = "Tcl/Tk",
        originalTitle = "Tool Command Language / Toolkit",
        author = "John Ousterhout at Berkeley",
        year = 1991,
        summary = "A command language built on strings, with the Tk toolkit for quick graphical interfaces.",
        details = "Ousterhout created Tcl in 1988 so that tools could be scripted without writing C. " +
            "Tk (1991) gave Tcl buttons, menus and canvases, and a working form took only a few lines. " +
            "Expect, a Tcl tool for automating interactive programs, appeared around 1990.",
        example = """
            button .hello -text "Say hello" -command {puts "Hello!"}
            pack .hello
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Embedded and games",
        title = "Lua",
        originalTitle = "Lua (Portuguese for moon)",
        author = "Roberto Ierusalimschy, Luiz Henrique de Figueiredo and Waldemar Celes",
        year = 1994,
        summary = "A small language meant to be embedded in other programs, from games to configuration tools.",
        details = "Lua was built at PUC-Rio in Rio de Janeiro. Its single data structure, the table, serves as an array, a dictionary and an object. " +
            "The interpreter was small enough to ship inside a game, and it was embedded in many programs of the decade.",
        example = """
            player = { name = "Ann", lives = 3 }
            function hit(p)
                p.lives = p.lives - 1
                print(p.name .. " has " .. p.lives .. " lives")
            end
            hit(player)
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Embedded and games",
        title = "QuakeC",
        originalTitle = "QuakeC",
        author = "id Software",
        year = 1996,
        summary = "A C-like language that compiled the game rules into a data file, so players could change monsters and weapons.",
        details = "Quake (1996) kept its game logic in QuakeC. The compiler turned the source into a progs.dat file that the engine loaded at start-up. " +
            "Mod authors could change weapons and monsters without touching the engine's C code, and many Quake mods were written this way.",
        example = """
            void() W_FireRocket =
            {
                newmis = spawn();
                newmis.owner = self;
                newmis.movetype = MOVETYPE_FLYMISSILE;
            };
        """,
        rare = true,
    ),

    // ---------- Functional and logic ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Erlang",
        originalTitle = "Erlang",
        author = "Joe Armstrong, Robert Virding and Mike Williams at Ericsson",
        year = 1998,
        summary = "A language for telephone switches: light processes that talk by messages and restart when they crash.",
        details = "Ericsson built Erlang for telecom systems that must not stop. Processes are cheap, share no memory and talk only by messages. " +
            "A supervisor process restarts a failed one, which is the idea behind the phrase let it crash. " +
            "The language was released as open source in 1998.",
        example = """
            -module(echo).
            -export([start/0, loop/0]).

            start() -> spawn(echo, loop, []).

            loop() ->
                receive
                    {From, Msg} ->
                        From ! {echo, Msg},
                        loop()
                end.
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Haskell 98",
        originalTitle = "Haskell 98 Report",
        author = "The Haskell committee",
        year = 1998,
        summary = "A purely functional language with lazy evaluation and type classes, standardised as a common base.",
        details = "The first Haskell report appeared in 1990, after a committee of researchers merged several lazy languages. " +
            "Haskell 98 was the stable base that compilers agreed on, and universities used it for teaching. " +
            "Lazy evaluation means an expression is calculated only when its value is needed, so an infinite list can be written down.",
        example = """
            primes = sieve [2..]
              where sieve (p:xs) = p : sieve [x | x <- xs, x `mod` p /= 0]

            main = print (take 10 primes)
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "OCaml",
        originalTitle = "Objective Caml",
        author = "Xavier Leroy and the INRIA team",
        year = 1996,
        summary = "A functional language with type inference, a fast native compiler and objects on top of ML.",
        details = "The ML family grew at INRIA in France, and Caml was its branch. " +
            "Objective Caml (1996) added classes and objects while keeping the strict type checker. " +
            "Type inference found many errors before the program ran, and the compiler produced fast native code.",
        example = """
            let rec fact n = if n = 0 then 1 else n * fact (n - 1)
            let () = Printf.printf "%d\n" (fact 10)
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Functional and logic",
        title = "Standard ML '97",
        originalTitle = "Standard ML",
        author = "Robin Milner, Mads Tofte, Robert Harper and David MacQueen",
        year = 1997,
        summary = "A typed functional language with modules, defined so precisely that its report is a formal document.",
        details = "The Definition of Standard ML (1990) fixed the meaning of every construct, and a revised edition appeared in 1997. " +
            "The module system, with signatures and functors, influenced later languages. " +
            "Several research compilers ran the language, and many universities taught from it.",
        example = """
            fun fact 0 = 1
              | fact n = n * fact (n - 1);
            print (Int.toString (fact 10) ^ "\n");
        """,
        rare = true,
    ),

    // ---------- Systems and tools ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Systems and tools",
        title = "Oberon-2",
        originalTitle = "Oberon-2",
        author = "Niklaus Wirth and Hanspeter Mössenböck",
        year = 1991,
        summary = "Wirth's successor to Modula-2: type-bound procedures, a clean module system and a compact compiler.",
        details = "Oberon-2 extended the Oberon language, which Wirth and Jürg Gutknecht designed for the Oberon System (1987). " +
            "Its type-bound procedures act as methods, so objects can be written without a large class library. " +
            "The language was used for teaching and for building the Oberon system itself.",
        example = """
            TYPE Shape = POINTER TO ShapeDesc;
              ShapeDesc = RECORD x, y: INTEGER END;

            PROCEDURE (s: Shape) Move (dx, dy: INTEGER);
            BEGIN
              s.x := s.x + dx; s.y := s.y + dy
            END Move;
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Systems and tools",
        title = "Ada 95",
        originalTitle = "Ada 95 (ISO/IEC 8652:1995)",
        author = "ISO working group, based on the Ada 83 design for the US Department of Defense",
        year = 1995,
        summary = "The revised Ada standard, which added object-oriented programming and kept the strict checking of the original.",
        details = "Ada 83 was designed for the US Department of Defense. The 1995 revision added tagged types, inheritance and protected objects. " +
            "Ada stayed the language of many safety-critical projects, such as avionics, where its checks and packages were valued.",
        example = """
            with Ada.Text_IO; use Ada.Text_IO;
            procedure Hello is
            begin
               Put_Line ("Hello, Ada 95");
            end Hello;
        """,
        rare = true,
    ),

    // ---------- Forgotten and niche ----------

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Forgotten and niche",
        title = "Euphoria",
        originalTitle = "Euphoria",
        author = "Robert Craig",
        year = 1993,
        summary = "A small interpreted language for DOS and Windows whose only data structure is the sequence.",
        details = "Craig released Euphoria in 1993 as a quick way to write programs for the PC. " +
            "The sequence covers arrays, strings and records, so there is only one structure to learn.",
        example = """
            sequence names = {"ann", "bob"}
            for i = 1 to length(names) do
                puts(1, names[i] & "\n")
            end for
        """,
        rare = true,
    ),

    entry(
        databaseId = 2,
        kind = Kind.LANGUAGE,
        section = "Forgotten and niche",
        title = "AppleScript",
        originalTitle = "AppleScript",
        author = "Apple Computer",
        year = 1993,
        summary = "English-like scripting for the Macintosh: a script asks other programs to work through Apple events.",
        details = "AppleScript came with System 7.1 in 1993. Its sentence-like syntax was meant for people who were not programmers, " +
            "and a script could drive a word processor or a spreadsheet through the same messages that the user's clicks produced. " +
            "Many office workflows on the Mac ran on it.",
        example = """
            tell application "Finder"
                open folder "Documents" of startup disk
            end tell
        """,
        rare = true,
    ),
)
