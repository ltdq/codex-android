package com.cy.codexui.protocol.protocol

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The JSON layer is hand-written, so it is the one piece of the protocol that cannot rely on a
 * generated schema to be correct. These tests pin the behaviour the transport depends on.
 */
class JsonValueTest {

    @Test
    fun parsesScalars() {
        assertEquals(JsonValue.Null, Json.parse("null"))
        assertEquals(JsonValue.Bool(true), Json.parse("true"))
        assertEquals(JsonValue.Bool(false), Json.parse("false"))
        assertEquals(JsonValue.Num(42.0), Json.parse("42"))
        assertEquals(JsonValue.Num(-1.5), Json.parse("-1.5"))
        assertEquals(JsonValue.Str("hi"), Json.parse("\"hi\""))
    }

    @Test
    fun parsesNestedStructures() {
        val value = Json.parse(
            """{"method":"thread/read","params":{"threadId":"t1","items":[{"id":"i1"},null]}}""",
        )
        val obj = value as JsonValue.Obj
        assertEquals("thread/read", (obj["method"] as JsonValue.Str).value)
        val params = obj["params"] as JsonValue.Obj
        assertEquals("t1", (params["threadId"] as JsonValue.Str).value)
        val items = params["items"] as JsonValue.Arr
        assertEquals(2, items.values.size)
        assertEquals(JsonValue.Null, items.values[1])
    }

    @Test
    fun parsesStringEscapes() {
        val value = Json.parse("""{"path":"a\/b\n\t\"q\"","unicode":"\u0041"}""") as JsonValue.Obj
        assertEquals("a/b\n\t\"q\"", (value["path"] as JsonValue.Str).value)
        assertEquals("A", (value["unicode"] as JsonValue.Str).value)
    }

    @Test
    fun roundTripsThroughWrite() {
        val source = """{"a":[1,2,{"b":"x\ny"}],"c":true,"d":null}"""
        val parsed = Json.parse(source)
        assertEquals(source, Json.write(parsed))
    }

    @Test
    fun reportsMalformedInput() {
        assertFailsWith<JsonParseException> { Json.parse("{") }
        assertFailsWith<JsonParseException> { Json.parse("{\"a\":}") }
        assertFailsWith<JsonParseException> { Json.parse("[1,2") }
        assertFailsWith<JsonParseException> { Json.parse("\"unterminated") }
        assertFailsWith<JsonParseException> { Json.parse("tru") }
        assertFailsWith<JsonParseException> { Json.parse("{} trailing") }
    }

    @Test
    fun parseOrNullSwallowsErrors() {
        assertNull(Json.parseOrNull("{"))
        assertEquals(JsonValue.Num(1.0), Json.parseOrNull("1"))
    }

    @Test
    fun integralNumbersPrintWithoutDecimalPoint() {
        assertEquals("1", Json.write(JsonValue.Num(1.0)))
        assertEquals("1.5", Json.write(JsonValue.Num(1.5)))
    }

    @Test
    fun requestIdAcceptsNumbersAndStrings() {
        assertEquals("42", RequestId(42L).value)
        assertEquals("abc", RequestId("abc").value)
        assertEquals(42L, RequestId("42").asLongOrNull)
        assertNull(RequestId("abc").asLongOrNull)
        assertTrue(RequestId(7L) == RequestId("7"))
    }

    @Test
    fun jsonRpcErrorCarriesStandardCodes() {
        assertEquals(-32700, JsonRpcError.ParseError)
        assertEquals(-32603, JsonRpcError.InternalError)
        val response = JsonRpcResponse(id = RequestId(1L), error = JsonRpcError(-32601, "no such method"))
        assertTrue(response.isError)
    }
}
