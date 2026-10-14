package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 2000 to 2010: technologies mixed on purpose, from ctypes and embedded Python to Git hooks and LiveConnect. */
val db3Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 3,
        kind = Kind.CROSS,
        section = "Python and native code",
        title = "Python calls C through ctypes",
        originalTitle = "ctypes",
        author = "The Python community (ctypes module)",
        year = 2006,
        summary = "A Python program loads a C shared library and calls one of its functions with plain Python values.",
        details = "The ctypes module entered the standard library in Python 2.5 (2006). It loads a shared library and converts the arguments to C types, " +
            "so a script can use C code without writing an extension module. The pattern was popular for wrapping existing libraries quickly.",
        instructions = """
            1. Save the two files below in one folder and build the library: cc -shared -fPIC -o libmathlib.so mathlib.c
            2. Run the script: python3 call.py
        """,
        example = """
            ### mathlib.c
            int add_ints(int a, int b)
            {
                return a + b;
            }
            ### call.py
            import ctypes

            lib = ctypes.CDLL("./libmathlib.so")
            print(lib.add_ints(20, 22))
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.CROSS,
        section = "Embedding",
        title = "C embeds Python",
        originalTitle = "Embedding the Python interpreter",
        author = "Python C API practice",
        year = 2000,
        summary = "A C program starts the Python interpreter, runs a script inside itself and shuts the interpreter down.",
        details = "The Python C API let host programs start an interpreter inside themselves. Py_Initialize starts it, PyRun_SimpleString runs a script, " +
            "and Py_Finalize stops it. The same calls existed in the Python 2.0 era that this record describes, so programs could use Python as a scripting layer.",
        instructions = """
            1. Save the code as embed.c.
            2. Build it against the Python headers and library: cc -o embed embed.c <DOLLAR>(python3-config --includes) <DOLLAR>(python3-config --embed --ldflags)
            3. Run it: ./embed
        """,
        example = """
            #include <Python.h>

            int main(void)
            {
                Py_Initialize();
                PyRun_SimpleString("print('Hello from Python, run inside a C program')");
                Py_Finalize();
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.CROSS,
        section = "Version control",
        title = "A Git pre-commit hook written in Python",
        originalTitle = "pre-commit hook",
        author = "Git hook practice",
        year = 2005,
        summary = "A hook written in Python that stops a commit when a staged line ends with a space or a tab.",
        details = "Git runs the program named pre-commit before each commit, and a non-zero exit status stops the commit. " +
            "The hook asks Git for the staged changes, checks each added line for trailing whitespace, and reports how many lines are at fault. " +
            "Teams used such hooks to keep the code tidy without waiting for a review.",
        instructions = """
            1. Make a repository and save the hook as .git/hooks/pre-commit.
            2. Make it executable: chmod +x .git/hooks/pre-commit
            3. Try to commit a line that ends with a space: the commit is refused.
        """,
        example = """
            ### pre-commit
            #!/usr/bin/env python3
            # Git pre-commit hook: refuses the commit if a staged line ends with a space or a tab.
            import subprocess
            import sys

            diff = subprocess.run(["git", "diff", "--cached", "-U0"], capture_output=True, text=True).stdout
            bad = [line for line in diff.split("\n")
                   if line.startswith("+") and not line.startswith("+++") and line[1:] != line[1:].rstrip()]
            if bad:
                sys.stderr.write("trailing whitespace in %d staged line(s)\n" % len(bad))
                sys.exit(1)
        """,
        rare = true,
    ),

    entry(
        databaseId = 3,
        kind = Kind.CROSS,
        section = "Browsers and applets",
        title = "JavaScript calls Java through LiveConnect",
        originalTitle = "LiveConnect",
        author = "Netscape Communications",
        year = 2000,
        summary = "A web page's script uses Java classes directly through LiveConnect, the bridge built into Netscape browsers.",
        details = "LiveConnect (1996) let page scripts call Java classes and applets in Netscape browsers. " +
            "A script could create Java objects and call their methods, which made it possible to mix the two languages on one page. " +
            "Browser security rules limited what the Java side could reach.",
        instructions = """
            1. Put the script in an HTML page and serve it to a browser with LiveConnect enabled.
            2. Open the page and look at the Java console for the message.
        """,
        example = """
            <script>
              var system = java.lang.System;
              system.out.println("Hello from Java, called by JavaScript");
            </script>
        """,
    ),

    entry(
        databaseId = 3,
        kind = Kind.CROSS,
        section = "Windows interop",
        title = "C# calls a C library with P/Invoke",
        originalTitle = "P/Invoke (DllImport)",
        author = "Microsoft .NET practice",
        year = 2002,
        summary = "A C# program declares a native function with DllImport, and the runtime calls it in the native library.",
        details = "Platform invocation (P/Invoke) let .NET programs call functions in ordinary DLLs. " +
            "The DllImport attribute named the library and the function, and the runtime converted the arguments to native types. " +
            "The declaration was the only glue the program needed.",
        instructions = """
            1. Build the native library as mathlib.dll with a function add_ints.
            2. Put the class in a C# project next to the library.
            3. Run the program: it prints the sum that the native function returns.
        """,
        example = """
            using System.Runtime.InteropServices;

            class Native {
                [DllImport("mathlib")]
                public static extern int add_ints(int a, int b);

                static void Main() {
                    System.Console.WriteLine(add_ints(20, 22));
                }
            }
        """,
    ),
)
