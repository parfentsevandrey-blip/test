package app.opal.core.model.tor

/**
 * Tor control protocol replies (control-spec §2.3 / §4).
 *
 * A reply is a sequence of `NNN-text` (mid), `NNN+text` + data block (data) lines terminated by one
 * `NNN text` (end) line. Asynchronous events use status code 650.
 */
data class ReplyLine(val code: Int, val text: String, val data: String? = null)

data class ControlReply(val lines: List<ReplyLine>) {
    init {
        require(lines.isNotEmpty()) { "A reply has at least one line" }
    }

    /** Status code of the terminating line; decides success/failure of the whole reply. */
    val code: Int
        get() = lines.last().code

    val isAsync: Boolean
        get() = code == ASYNC_EVENT_CODE

    val isSuccess: Boolean
        get() = code in 200..299

    /** Human-readable message for errors, e.g. `552 Unrecognized option`. */
    val message: String
        get() = lines.last().text

    companion object {
        const val ASYNC_EVENT_CODE = 650
    }
}

class ControlProtocolException(message: String) : RuntimeException(message)

/**
 * Incrementally assembles [ControlReply]s from raw protocol lines (without CRLF). Stateful and not
 * thread-safe: feed it from the single reader coroutine.
 */
class ReplyAssembler {
    private val lines = mutableListOf<ReplyLine>()
    private var pendingData: StringBuilder? = null
    private var pendingCode = 0
    private var pendingText = ""

    /** Returns a complete reply when [line] terminates one, otherwise null. */
    fun feed(line: String): ControlReply? {
        val data = pendingData
        if (data != null) {
            if (line == ".") {
                lines += ReplyLine(pendingCode, pendingText, data.toString())
                pendingData = null
            } else {
                // Leading dots are escaped by doubling them (control-spec §2.2).
                if (data.isNotEmpty()) data.append('\n')
                data.append(if (line.startsWith("..")) line.substring(1) else line)
            }
            return null
        }
        if (line.length < 3) throw ControlProtocolException("Line too short: '$line'")
        val code =
            line.substring(0, 3).toIntOrNull()
                ?: throw ControlProtocolException("Bad status code in '$line'")
        val separator = if (line.length > 3) line[3] else ' '
        val text = if (line.length > 4) line.substring(4) else ""
        return when (separator) {
            '-' -> {
                lines += ReplyLine(code, text)
                null
            }
            '+' -> {
                pendingCode = code
                pendingText = text
                pendingData = StringBuilder()
                null
            }
            ' ' -> {
                lines += ReplyLine(code, text)
                val reply = ControlReply(lines.toList())
                lines.clear()
                reply
            }
            else -> throw ControlProtocolException("Bad separator '$separator' in '$line'")
        }
    }
}

/** Tokenizer for control-protocol arguments: words, `KEY=value` and `KEY="quoted value"`. */
object ControlArgs {

    data class Parsed(val positional: List<String>, val keywords: Map<String, String>)

    fun parse(text: String): Parsed {
        val positional = mutableListOf<String>()
        val keywords = LinkedHashMap<String, String>()
        var i = 0
        while (i < text.length) {
            while (i < text.length && text[i] == ' ') i++
            if (i >= text.length) break
            val tokenStart = i
            var eq = -1
            while (i < text.length && text[i] != ' ' && text[i] != '"') {
                if (text[i] == '=' && eq < 0) eq = i
                i++
            }
            if (eq >= 0) {
                val key = text.substring(tokenStart, eq)
                if (i < text.length && text[i] == '"' && i == eq + 1) {
                    val (value, next) = unquote(text, i)
                    keywords[key] = value
                    i = next
                } else {
                    keywords[key] = text.substring(eq + 1, i)
                }
            } else if (i < text.length && text[i] == '"' && i == tokenStart) {
                val (value, next) = unquote(text, i)
                positional += value
                i = next
            } else {
                positional += text.substring(tokenStart, i)
            }
        }
        return Parsed(positional, keywords)
    }

    /** Reads a C-style quoted string starting at [start] (the opening quote). */
    fun unquote(text: String, start: Int): Pair<String, Int> {
        require(text[start] == '"')
        val out = StringBuilder()
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            when {
                c == '"' -> return out.toString() to i + 1
                c == '\\' && i + 1 < text.length -> {
                    val n = text[i + 1]
                    when (n) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        in '0'..'7' -> {
                            var j = i + 1
                            var value = 0
                            while (j < text.length && j < i + 4 && text[j] in '0'..'7') {
                                value = value * 8 + (text[j] - '0')
                                j++
                            }
                            out.append(value.toChar())
                            i = j
                            continue
                        }
                        else -> out.append(n)
                    }
                    i += 2
                    continue
                }
                else -> out.append(c)
            }
            i++
        }
        // Unterminated quote: be lenient and return what we have.
        return out.toString() to text.length
    }

    /** Quotes a value for SETCONF / commands when needed (spaces, quotes, backslashes, empty). */
    private fun needsQuoting(c: Char): Boolean = c == ' ' || c == '"' || c == '\\' || c < ' '

    fun quote(value: String): String {
        if (value.isNotEmpty() && value.none(::needsQuoting)) return value
        val sb = StringBuilder("\"")
        for (c in value) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(c)
            }
        }
        return sb.append('"').toString()
    }
}

/** Parses the body of a successful `GETINFO` reply into key → value. */
object GetInfoParser {
    fun parse(reply: ControlReply): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for (line in reply.lines) {
            if (line.data != null) {
                result[line.text.substringBefore('=')] = line.data
            } else {
                val eq = line.text.indexOf('=')
                if (eq > 0) result[line.text.substring(0, eq)] = line.text.substring(eq + 1)
            }
        }
        return result
    }
}
