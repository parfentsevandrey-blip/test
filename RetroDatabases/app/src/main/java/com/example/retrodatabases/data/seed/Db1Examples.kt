package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Usage examples: short recipes that solve real problems of the 80s. */
val db1Examples: List<RecordEntity> = listOf(

    entry(
        kind = Kind.EXAMPLE,
        section = "Text data",
        title = "Word frequency in a text",
        originalTitle = "Word frequency with tr, sort, uniq",
        author = "Unix practice",
        year = 1973,
        summary = "One line of shell tells us which words occur most often in a text.",
        details = "tr replaces everything that is not a letter with a newline, so each word ends up on its own line. " +
            "sort puts identical words next to each other, uniq -c counts the repeats, and the last sort -rn lists the most frequent words at the top. " +
            "This is how people analysed documentation and texts in the 80s.",
        instructions = """
            1. Save any text as book.txt.
            2. Run the command from the example.
            3. The output shows the count on the left and the word on the right; the most frequent words are at the top.
        """,
        example = """
            tr -cs 'A-Za-z' '\n' < book.txt | tr 'A-Z' 'a-z' | sort | uniq -c | sort -rn | head -5
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Text data",
        title = "Bulk replacement in a text: sed",
        originalTitle = "sed substitution and line selection",
        author = "Lee McMahon, Bell Labs",
        year = 1974,
        summary = "Replacing a word on every line of a file without opening an editor.",
        details = "The command s/pattern/replacement/g changes every occurrence on each line; the g flag means “all”, not just the first one. " +
            "Addresses such as 2,4 select lines, and /pattern/d deletes the matching lines. " +
            "It is convenient to send the result to a new file, so the original text stays intact.",
        instructions = """
            1. Create notes.txt with several lines, one of which contains the word Unix.
            2. Run the three commands from the example one after another.
            3. To keep the result, add > notes-new.txt to the command.
        """,
        example = """
            sed 's/Unix/UNIX/g' notes.txt
            sed -n '2,4p' notes.txt
            sed '/^#/d' notes.txt
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Logs and reports",
        title = "Top errors in a log",
        originalTitle = "Error report with grep, cut, sort, uniq",
        author = "Unix practice",
        year = 1973,
        summary = "We pull the error lines out of a long log and count which codes occur most often.",
        details = "grep selects the lines with ERROR, cut extracts the third field, sort and uniq -c count the repeats, and sort -rn prints the ranking. " +
            "This is how reports were built in the 80s without programming: the pipeline ran on any Unix machine and needed no compiler.",
        instructions = """
            1. Create a file errors.log with this content:
               12:00 ERROR E42 disk full
               12:01 INFO start
               12:02 ERROR E17 no paper
               12:03 ERROR E42 disk full
               12:04 ERROR E42 disk full
            2. Run the command from the example.
            3. The result: E42 appears 3 times, E17 once.
        """,
        example = """
            grep 'ERROR' errors.log | cut -d' ' -f3 | sort | uniq -c | sort -rn | head -3
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "C programs",
        title = "Sorting any array: qsort",
        originalTitle = "qsort from the C standard library",
        author = "C standard library",
        year = 1979,
        summary = "One function sorts arrays of any type: the comparison rule is passed as a pointer to a function.",
        details = "qsort takes an array, the number of elements, the size of an element and a comparison function. " +
            "The same code sorts numbers, strings and records; this was an important trick of portable libraries in the 80s. " +
            "The comparison function returns a negative, zero or positive value.",
        instructions = """
            1. Save the code as sortdemo.c.
            2. Build it: cc -o sortdemo sortdemo.c
            3. Run ./sortdemo — the numbers are printed in ascending order: 1, 3, 7, 19, 42, 88.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>

            static int compare(const void *a, const void *b)
            {
                int x = *(const int *)a;
                int y = *(const int *)b;
                return (x > y) - (x < y);
            }

            int main(void)
            {
                int values[] = { 42, 7, 19, 3, 88, 1 };
                size_t n = sizeof values / sizeof values[0];
                size_t i;

                qsort(values, n, sizeof values[0], compare);
                for (i = 0; i < n; i++)
                    printf("%d\n", values[i]);
                return 0;
            }
        """,
    ),

    entry(
        kind = Kind.EXAMPLE,
        section = "Databases",
        title = "Summary report: GROUP BY",
        originalTitle = "SQL GROUP BY report",
        author = "IBM, SEQUEL language",
        year = 1974,
        summary = "GROUP BY turns records into a summary: how many there are and of which kinds.",
        details = "The aggregate functions COUNT, MIN, MAX and SUM work over groups of rows that share the same field value. " +
            "Such reports used to be written as separate COBOL programs; SQL does the same in one query.",
        instructions = """
            1. Save the example as report.sql.
            2. Run it in any SQL engine, for example sqlite3: sqlite3 < report.sql
            3. The result: for each kind, the number of records and the earliest year.
        """,
        example = """
            CREATE TABLE records (kind TEXT, title TEXT, year INTEGER);
            INSERT INTO records VALUES ('LANGUAGE', 'Pascal', 1970);
            INSERT INTO records VALUES ('LANGUAGE', 'C', 1972);
            INSERT INTO records VALUES ('TOOL', 'make', 1976);

            SELECT kind, COUNT(*) AS total, MIN(year) AS first_year
            FROM records
            GROUP BY kind
            ORDER BY total DESC;
        """,
    ),
)
