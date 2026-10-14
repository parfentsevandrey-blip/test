package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 2010 to 2020: technologies mixed on purpose, from cgo and Node addons to WebAssembly and Git filters. */
val db4Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.CROSS,
        section = "Go and native code",
        title = "Go calls C with cgo",
        originalTitle = "cgo",
        author = "The Go team",
        year = 2012,
        summary = "A Go program keeps C code in a comment above an import of the pseudo-package C and calls the C function directly.",
        details = "cgo has been part of the Go toolchain since the early releases, and Go 1.0 shipped in March 2012 with it. " +
            "The C code sits in a comment directly above import \"C\", and Go converts the arguments and results between the two languages. The build needs a C compiler installed.",
        instructions = """
            1. Save the code as cgo_demo.go.
            2. Run it: go run cgo_demo.go
            3. The output is the sum computed by the C function.
        """,
        example = """
            package main

            /*
            int add_ints(int a, int b) {
                return a + b;
            }
            */
            import "C"

            import "fmt"

            func main() {
                sum := C.add_ints(20, 22)
                fmt.Println(int(sum))
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.CROSS,
        section = "Node.js and native code",
        title = "A Node.js addon through N-API",
        originalTitle = "Node-API addon",
        author = "The Node.js project",
        year = 2017,
        summary = "A native addon written in C++ against N-API, so the compiled module keeps working when Node.js is upgraded.",
        details = "Node-API, or N-API, was introduced in 2017 as a stable interface between Node.js and native code. " +
            "An addon built against it does not need to be recompiled for every Node.js release, and node-gyp builds it from a small binding file.",
        instructions = """
            1. Put the three files below in one folder.
            2. Build the addon: npx node-gyp configure build
            3. Run the script: node index.js
        """,
        example = """
            ### binding.gyp
            {
              "targets": [
                { "target_name": "addon", "sources": ["addon.cc"] }
              ]
            }
            ### addon.cc
            #include <node_api.h>

            static napi_value Add(napi_env env, napi_callback_info info) {
              size_t argc = 2;
              napi_value args[2];
              napi_get_cb_info(env, info, &argc, args, NULL, NULL);
              double a, b;
              napi_get_value_double(env, args[0], &a);
              napi_get_value_double(env, args[1], &b);
              napi_value result;
              napi_create_double(env, a + b, &result);
              return result;
            }

            static napi_value Init(napi_env env, napi_value exports) {
              napi_value fn;
              napi_create_function(env, NULL, 0, Add, NULL, &fn);
              napi_set_named_property(env, exports, "add", fn);
              return exports;
            }

            NAPI_MODULE(NODE_GYP_MODULE_NAME, Init)
            ### index.js
            const addon = require("./build/Release/addon");
            console.log(addon.add(20, 22));
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.CROSS,
        section = "Python and Rust",
        title = "A Python module written in Rust",
        originalTitle = "PyO3 extension module",
        author = "The PyO3 project and its contributors",
        year = 2017,
        summary = "A Rust library compiled into a Python extension module, with a function that Python code calls like any other.",
        details = "PyO3 makes it routine to write Python modules in Rust, and many libraries used it for speed-critical code in the late 2010s. " +
            "The example uses a recent version of the PyO3 API, so the code shows the current form of the bindings rather than the first one.",
        instructions = """
            1. Create a Rust project with the files below, and build it with maturin develop.
            2. Run call.py from the same environment, which imports the new module.
        """,
        example = """
            ### Cargo.toml
            [lib]
            name = "mathlib"
            crate-type = ["cdylib"]

            [dependencies]
            pyo3 = { version = "0.20", features = ["extension-module"] }
            ### src/lib.rs
            use pyo3::prelude::*;

            #[pyfunction]
            fn add_ints(a: i64, b: i64) -> PyResult<i64> {
                Ok(a + b)
            }

            #[pymodule]
            fn mathlib(_py: Python<'_>, m: &PyModule) -> PyResult<()> {
                m.add_function(wrap_pyfunction!(add_ints, m)?)?;
                Ok(())
            }
            ### call.py
            import mathlib
            print(mathlib.add_ints(20, 22))
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.CROSS,
        section = "C and WebAssembly",
        title = "C compiled to WebAssembly with Emscripten",
        originalTitle = "Emscripten and WebAssembly",
        author = "The Emscripten project and the WebAssembly community",
        year = 2017,
        summary = "A C function compiled to WebAssembly and called from a web page, with the Emscripten toolchain writing the glue code.",
        details = "Emscripten compiled C to JavaScript, first as asm.js, and added WebAssembly output when browsers began running the format in 2017. " +
            "The build script exports one C function, and the page calls it through the generated module object.",
        instructions = """
            1. Install the Emscripten SDK and put the files below in one folder.
            2. Build: sh build.sh
            3. Open index.html through a local web server and read the console.
        """,
        example = """
            ### add.c
            int add_ints(int a, int b) {
              return a + b;
            }
            ### build.sh
            emcc add.c -O2 -s EXPORTED_FUNCTIONS="['_add_ints']" -s EXPORTED_RUNTIME_METHODS="['ccall']" -o add.js
            ### index.html
            <script src="add.js"></script>
            <script>
              Module.onRuntimeInitialized = function () {
                console.log(Module.ccall("add_ints", "number", ["number", "number"], [20, 22]));
              };
            </script>
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.CROSS,
        section = "Version control",
        title = "A Git clean filter written in Python",
        originalTitle = "clean filter",
        author = "Git filter attribute practice",
        year = 2015,
        summary = "A filter that Git runs on each staged file, here removing trailing spaces before the content is stored.",
        details = "Git's clean and smudge filters let a repository change file content on the way in and on the way out. Git LFS made the pattern widely known in 2015. " +
            "The filter is set in the repository configuration and matched to files through .gitattributes, so the working copy stays as the author typed it.",
        instructions = """
            1. Save the two files below in one folder.
            2. Run the setup script in that folder: sh setup.sh
            3. The stored file has no trailing spaces, even though the working copy had them.
        """,
        example = """
            ### clean.py
            import sys

            for line in sys.stdin:
                sys.stdout.write(line.rstrip(" \t\n") + "\n")
            ### setup.sh
            git init -q repo
            cd repo
            git config user.name "Ann"
            git config user.email "ann@example.com"
            git config filter.trim.clean "python3 ../clean.py"
            echo "*.txt filter=trim" > .gitattributes
            printf "hello   \n" > notes.txt
            git add .gitattributes notes.txt
            git commit -qm "notes"
            git show HEAD:notes.txt
        """,
        rare = true,
    ),
)
