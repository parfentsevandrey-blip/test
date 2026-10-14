package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Usage examples of the 1990s: short recipes for the everyday problems of the decade. */
val db2Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Files and state",
        title = "Visit counter in a file",
        originalTitle = "Counter kept in counter.txt",
        author = "Common CGI practice",
        year = 1996,
        summary = "A page counter that keeps its value in a text file, so it survives between runs.",
        details = "Without a database, the usual way to keep state on a 90s web server was a text file. " +
            "The script reads the number, adds one and writes the new value back. The same trick worked for guest books and polls.",
        instructions = """
            1. Save the code as counter.pl.
            2. Run it: perl counter.pl
            3. Run it again: the number goes up on every run.
            4. Delete counter.txt to start from zero.
        """,
        example = """
            #!/usr/bin/perl
            # Keeps the visit count in counter.txt
            my <DOLLAR>file = 'counter.txt';
            my <DOLLAR>count = 0;
            if (open(my <DOLLAR>in, '<', <DOLLAR>file)) {
                <DOLLAR>count = <<DOLLAR>in> + 0;
                close(<DOLLAR>in);
            }
            <DOLLAR>count++;
            open(my <DOLLAR>out, '>', <DOLLAR>file) or die "cannot write <DOLLAR>file: <DOLLAR>!";
            print <DOLLAR>out "<DOLLAR>count\n";
            close(<DOLLAR>out);
            print "Visit number <DOLLAR>count\n";
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Text and collections",
        title = "Word count with Hashtable",
        originalTitle = "Hashtable word count (Java 1.0 style)",
        author = "Java 1.0 practice",
        year = 1996,
        summary = "Counting words with java.util.Hashtable in a console program, the way early Java programs did.",
        details = "The Hashtable class holds the counts, keyed by word. The program reads standard input line by line and splits each line into words with StringTokenizer. " +
            "Java 1.0 had no generics and no autoboxing, so the counts were wrapped in Integer objects by hand.",
        instructions = """
            1. Save the example as WordCount.java.
            2. Compile it: javac WordCount.java
            3. Run it with some text: echo "the cat and the hat" | java WordCount
        """,
        example = """
            import java.io.*;
            import java.util.*;

            public class WordCount {
                public static void main(String[] args) throws IOException {
                    Hashtable counts = new Hashtable();
                    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
                    String line;
                    while ((line = in.readLine()) != null) {
                        StringTokenizer words = new StringTokenizer(line);
                        while (words.hasMoreTokens()) {
                            String w = words.nextToken();
                            Integer n = (Integer) counts.get(w);
                            counts.put(w, new Integer(n == null ? 1 : n.intValue() + 1));
                        }
                    }
                    Enumeration keys = counts.keys();
                    while (keys.hasMoreElements()) {
                        String w = (String) keys.nextElement();
                        System.out.println(w + " " + counts.get(w));
                    }
                }
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Web pages",
        title = "HTML table from a PHP array",
        originalTitle = "PHP/FI table page",
        author = "PHP/FI practice",
        year = 1996,
        summary = "Build a table from an associative array: the array is the data, and the page is only the view.",
        details = "PHP/FI pages mixed HTML with blocks of code. An associative array held the data, and a foreach loop printed one table row per entry. " +
            "A designer could change the markup around the loop without touching the data.",
        instructions = """
            1. Save the example as table.php.
            2. Run it from the command line: php -f table.php
            3. On a web server the same output reaches the browser.
        """,
        example = """
            <?php
            <DOLLAR>scores = array("Ann" => 31, "Bob" => 27, "Eve" => 45);
            <DOLLAR>total = 0;
            echo "<table border=1>\n<tr><th>Name</th><th>Score</th></tr>\n";
            foreach (<DOLLAR>scores as <DOLLAR>name => <DOLLAR>score) {
                echo "<tr><td><DOLLAR>name</td><td><DOLLAR>score</td></tr>\n";
                <DOLLAR>total += <DOLLAR>score;
            }
            echo "<tr><td>total</td><td><DOLLAR>total</td></tr>\n</table>\n";
            ?>
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Text data",
        title = "Sorting pairs in Python 1.5 style",
        originalTitle = "Tuple sorting",
        author = "Python practice",
        year = 1997,
        summary = "A list of tuples sorts itself on the first field, which made many 90s scripts short.",
        details = "Python compares tuples element by element. Putting the score first sorts the list by score, without writing a comparison function. " +
            "The loop unpacks each pair into two variables.",
        instructions = """
            1. Save the example as sortpairs.py.
            2. Run it: python sortpairs.py
        """,
        example = """
            import sys
            people = [(31, "Ann"), (27, "Bob"), (45, "Eve")]
            people.sort()
            for score, name in people:
                sys.stdout.write("%d %s\n" % (score, name))
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Templates and containers",
        title = "Sorting with a function object (C++98)",
        originalTitle = "std::sort with a functor",
        author = "C++98 practice",
        year = 1998,
        summary = "A small class with operator() tells std::sort how to order the words.",
        details = "std::sort takes a comparison object as its third argument. The object is a class with operator(), which the algorithm calls for each pair of elements. " +
            "Because the object carries its own type, the compiler can inline the comparison, so the code runs as fast as a hand-written loop.",
        instructions = """
            1. Save the example as sortwords.cpp.
            2. Compile it in C++98 mode: g++ -std=c++98 -o sortwords sortwords.cpp
            3. Run ./sortwords: the words come out from the shortest to the longest.
        """,
        example = """
            #include <algorithm>
            #include <iostream>
            #include <string>
            #include <vector>

            struct ByLength {
                bool operator()(const std::string& a, const std::string& b) const {
                    return a.size() < b.size();
                }
            };

            int main() {
                std::vector<std::string> words;
                words.push_back("compiler");
                words.push_back("c");
                words.push_back("linker");
                std::sort(words.begin(), words.end(), ByLength());
                for (std::size_t i = 0; i < words.size(); ++i)
                    std::cout << words[i] << "\n";
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "Databases",
        title = "Join two tables (SQL-92)",
        originalTitle = "SQL-92 inner join with GROUP BY",
        author = "SQL-92 practice",
        year = 1992,
        summary = "Two tables joined on a shared key, then grouped to add up the orders of each customer.",
        details = "The SQL-92 standard made the explicit JOIN ... ON syntax common. The condition after ON ties each order to its customer, " +
            "and GROUP BY adds the amounts per name. Databases of the 90s ran queries like this on real business tables.",
        instructions = """
            1. Save the statements as shop.sql.
            2. Run them with the SQLite command line: sqlite3 shop.db < shop.sql
            3. Any SQL-92 database accepts the same SELECT.
        """,
        example = """
            CREATE TABLE customers (id INTEGER, name TEXT);
            CREATE TABLE orders (id INTEGER, customer_id INTEGER, amount INTEGER);
            INSERT INTO customers VALUES (1, 'Ann');
            INSERT INTO customers VALUES (2, 'Bob');
            INSERT INTO orders VALUES (10, 1, 50);
            INSERT INTO orders VALUES (11, 2, 20);
            INSERT INTO orders VALUES (12, 1, 30);
            SELECT c.name, SUM(o.amount)
              FROM customers c JOIN orders o ON o.customer_id = c.id
              GROUP BY c.name;
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.EXAMPLE,
        section = "DOS batch",
        title = "Nightly backup with a DOS batch file",
        originalTitle = "BACKUP.BAT",
        author = "DOS batch practice",
        year = 1993,
        summary = "A three-line batch file copies the work directory to a floppy or a second disk.",
        details = "MS-DOS batch files ran commands one after another, and xcopy copied whole trees with the /S switch. " +
            "The /E switch keeps empty directories, and /Y answers the overwrite question without stopping the batch. " +
            "Many offices ran a file like this at the end of the working day.",
        instructions = """
            1. Save the lines as BACKUP.BAT in the root directory.
            2. Insert a blank floppy disk in drive A:, then run BACKUP.
            3. Check the copy with DIR A:\BACKUP.
        """,
        example = """
            @echo off
            echo Backing up C:\WORK to A:\BACKUP ...
            xcopy C:\WORK A:\BACKUP /S /E /Y
            echo Done.
        """,
    ),
)
