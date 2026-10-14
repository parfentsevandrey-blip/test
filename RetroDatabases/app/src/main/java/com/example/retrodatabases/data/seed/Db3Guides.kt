package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 2000 to 2010: how the mechanisms of the decade worked, from Git objects to MapReduce, GFS and ORM mapping. */
val db3Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 3,
        kind = Kind.GUIDE,
        section = "Version control",
        title = "How Git stores your history",
        originalTitle = "The Git object model",
        author = "Linus Torvalds and the Git contributors",
        year = 2005,
        summary = "Git keeps every version as a content-addressed object: a blob for file contents, a tree for directories and a commit for history.",
        details = "Each file's contents are stored as a blob named by the SHA-1 hash of the data, so identical content is stored once. " +
            "A tree lists the blobs and sub-trees of a directory, and a commit points to a tree, its parent commits and its author. " +
            "Branches and tags are only names that point to commits, which makes creating them almost free.",
        instructions = """
            1. Save the lines below as store.sh.
            2. Run it: sh store.sh
            3. The first line is the hash that Git computed for the text hello; the second line prints the stored object back.
        """,
        example = """
            git init store
            cd store
            echo hello | git hash-object -w --stdin
            git cat-file -p ce013625030ba8dba906f756967f9e9ca394464a
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.GUIDE,
        section = "Data at scale",
        title = "How MapReduce counts words",
        originalTitle = "MapReduce word count",
        author = "Jeffrey Dean and Sanjay Ghemawat (Google)",
        year = 2004,
        summary = "The map step emits a pair for every word, the shuffle groups the pairs by word, and the reduce step adds them up.",
        details = "A MapReduce job has three phases. The map function turns each input record into key-value pairs, here one pair per word. " +
            "The framework then groups the pairs by key, which in a cluster means moving data between machines. " +
            "The reduce function receives each key with all its values and returns one result. Because each map call and each reduce call is independent, the work spreads over many machines.",
        instructions = """
            1. Save the example as wordcount.py.
            2. Run it: python3 wordcount.py
            3. Each output line is a word with its count.
        """,
        example = """
            from collections import defaultdict

            def map_phase(document):
                for word in document.split():
                    yield (word.lower(), 1)

            def reduce_phase(word, counts):
                return (word, sum(counts))

            documents = ["the cat sat", "the dog sat down"]
            groups = defaultdict(list)
            for doc in documents:
                for word, one in map_phase(doc):
                    groups[word].append(one)
            for word in sorted(groups):
                print(reduce_phase(word, groups[word]))
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.GUIDE,
        section = "Data at scale",
        title = "How the Google File System splits a file",
        originalTitle = "GFS chunks and replicas",
        author = "Sanjay Ghemawat, Howard Gobioff and Shun-Tak Leung (Google)",
        year = 2003,
        summary = "Files become 64 MB chunks, each stored on three chunk servers, while one master keeps the map of where everything is.",
        details = "A client asks the master where a chunk lives, then reads the data straight from a chunk server, so the master handles only small metadata requests. " +
            "Each chunk has three replicas on different machines, and the master creates a new replica when a server fails. " +
            "Record appends may leave duplicate records, and the applications were written to tolerate them.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.GUIDE,
        section = "Web",
        title = "How Ajax updates a page without a reload",
        originalTitle = "XMLHttpRequest and the asynchronous page",
        author = "Jesse James Garrett and the browser makers",
        year = 2005,
        summary = "A script sends a request in the background, waits for the answer and changes one part of the page.",
        details = "A browser starts a request with XMLHttpRequest, which runs in the background while the page stays usable. " +
            "The script registers a handler that the browser calls when the request changes state. When the server answers, the handler reads the response " +
            "and updates the matching element of the page. Only the changed fragment travels over the network, so pages feel responsive.",
    ),

    entry(
        databaseId = 3,
        kind = Kind.GUIDE,
        section = "Frameworks and data",
        title = "How a mapper turns classes into tables",
        originalTitle = "Object-relational mapping with Hibernate",
        author = "Gavin King and the Hibernate team",
        year = 2001,
        summary = "A mapping file says which class fields become which columns, and the framework writes the SQL for loads and saves.",
        details = "Hibernate (2001) let Java programmers store objects without writing SQL by hand. A mapping file tied each class to a table and each field to a column. " +
            "The framework built the SQL for saving, loading and updating objects, and it cached objects so that repeated reads did not reach the database.",
        example = """
            public class Cat {
                private Long id;
                private String name;
                // getters and setters omitted
            }

            <!-- Cat.hbm.xml -->
            <hibernate-mapping>
              <class name="Cat" table="CATS">
                <id name="id" column="CAT_ID"><generator class="native"/></id>
                <property name="name" column="NAME"/>
              </class>
            </hibernate-mapping>
        """,
    ),
)
