package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Small examples of 2020 to 2026: one feature per program, short enough to read in a minute. */
val db5Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Python",
        title = "match statements in Python",
        originalTitle = "PEP 634 (Python 3.10)",
        author = "Brandt Bucher, Guido van Rossum and the Python core developers",
        year = 2021,
        summary = "A match statement that picks a branch by the shape of a dictionary and binds its parts to names, with a guard for extra conditions.",
        details = "Python 3.10 added match in October 2021 following PEP 634. Each case can match mappings, sequences and literals, and a guard after if adds a condition. " +
            "The example describes four shapes and falls through to a default for anything else.",
        instructions = """
            1. Save the example as shapes.py.
            2. Run it: python3 shapes.py
        """,
        example = """
            def describe(shape):
                match shape:
                    case {"kind": "circle", "radius": r}:
                        return f"circle with area {3 * r * r}"
                    case {"kind": "rect", "w": w, "h": h} if w == h:
                        return f"square of side {w}"
                    case {"kind": "rect", "w": w, "h": h}:
                        return f"rectangle {w} by {h}"
                    case _:
                        return "unknown shape"

            for item in [{"kind": "circle", "radius": 2}, {"kind": "rect", "w": 3, "h": 3},
                         {"kind": "rect", "w": 2, "h": 5}, {"kind": "blob"}]:
                print(describe(item))
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Python",
        title = "Nested quotes in f-strings",
        originalTitle = "PEP 701 (Python 3.12)",
        author = "The Python core developers",
        year = 2023,
        summary = "An f-string can now reuse its own quote character inside the braces, so nested strings read naturally.",
        details = "Before Python 3.12, an f-string could not reuse its own quote character inside a replacement field. PEP 701 removed that limit and let f-strings contain expressions of any kind. " +
            "The example joins a list inside an f-string, using double quotes at both levels.",
        instructions = """
            1. Save the example as nested.py.
            2. Run it with Python 3.12 or newer: python3 nested.py
        """,
        example = """
            names = ["Ann", "Bob"]
            print(f"Greeting: {", ".join(f"hi {n.upper()}" for n in names)}")
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Java",
        title = "Records and sealed classes in Java",
        originalTitle = "JEP 395 and JEP 409",
        author = "Oracle and the OpenJDK community",
        year = 2021,
        summary = "Records for plain data classes, and sealed interfaces that list every type allowed to implement them.",
        details = "Records were finalised in Java 16 and sealed classes in Java 17, and both reached the 2021 long-term support release. " +
            "The example defines a sealed shape interface with two records, then prints each shape and its area.",
        instructions = """
            1. Save the example as Shapes.java.
            2. Compile it: javac Shapes.java
            3. Run it: java Shapes
        """,
        example = """
            public class Shapes {
                sealed interface Shape permits Circle, Square {}
                record Circle(double radius) implements Shape {}
                record Square(double side) implements Shape {}

                static double area(Shape shape) {
                    if (shape instanceof Circle c) {
                        return Math.PI * c.radius() * c.radius();
                    }
                    Square square = (Square) shape;
                    return square.side() * square.side();
                }

                public static void main(String[] args) {
                    System.out.println(new Square(3));
                    System.out.printf("%.2f%n", area(new Circle(1)));
                    System.out.println(area(new Square(3)));
                }
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Java",
        title = "Virtual threads in Java 21",
        originalTitle = "JEP 444 (Java 21)",
        author = "Oracle and the OpenJDK community (Project Loom)",
        year = 2023,
        summary = "A thread that is cheap enough to create by the ten thousand, so blocking code can scale without a thread pool.",
        details = "Java 21 made virtual threads final. A virtual thread is scheduled onto a few platform threads when it runs, and it gives its platform thread back while it waits. " +
            "The example starts ten thousand tasks, and each one sleeps for ten milliseconds.",
        instructions = """
            1. Save the example as Tasks.java.
            2. Compile it: javac Tasks.java
            3. Run it: java Tasks
        """,
        example = """
            import java.util.concurrent.Executors;
            import java.util.stream.IntStream;

            public class Tasks {
                public static void main(String[] args) throws Exception {
                    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
                        IntStream.range(0, 10_000).forEach(i -> executor.submit(() -> {
                            Thread.sleep(10);
                            return i;
                        }));
                    }
                    System.out.println("finished 10000 tasks");
                }
            }
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "JavaScript",
        title = "Top-level await in modules",
        originalTitle = "ECMAScript 2022 top-level await",
        author = "TC39 (ECMAScript 2022)",
        year = 2022,
        summary = "A module can use await outside any function, so setup code can wait for values at the top of the file.",
        details = "ECMAScript 2022 allowed await at the top level of a module. Node.js runs such code when the file ends in .mjs or its package declares a module type. " +
            "The example waits for two timers and adds up their results.",
        instructions = """
            1. Save the example as wait.mjs.
            2. Run it: node wait.mjs
        """,
        example = """
            const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

            console.log("start");
            await sleep(50);
            console.log("after the first wait");
            const values = await Promise.all([sleep(10).then(() => 1), sleep(20).then(() => 2)]);
            console.log("sum:", values.reduce((a, b) => a + b, 0));
        """,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Rust",
        title = "Let-else bindings in Rust",
        originalTitle = "let-else (Rust 1.65)",
        author = "The Rust team",
        year = 2022,
        summary = "A let statement that must match a pattern, with an else block that leaves early, which keeps the main path flat.",
        details = "Rust 1.65 stabilised let-else in late 2022. When the pattern does not match, the else block must leave the function, with return, break or a panic. " +
            "The example returns a placeholder when the line is blank.",
        instructions = """
            1. Save the example as let_else.rs.
            2. Compile it: rustc --edition 2021 let_else.rs -o let_else
            3. Run it: ./let_else
        """,
        example = """
            fn first_word(line: &str) -> &str {
                let Some(word) = line.split_whitespace().next() else {
                    return "(empty)";
                };
                word
            }

            fn main() {
                println!("{}", first_word("  hello there"));
                println!("{}", first_word("   "));
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 5,
        kind = Kind.EXAMPLE,
        section = "Databases",
        title = "Strict tables in SQLite",
        originalTitle = "STRICT tables (SQLite 3.37)",
        author = "The SQLite project",
        year = 2021,
        summary = "A table declaration that refuses values which cannot be stored in the declared column type, instead of quietly converting them.",
        details = "SQLite 3.37, released in late 2021, added STRICT tables. A STRICT table accepts only the declared types, so text that cannot be an integer is rejected. " +
            "The example inserts one valid row and then tries one invalid row.",
        instructions = """
            1. Save the example as strict.py.
            2. Run it: python3 strict.py
        """,
        example = """
            import sqlite3

            db = sqlite3.connect(":memory:")
            db.execute("CREATE TABLE readings (id INTEGER PRIMARY KEY, value INTEGER NOT NULL) STRICT")
            db.execute("INSERT INTO readings (value) VALUES (42)")
            try:
                db.execute("INSERT INTO readings (value) VALUES ('forty-two')")
            except sqlite3.Error as error:
                print("rejected:", error)
            print(db.execute("SELECT id, value FROM readings").fetchall())
        """,
        rare = true,
    ),
)
