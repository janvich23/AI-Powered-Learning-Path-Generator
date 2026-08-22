package com.dsalearning;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public static Session create() {
        String id = UUID.randomUUID().toString();
        Session s = new Session(id);
        sessions.put(id, s);
        return s;
    }

    public static Session get(String id) {
        return sessions.get(id);
    }
}
