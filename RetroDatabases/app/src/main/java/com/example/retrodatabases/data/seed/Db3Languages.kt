package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Programming languages of 2000 to 2010: the new managed platforms, the dynamic and functional newcomers, and some playful ones. */
val db3Languages: List<RecordEntity> = listOf(

    // ---------- Object-oriented and managed ----------

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "C# 1.0",
        originalTitle = "C# (C-sharp)",
        author = "Anders Hejlsberg and the Microsoft team",
        year = 2002,
        summary = "A C-family language for the .NET Framework, with garbage collection, properties and delegates built in.",
        details = "Microsoft released C# 1.0 with the .NET Framework in 2002. Hejlsberg, who had designed Turbo Pascal and Delphi, led the design. " +
            "The syntax looked familiar to Java and C++ programmers, but properties, events and delegates were part of the language itself. " +
            "Visual Studio .NET gave the language a full IDE from the first day.",
        example = """
            using System;

            class Hello {
                static void Main() {
                    Console.WriteLine("Hello, C#");
                }
            }
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Visual Basic .NET",
        originalTitle = "Visual Basic .NET 2002",
        author = "Microsoft",
        year = 2002,
        summary = "Visual Basic on the .NET Framework: classes, inheritance and garbage collection for BASIC programmers.",
        details = "Visual Basic .NET (2002) broke compatibility with VB6 on purpose. Programs became classes compiled for the Common Language Runtime, " +
            "with inheritance and garbage collection. Many VB6 programmers found the move hard, and the community argued about it for years.",
        example = """
            Public Class Greeter
                Public Function Greet(name As String) As String
                    Return "Hello, " & name
                End Function
            End Class
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Java 5",
        originalTitle = "J2SE 5.0 (Tiger)",
        author = "Sun Microsystems (JSR 14 and JSR 175 expert groups)",
        year = 2004,
        summary = "Generics, annotations, varargs, autoboxing and the enhanced for loop, added to Java in one release.",
        details = "Java 5.0 (2004) was code-named Tiger. Generics (JSR 14) let collections state the type of their elements, " +
            "and annotations (JSR 175) gave tools a standard way to read metadata. Autoboxing, varargs and the enhanced for loop made everyday code shorter.",
        instructions = """
            1. Save the example as Java5Demo.java.
            2. Compile it: javac Java5Demo.java
            3. Run it: java Java5Demo
        """,
        example = """
            import java.util.*;

            public class Java5Demo {
                static int sum(int... values) {          // varargs
                    int total = 0;
                    for (int v : values) total += v;     // enhanced for loop
                    return total;
                }

                public static void main(String[] args) {
                    List<String> names = new ArrayList<String>();   // generics
                    names.add("Ann");
                    names.add("Bob");
                    for (String n : names) System.out.println(n);
                    Integer boxed = 40;                             // autoboxing
                    System.out.println(sum(1, 2, boxed));
                }
            }
        """,
        rare = false,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Scala",
        originalTitle = "Scala 1.0",
        author = "Martin Odersky and the EPFL team",
        year = 2004,
        summary = "A language for the JVM that mixes object-oriented and functional programming in one type system.",
        details = "Design began in 2001 at EPFL in Lausanne, and the first public release came in January 2004. " +
            "Scala compiles to Java bytecode and reuses the Java libraries. Its type system unites classes with functions used as values, " +
            "so the same code can be written in either style.",
        example = """
            object Hello extends App {
              val names = List("Ann", "Bob")
              names.map(_.toUpperCase).foreach(println)
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Groovy",
        originalTitle = "Groovy 1.0",
        author = "James Strachan, Bob McWhirter and the Groovy community",
        year = 2007,
        summary = "A dynamic language for the JVM with Java-like syntax, closures and builders for markup.",
        details = "Groovy was started by James Strachan and reached version 1.0 in January 2007 under JSR 241. " +
            "It accepts almost any Java code and adds closures, optional typing and builders for XML and other markup. " +
            "Groovy scripts that run from the command line made it popular for build and test tasks.",
        example = """
            def names = ['Ann', 'Bob']
            names.each { println "Hello, <DOLLAR>{it}" }
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "F#",
        originalTitle = "F# 1.0",
        author = "Don Syme and Microsoft Research",
        year = 2005,
        summary = "A functional language for .NET in the ML family, with type inference, pattern matching and immutable values.",
        details = "Don Syme at Microsoft Research built F# on the ML tradition. The first version appeared in 2005 as a research release for Visual Studio, " +
            "and the language shipped as a standard part of Visual Studio 2010.",
        example = """
            let rec fact n = if n = 0 then 1 else n * fact (n - 1)
            printfn "%d" (fact 10)
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Objective-C 2.0",
        originalTitle = "Objective-C 2.0",
        author = "Apple Computer",
        year = 2006,
        summary = "Smalltalk-style messages on top of C, with properties, fast enumeration and garbage collection for Mac OS X.",
        details = "Objective-C 2.0 (2006) added properties, fast enumeration and garbage collection for Mac OS X 10.5. " +
            "Messages between objects were the heart of the language, inherited from Smalltalk and used throughout Cocoa.",
        example = """
            @interface Counter : NSObject
            @property (nonatomic) int count;
            - (void)increment;
            @end
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Object-oriented and managed",
        title = "Pharo",
        originalTitle = "Pharo (a fork of Squeak)",
        author = "Stephane Ducasse, Marcus Denker and the Pharo team",
        year = 2008,
        summary = "A Smalltalk fork that began as a cleaner base for research and business, with a live development environment.",
        details = "A group of developers forked Squeak in March 2008 to clean up its code and make a stable base for research and commercial work. " +
            "The first numbered release, Pharo 1.0, appeared in April 2010. The live image and the browser tools stayed at the heart of the system.",
        example = """
            Transcript show: 'Hello, Pharo'; cr.
        """,
        rare = true,
    ),

    // ---------- Web and scripting ----------

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "Python 2.0",
        originalTitle = "Python 2.0",
        author = "Guido van Rossum and the Python community",
        year = 2000,
        summary = "List comprehensions, augmented assignment and a garbage collector for reference cycles, in a release many people kept for years.",
        details = "Python 2.0 (October 2000) added list comprehensions, augmented assignment operators and a garbage collector that could reclaim reference cycles. " +
            "Development moved to an open process with a public list of proposals (PEPs). Python 2 remained the everyday version until the 2010s.",
        instructions = """
            1. Save the example as squares.py.
            2. Run it: python squares.py
        """,
        example = """
            # Python 2.0 added list comprehensions and augmented assignment
            squares = [n * n for n in range(10) if n % 2 == 0]
            total = 0
            for value in squares:
                total += value
            print(squares)
            print(total)
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "PHP 5",
        originalTitle = "PHP 5.0 (Zend Engine II)",
        author = "Zend Technologies (Zeev Suraski and Andi Gutmans)",
        year = 2004,
        summary = "A rewritten object model with exceptions and visibility, which made PHP a serious choice for large sites.",
        details = "PHP 5.0 (July 2004) ran on the Zend Engine II, which gave PHP a real object model with constructors, private members and exceptions. " +
            "Sites that had outgrown PHP 4 moved to the new version, and frameworks built on it became common in the second half of the decade.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "ActionScript 3.0",
        originalTitle = "ActionScript 3.0",
        author = "Adobe (formerly Macromedia)",
        year = 2006,
        summary = "A class-based language for Flash 9 and the Flex framework, run by a much faster virtual machine.",
        details = "ActionScript 3.0 arrived with Flash 9 in 2006. It was based on the ECMAScript standard, with classes, typed variables " +
            "and the AVM2 virtual machine, which was much faster than the older one. Flex used it to build rich web applications.",
        example = """
            package {
              import flash.display.Sprite;

              public class Hello extends Sprite {
                public function Hello() {
                  trace("Hello, ActionScript 3");
                }
              }
            }
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Web and scripting",
        title = "Perl 6 (announced)",
        originalTitle = "Perl 6, later Raku",
        author = "Larry Wall and the Perl community",
        year = 2000,
        summary = "The rewrite of Perl announced in 2000 that took fifteen years to arrive; it was renamed Raku in 2019.",
        details = "Larry Wall announced Perl 6 in July 2000 as a clean redesign of Perl 5. The work took years, and the first production release came in 2015. " +
            "In 2019 the language was renamed Raku, to separate it from Perl 5.",
        example = """
            say "Hello from Perl 6";
            my @squares = (1..5).map({ <DOLLAR>_ * <DOLLAR>_ });
            say @squares;
        """,
        rare = true,
    ),

    // ---------- Functional and concurrent ----------

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Functional and concurrent",
        title = "Clojure",
        originalTitle = "Clojure 1.0",
        author = "Rich Hickey",
        year = 2007,
        summary = "A Lisp on the Java Virtual Machine with immutable data structures and easy access to Java libraries.",
        details = "Hickey released Clojure publicly in October 2007 after about two years of work. It runs on the JVM and reuses Java libraries. " +
            "Its persistent data structures are immutable, and its concurrency model is built on software transactional memory. Version 1.0 followed in 2009.",
        example = """
            (defn greet [name]
              (str "Hello, " name))

            (println (map greet ["Ann" "Bob"]))
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Functional and concurrent",
        title = "Go",
        originalTitle = "Go (Golang)",
        author = "Robert Griesemer, Rob Pike and Ken Thompson at Google",
        year = 2009,
        summary = "A compiled language with garbage collection, goroutines and channels, designed for servers with many cores.",
        details = "Google announced Go in November 2009. The design came from Griesemer, Pike and Thompson, who wanted fast builds and simple concurrency. " +
            "Goroutines are cheap threads, and channels pass values between them. The language reached version 1.0 in 2012.",
        instructions = """
            1. Save the example as hello.go.
            2. Run it: go run hello.go
        """,
        example = """
            package main

            import "fmt"

            func main() {
                done := make(chan string)
                go func() { done <- "hello from a goroutine" }()
                fmt.Println(<-done)
            }
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Functional and concurrent",
        title = "Haskell 2010",
        originalTitle = "The Haskell 2010 Report",
        author = "Simon Marlow (editor) and the Haskell community",
        year = 2010,
        summary = "The revised standard of the lazy functional language, built on the Haskell 98 base with a few additions.",
        details = "The Haskell 2010 Report fixed the standard after the Haskell 98 base had grown. Compilers such as GHC implemented it, " +
            "and the report became the reference for teaching and for libraries. Lazy evaluation stayed at the heart of the language.",
        example = """
            import Data.List (sort)

            main :: IO ()
            main = print (sort [3, 1, 2 :: Int])
        """,
        rare = true,
    ),

    // ---------- Creative coding ----------

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Creative coding",
        title = "Processing",
        originalTitle = "Processing",
        author = "Casey Reas and Ben Fry at the MIT Media Lab",
        year = 2001,
        summary = "A language and editor for artists and designers, built on Java, that draws a picture in a few lines.",
        details = "Reas and Fry began Processing in 2001 at the MIT Media Lab as a sketchbook for visual programming. " +
            "The language compiles to Java, and its editor runs a sketch with a single button. " +
            "Many interactive artworks and data visualizations started as Processing sketches.",
        example = """
            void setup() {
              size(200, 200);
            }

            void draw() {
              background(255);
              ellipse(mouseX, mouseY, 40, 40);
            }
        """,
    ),

    // ---------- Esoteric and playful ----------

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Esoteric and playful",
        title = "Whitespace",
        originalTitle = "Whitespace",
        author = "Edwin Brady and Chris Morris",
        year = 2003,
        summary = "An esoteric language whose programs consist only of spaces, tabs and line feeds; everything else is ignored.",
        details = "Brady and Morris published Whitespace in 2003 as a joke that is also a real language. " +
            "Instructions are made of spaces, tabs and line feeds, so a program looks blank to a reader who cannot see the characters. " +
            "An interpreter reads those three symbols as commands for a stack machine.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Esoteric and playful",
        title = "Shakespeare Programming Language",
        originalTitle = "SPL",
        author = "Karl Hasselbring",
        year = 2001,
        summary = "An esoteric language whose programs are plays: variables are characters and numbers come from the nouns they speak.",
        details = "Hasselbring released the Shakespeare Programming Language in 2001 as a parody of programming. " +
            "Each variable is a character in a play, and values come from the nouns in their lines. " +
            "A program is a sequence of lines spoken by the cast, and it ends when the play ends.",
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Esoteric and playful",
        title = "LOLCODE",
        originalTitle = "LOLCODE",
        author = "Adam Lindsay",
        year = 2007,
        summary = "An esoteric language with cat-speak keywords: HAI starts a program and KTHXBYE ends it.",
        details = "Adam Lindsay created LOLCODE in 2007 as a playful language for internet memes. " +
            "Programs start with HAI and end with KTHXBYE, and the other keywords are written in the style of lolcat captions. " +
            "Interpreters run on ordinary machines, so the jokes are real programs.",
        example = """
            HAI
              VISIBLE "HAI WORLD!"
            KTHXBYE
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.LANGUAGE,
        section = "Esoteric and playful",
        title = "Piet",
        originalTitle = "Piet",
        author = "David Morgan-Mar",
        year = 2001,
        summary = "An esoteric language whose programs are images: each coloured block is an instruction, and the picture is the code.",
        details = "Morgan-Mar designed Piet in 2001 and named it after the Dutch painter Piet Mondrian. A program is a picture of coloured blocks, " +
            "and the interpreter moves a pointer through the picture, executing an instruction at every change of colour. " +
            "Hello World programs are painted as small abstract pictures.",
        rare = true,
    ),
)
