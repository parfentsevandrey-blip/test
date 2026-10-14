package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Built products of 2010 to 2020: finished programs with an explanation of how they work and instructions for running them. */
val db4Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.PROJECT,
        section = "Web publishing",
        title = "A static site generator in one file",
        originalTitle = "One-file static site builder",
        author = "Written in the style of the 2013 static site generators",
        year = 2013,
        summary = "A small tool that turns a text file with headings and list items into a complete HTML page.",
        details = "Static site generators such as Jekyll and Pelican spread in the early 2010s, and many writers built their own in a few hundred lines. " +
            "This one reads a text file, takes the first heading as the page title, and writes the body as HTML headings, paragraphs and list items.",
        instructions = """
            1. Save the code as build.py.
            2. Write a page in site/index.txt, with a first line that starts with "# ".
            3. Run it: python3 build.py site/index.txt public/index.html
            4. Open public/index.html in a browser.
        """,
        example = """
            #!/usr/bin/env python3
            # A static site generator in one file: a text page in, an HTML page out.
            import os
            import sys

            HEAD = "<!doctype html><html><head><meta charset='utf-8'><title>%s</title></head><body>\n"
            FOOT = "\n</body></html>\n"

            def convert(line):
                if line.startswith("# "):
                    return "<h1>%s</h1>" % line[2:]
                if line.startswith("- "):
                    return "<li>%s</li>" % line[2:]
                return "<p>%s</p>" % line if line else ""

            def build(source, target):
                with open(source, encoding="utf-8") as handle:
                    lines = [line.rstrip("\n") for line in handle]
                title = next((line[2:] for line in lines if line.startswith("# ")), "Untitled")
                body = "\n".join(convert(line) for line in lines)
                os.makedirs(os.path.dirname(target) or ".", exist_ok=True)
                with open(target, "w", encoding="utf-8") as out:
                    out.write(HEAD % title + body + FOOT)

            if __name__ == "__main__":
                build(sys.argv[1], sys.argv[2])
                print("built", sys.argv[2])
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PROJECT,
        section = "Command-line tools",
        title = "A command-line task list",
        originalTitle = "Task list with a JSON file",
        author = "Written in the style of the 2014 command-line todo tools",
        year = 2014,
        summary = "A tool that adds, completes and lists tasks, and keeps them in a JSON file next to the script.",
        details = "Small command-line todo tools were popular with programmers in the 2010s, because a plain file was easy to back up and sync. " +
            "This one keeps its list in tasks.json, adds tasks from the arguments, marks a task done by its number, and prints the list with check marks.",
        instructions = """
            1. Save the code as tasks.py.
            2. Add a task: python3 tasks.py add Buy milk
            3. Mark the first task done: python3 tasks.py done 1
            4. List the tasks: python3 tasks.py
        """,
        example = """
            #!/usr/bin/env python3
            # A command-line task list that keeps its tasks in a JSON file.
            import json
            import os
            import sys

            FILE = "tasks.json"

            def load():
                if not os.path.exists(FILE):
                    return []
                with open(FILE, encoding="utf-8") as handle:
                    return json.load(handle)

            def save(tasks):
                with open(FILE, "w", encoding="utf-8") as handle:
                    json.dump(tasks, handle, indent=2)

            def main(argv):
                tasks = load()
                if argv[:1] == ["add"]:
                    tasks.append({"text": " ".join(argv[1:]), "done": False})
                    save(tasks)
                elif argv[:1] == ["done"]:
                    tasks[int(argv[1]) - 1]["done"] = True
                    save(tasks)
                for number, task in enumerate(tasks, 1):
                    mark = "x" if task["done"] else " "
                    print("%d. [%s] %s" % (number, mark, task["text"]))

            if __name__ == "__main__":
                main(sys.argv[1:])
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PROJECT,
        section = "Browser extensions",
        title = "A Chrome extension that counts tabs",
        originalTitle = "Tab counter (manifest version 2)",
        author = "Written in the style of the 2012 Chrome extensions",
        year = 2012,
        summary = "A browser extension whose toolbar button logs the number of open tabs to the extension console.",
        details = "Chrome extensions of the early 2010s were packages of a manifest file and scripts, and manifest version 2 was the common format. " +
            "A background script listens for clicks on the toolbar button and asks the tabs API for the open tabs. The count goes to the console of the extension.",
        instructions = """
            1. Put the two files below in one folder called tab-counter.
            2. Open chrome://extensions, switch on developer mode and choose Load unpacked.
            3. Pick the folder, then click the toolbar button and open the extension console to read the count.
        """,
        example = """
            ### manifest.json
            {
              "manifest_version": 2,
              "name": "Tab counter",
              "version": "1.0",
              "browser_action": { "default_title": "Count tabs" },
              "background": { "scripts": ["background.js"] },
              "permissions": ["tabs"]
            }
            ### background.js
            chrome.browserAction.onClicked.addListener(function () {
              chrome.tabs.query({}, function (tabs) {
                console.log("open tabs: " + tabs.length);
              });
            });
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PROJECT,
        section = "Command-line tools",
        title = "A Go command that prints file hashes",
        originalTitle = "SHA-256 file hasher",
        author = "Written in the style of the 2017 Go command-line tools",
        year = 2017,
        summary = "A single-file command that reads each file named on the command line and prints its SHA-256 hash.",
        details = "Go's standard library has the hash and io packages, so a useful tool fits in one file with no outside dependencies. " +
            "The command streams each file through SHA-256 without loading it into memory, and prints the hash next to the name in the same layout as sha256sum.",
        instructions = """
            1. Save the code as hashes.go.
            2. Run it on a file: go run hashes.go notes.txt
            3. Compare the output with sha256sum notes.txt, which prints the same hash.
        """,
        example = """
            package main

            import (
                "crypto/sha256"
                "fmt"
                "io"
                "os"
            )

            func main() {
                for _, name := range os.Args[1:] {
                    file, err := os.Open(name)
                    if err != nil {
                        fmt.Fprintln(os.Stderr, err)
                        os.Exit(1)
                    }
                    hash := sha256.New()
                    if _, err := io.Copy(hash, file); err != nil {
                        fmt.Fprintln(os.Stderr, err)
                        os.Exit(1)
                    }
                    file.Close()
                    fmt.Printf("%x  %s\n", hash.Sum(nil), name)
                }
            }
        """,
        rare = true,
    ),
)
