package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Usage examples of 2000 to 2010: short recipes for feeds, databases, PHP 5 classes, Git, Ajax, Maven and Python 2.4. */
val db3Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Web feeds",
        title = "Parse an RSS feed in Python",
        originalTitle = "ElementTree RSS reader",
        author = "Python practice",
        year = 2002,
        summary = "Read the titles and links of an RSS 2.0 feed with the standard library, without a third-party package.",
        details = "RSS 2.0 (2002) is XML with a fixed set of elements. The ElementTree module, part of the Python standard library from version 2.5, " +
            "reads the document into a tree that the program walks with simple paths. Each item becomes one line of output.",
        instructions = """
            1. Save the example as rss.py.
            2. Run it: python3 rss.py
        """,
        example = """
            import sys
            import xml.etree.ElementTree as ET

            feed = '<rss version="2.0"><channel><title>Example news</title><item><title>First post</title><link>http://example.com/1</link></item><item><title>Second post</title><link>http://example.com/2</link></item></channel></rss>'
            root = ET.fromstring(feed)
            for item in root.findall("./channel/item"):
                sys.stdout.write("%s -> %s\n" % (item.find("title").text, item.find("link").text))
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Databases",
        title = "A running total with a SQL window function",
        originalTitle = "SUM() OVER (PARTITION BY ... ORDER BY ...)",
        author = "SQL:2003 practice",
        year = 2003,
        summary = "A window function adds a running total per region without grouping the detail rows away.",
        details = "SQL:2003 added window functions. An OVER clause computes an aggregate for each row over a window of related rows, so the detail rows stay visible. " +
            "PARTITION BY restarts the total for each region, and ORDER BY sets the order of the running sum.",
        instructions = """
            1. Save the statements as sales.sql.
            2. Run them with the SQLite command line: sqlite3 sales.db < sales.sql
            3. Other databases that support window functions accept the same SELECT.
        """,
        example = """
            CREATE TABLE sales (region TEXT, month INTEGER, amount INTEGER);
            INSERT INTO sales VALUES ('north', 1, 120), ('north', 2, 90), ('north', 3, 150);
            INSERT INTO sales VALUES ('south', 1, 80), ('south', 2, 200), ('south', 3, 40);
            SELECT region, month, amount,
                   SUM(amount) OVER (PARTITION BY region ORDER BY month) AS running_total
            FROM sales
            ORDER BY region, month;
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Web pages",
        title = "A PHP 5 class that throws an exception",
        originalTitle = "PHP 5 object and exception",
        author = "PHP 5 practice",
        year = 2004,
        summary = "A small class validates its input and reports a bad amount with an exception that the caller catches.",
        details = "PHP 5 added private members and exceptions, so an error can travel up to the code that can handle it. " +
            "The deposit method refuses a non-positive amount by throwing InvalidArgumentException, and the caller catches it and prints the message.",
        instructions = """
            1. Save the example as account.php.
            2. Run it: php account.php
        """,
        example = """
            <?php
            class Account {
                private <DOLLAR>balance = 0;

                public function deposit(<DOLLAR>amount) {
                    if (<DOLLAR>amount <= 0) {
                        throw new InvalidArgumentException("amount must be positive");
                    }
                    <DOLLAR>this->balance += <DOLLAR>amount;
                }

                public function balance() {
                    return <DOLLAR>this->balance;
                }
            }

            <DOLLAR>account = new Account();
            <DOLLAR>account->deposit(50);
            try {
                <DOLLAR>account->deposit(-5);
            } catch (InvalidArgumentException <DOLLAR>e) {
                echo "error: " . <DOLLAR>e->getMessage() . "\n";
            }
            echo "balance: " . <DOLLAR>account->balance() . "\n";
            ?>
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Version control",
        title = "Your first Git commit",
        originalTitle = "git init, add, commit",
        author = "Git practice",
        year = 2005,
        summary = "Create a repository, stage a file and record the first snapshot, with the commands typed in a terminal.",
        details = "Git records snapshots of the project. The init command creates the repository, add stages a file for the next snapshot, " +
            "and commit records it with a message. The log command lists the snapshots, each identified by a short hash.",
        instructions = """
            1. Save the lines below as first-commit.sh.
            2. Run it: sh first-commit.sh
            3. The last line shows the commit with its message.
        """,
        example = """
            git init first-commit
            cd first-commit
            git config user.name "Ann"
            git config user.email "ann@example.com"
            echo "hello" > notes.txt
            git add notes.txt
            git commit -m "First commit"
            git log --oneline
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Web",
        title = "An Ajax request in plain JavaScript",
        originalTitle = "XMLHttpRequest counter",
        author = "Ajax practice",
        year = 2005,
        summary = "A script asks the server for a number and writes it into the page without reloading.",
        details = "The XMLHttpRequest object sends the request in the background. The handler waits for the ready state to reach 4, " +
            "which means the response has arrived, and then checks the HTTP status before using the text.",
        instructions = """
            1. Put the script in a page served by your web server.
            2. Point the URL at a script that returns a number.
            3. Open the page: the number appears without a reload.
        """,
        example = """
            var request = new XMLHttpRequest();
            request.open("GET", "/inbox/count", true);
            request.onreadystatechange = function () {
                if (request.readyState == 4 && request.status == 200) {
                    document.getElementById("count").innerHTML = request.responseText;
                }
            };
            request.send(null);
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Build",
        title = "A Maven pom.xml for a small project",
        originalTitle = "Project object model",
        author = "Maven practice",
        year = 2004,
        summary = "One XML file describes the project, its dependencies and the plugins that build it.",
        details = "Maven reads this file to decide what to compile, which libraries to download and how to run the tests. " +
            "The groupId and artifactId name the project, and each dependency names a library that Maven fetches from a repository.",
        instructions = """
            1. Save the file as pom.xml in the project folder.
            2. Run mvn test to build the project and run its tests.
        """,
        example = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>guestbook</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>junit</groupId>
                  <artifactId>junit</artifactId>
                  <version>3.8.1</version>
                  <scope>test</scope>
                </dependency>
              </dependencies>
            </project>
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.EXAMPLE,
        section = "Python idioms",
        title = "A generator expression in Python 2.4",
        originalTitle = "Generator expression",
        author = "Python 2.4 practice",
        year = 2004,
        summary = "A sum over squares without building a list in memory, written as a generator expression.",
        details = "Python 2.4 (2004) added generator expressions, which have the syntax of list comprehensions but are written in parentheses. " +
            "The values are produced one at a time, so a large range needs no list in memory. The sum function consumes the generator directly.",
        instructions = """
            1. Save the example as squares.py.
            2. Run it: python3 squares.py
        """,
        example = """
            # Python 2.4 generator expression: sums the squares without building a list
            total = sum(n * n for n in range(1, 11))
            print(total)
        """,
    ),
)
