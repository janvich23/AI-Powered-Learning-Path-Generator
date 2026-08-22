package com.dsalearning;

import java.util.*;

/**
 * Minimal JSON parser + escaper. No external dependencies on purpose,
 * so the whole project builds with plain javac/java.
 *
 * parse() returns a tree of: Map<String,Object>, List<Object>, String, Double, Boolean, or null.
 */
public class MiniJson {

    public static Object parse(String json) {
        Parser p = new Parser(json);
        return p.parseValue();
    }

    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    public static String quote(String s) {
        return "\"" + escape(s) + "\"";
    }

    private static class Parser {
        private final String s;
        private int pos = 0;

        Parser(String s) { this.s = s; }

        private void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        Object parseValue() {
            skipWs();
            char c = s.charAt(pos);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't' || c == 'f') return parseBool();
            if (c == 'n') { pos += 4; return null; }
            return parseNumber();
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWs();
            if (s.charAt(pos) == '}') { pos++; return map; }
            while (true) {
                skipWs();
                String key = parseString();
                skipWs();
                pos++; // :
                Object val = parseValue();
                map.put(key, val);
                skipWs();
                char c = s.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == '}') { pos++; break; }
            }
            return map;
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWs();
            if (s.charAt(pos) == ']') { pos++; return list; }
            while (true) {
                Object val = parseValue();
                list.add(val);
                skipWs();
                char c = s.charAt(pos);
                if (c == ',') { pos++; continue; }
                if (c == ']') { pos++; break; }
            }
            return list;
        }

        String parseString() {
            skipWs();
            pos++; // opening quote
            StringBuilder sb = new StringBuilder();
            while (s.charAt(pos) != '"') {
                char c = s.charAt(pos);
                if (c == '\\') {
                    pos++;
                    char esc = s.charAt(pos);
                    switch (esc) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            String hex = s.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                            break;
                        default: sb.append(esc);
                    }
                    pos++;
                } else {
                    sb.append(c);
                    pos++;
                }
            }
            pos++; // closing quote
            return sb.toString();
        }

        Boolean parseBool() {
            if (s.startsWith("true", pos)) { pos += 4; return Boolean.TRUE; }
            pos += 5;
            return Boolean.FALSE;
        }

        Double parseNumber() {
            int start = pos;
            while (pos < s.length() && "-+.eE0123456789".indexOf(s.charAt(pos)) >= 0) pos++;
            return Double.parseDouble(s.substring(start, pos));
        }
    }
}
