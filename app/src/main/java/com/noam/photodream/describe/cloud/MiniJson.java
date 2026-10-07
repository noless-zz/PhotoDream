package com.noam.photodream.describe.cloud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tiny JSON reader/writer in plain Java. Android's own org.json does not exist in JVM unit
 * tests, and the request/response shapes of the AI services are the part worth testing, so this
 * keeps them testable without adding a library.
 * Values come back as Map, List, String, Double, Boolean or null.
 */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) { this.s = s; }

    /** Parses text; throws IllegalArgumentException if it is not valid JSON. */
    public static Object parse(String text) {
        if (text == null) throw new IllegalArgumentException("null");
        MiniJson p = new MiniJson(text);
        Object v = p.value();
        p.skipSpace();
        if (p.i != text.length()) throw new IllegalArgumentException("Trailing text");
        return v;
    }

    /** A JSON string literal, quotes included. */
    public static String quote(String v) {
        StringBuilder b = new StringBuilder("\"");
        for (int k = 0; k < v.length(); k++) {
            char c = v.charAt(k);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    /** Safe path lookup: keys (String) go into maps, indexes (Integer) into lists; null if anything is missing. */
    public static Object get(Object root, Object... path) {
        Object cur = root;
        for (Object step : path) {
            if (cur instanceof Map && step instanceof String) {
                cur = ((Map<?, ?>) cur).get(step);
            } else if (cur instanceof List && step instanceof Integer) {
                List<?> l = (List<?>) cur;
                int idx = (Integer) step;
                cur = idx >= 0 && idx < l.size() ? l.get(idx) : null;
            } else {
                return null;
            }
            if (cur == null) return null;
        }
        return cur;
    }

    /** Like {@link #get} but only if the result is a String. */
    public static String getString(Object root, Object... path) {
        Object v = get(root, path);
        return v instanceof String ? (String) v : null;
    }

    // ---------------------------------------------------------------- parser

    private Object value() {
        skipSpace();
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        skipSpace();
        if (peek() == '}') { i++; return m; }
        while (true) {
            skipSpace();
            if (peek() != '"') throw new IllegalArgumentException("Expected key");
            String k = string();
            skipSpace();
            expect(':');
            m.put(k, value());
            skipSpace();
            char c = next();
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("Expected , or }");
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        skipSpace();
        if (peek() == ']') { i++; return l; }
        while (true) {
            l.add(value());
            skipSpace();
            char c = next();
            if (c == ']') return l;
            if (c != ',') throw new IllegalArgumentException("Expected , or ]");
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;                                           // opening quote
        while (true) {
            char c = next();
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            char e = next();
            switch (e) {
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new IllegalArgumentException("Bad escape");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: b.append(e);                   // \" \\ \/
            }
        }
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw new IllegalArgumentException("Unexpected character at " + i);
        return Double.valueOf(s.substring(start, i));
    }

    private void skipSpace() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

    private char peek() {
        if (i >= s.length()) throw new IllegalArgumentException("Unexpected end");
        return s.charAt(i);
    }

    private char next() {
        char c = peek();
        i++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) throw new IllegalArgumentException("Expected " + c);
    }
}
