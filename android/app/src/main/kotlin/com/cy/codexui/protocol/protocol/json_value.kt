package com.cy.codexui.protocol.protocol

import kotlin.math.floor

/**
 * Minimal JSON value model plus a parser and printer.
 *
 * The Rust side gets this from `serde_json`; the phone only needs enough to carry untyped request
 * params and decode the fields it renders, so the tree is small and handwritten rather than
 * generated. Keeping it here (instead of pulling in a serialization runtime) means the wire format
 * has exactly one implementation and no plugin/dependency surface.
 *
 * Numbers are held as [Double] because the protocol's untyped payloads are only ever echoed back or
 * rendered; every strongly-typed field on a protocol class is decoded from the tree directly.
 */
sealed interface JsonValue {
    data object Null : JsonValue
    data class Bool(val value: Boolean) : JsonValue
    data class Num(val value: Double) : JsonValue {
        val isIntegral: Boolean get() = value == floor(value) && !value.isInfinite()
        fun toInt(): Int = value.toInt()
        fun toLong(): Long = value.toLong()
    }

    data class Str(val value: String) : JsonValue
    data class Arr(val values: List<JsonValue>) : JsonValue
    data class Obj(val fields: Map<String, JsonValue>) : JsonValue {
        operator fun get(key: String): JsonValue? = fields[key]
    }

    companion object {
        fun of(value: String?): JsonValue = if (value == null) Null else Str(value)
        fun of(value: Boolean): JsonValue = Bool(value)
        fun of(value: Int): JsonValue = Num(value.toDouble())
        fun of(value: Long): JsonValue = Num(value.toDouble())
        fun of(value: Double): JsonValue = Num(value)
        fun of(values: List<JsonValue>): JsonValue = Arr(values)
    }
}

/** Decode JSON text into a tree. Throws [JsonParseException] on malformed input. */
object Json {
    fun parse(text: String): JsonValue {
        val parser = Parser(text)
        val value = parser.parseValue()
        parser.skipWhitespace()
        if (!parser.atEnd) throw JsonParseException("尾部有多余内容：位置 ${parser.position}")
        return value
    }

    fun parseOrNull(text: String): JsonValue? = try {
        parse(text)
    } catch (_: JsonParseException) {
        null
    }

    /** Encode a tree back to text. Object key order follows insertion order. */
    fun write(value: JsonValue): String = buildString { writeTo(value, this) }

    private fun writeTo(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> out.append(if (value.isIntegral) value.toLong().toString() else value.value.toString())
            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Arr -> {
                out.append('[')
                value.values.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    writeTo(item, out)
                }
                out.append(']')
            }

            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                value.fields.forEach { (key, item) ->
                    if (!first) out.append(',')
                    first = false
                    writeString(key, out)
                    out.append(':')
                    writeTo(item, out)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(text: String, out: StringBuilder) {
        out.append('"')
        for (char in text) {
            when (char) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                else -> if (char < ' ') {
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(char)
                }
            }
        }
        out.append('"')
    }

    private class Parser(private val text: String) {
        var position = 0
            private set

        val atEnd: Boolean get() = position >= text.length

        fun skipWhitespace() {
            while (position < text.length && text[position].isWhitespace()) position++
        }

        fun parseValue(): JsonValue {
            skipWhitespace()
            if (atEnd) throw JsonParseException("内容为空")
            return when (val char = text[position]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> JsonValue.Str(parseString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> if (char == '-' || char.isDigit()) parseNumber() else {
                    throw JsonParseException("位置 $position 出现意外字符 '$char'")
                }
            }
        }

        private fun <T : JsonValue> literal(word: String, value: T): T {
            if (!text.startsWith(word, position)) throw JsonParseException("位置 $position 期望 $word")
            position += word.length
            return value
        }

        private fun parseObject(): JsonValue {
            position++ // '{'
            val fields = LinkedHashMap<String, JsonValue>()
            skipWhitespace()
            if (!atEnd && text[position] == '}') {
                position++
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                if (atEnd || text[position] != ':') throw JsonParseException("位置 $position 期望 ':'")
                position++
                fields[key] = parseValue()
                skipWhitespace()
                if (atEnd) throw JsonParseException("对象没有闭合")
                when (text[position]) {
                    ',' -> position++
                    '}' -> {
                        position++
                        return JsonValue.Obj(fields)
                    }

                    else -> throw JsonParseException("位置 $position 期望 ',' 或 '}'")
                }
            }
        }

        private fun parseArray(): JsonValue {
            position++ // '['
            val values = mutableListOf<JsonValue>()
            skipWhitespace()
            if (!atEnd && text[position] == ']') {
                position++
                return JsonValue.Arr(values)
            }
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (atEnd) throw JsonParseException("数组没有闭合")
                when (text[position]) {
                    ',' -> position++
                    ']' -> {
                        position++
                        return JsonValue.Arr(values)
                    }

                    else -> throw JsonParseException("位置 $position 期望 ',' 或 ']'")
                }
            }
        }

        private fun parseString(): String {
            if (atEnd || text[position] != '"') throw JsonParseException("位置 $position 期望字符串")
            position++
            val out = StringBuilder()
            while (true) {
                if (atEnd) throw JsonParseException("字符串没有闭合")
                when (val char = text[position]) {
                    '"' -> {
                        position++
                        return out.toString()
                    }

                    '\\' -> {
                        position++
                        if (atEnd) throw JsonParseException("转义没有结束")
                        when (val escape = text[position]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                if (position + 4 >= text.length) throw JsonParseException("\\u 转义不完整")
                                val hex = text.substring(position + 1, position + 5)
                                out.append(hex.toInt(16).toChar())
                                position += 4
                            }

                            else -> throw JsonParseException("未知转义 \\$escape")
                        }
                        position++
                    }

                    else -> {
                        out.append(char)
                        position++
                    }
                }
            }
        }

        private fun parseNumber(): JsonValue {
            val start = position
            if (!atEnd && text[position] == '-') position++
            while (!atEnd && (text[position].isDigit() || text[position] in ".eE+-")) position++
            val slice = text.substring(start, position)
            val number = slice.toDoubleOrNull() ?: throw JsonParseException("非法数字 '$slice'")
            return JsonValue.Num(number)
        }
    }
}

class JsonParseException(message: String) : IllegalArgumentException(message)
