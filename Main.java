package com.dsalearning;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * DSA Adaptive Learning Path backend.
 *
 * Endpoints:
 *   POST /api/session/start                          -> { sessionId }
 *   GET  /api/session/{id}/style-quiz                 -> quiz questions/options
 *   POST /api/session/{id}/style-quiz                 -> body { "answers": [0,3,1,2,0] } -> preferredStyle
 *   POST /api/session/{id}/topic                      -> body { "topic": "Arrays" } -> first question
 *   POST /api/session/{id}/answer                      -> body { "answerIndex": 2 } -> grade + next question
 *   GET  /api/session/{id}/dashboard                  -> stats, weekly plan, feedback
 *
 * Env var required: GEMINI_API_KEY
 * Run:
 *   javac -d out src/com/dsalearning/*.java
 *   GEMINI_API_KEY=sk-... java -cp out com.dsalearning.Main
 */
public class Main {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new ApiHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
        System.out.println("DSA learning backend running on http://localhost:" + port);
        if (System.getenv("GEMINI_API_KEY") == null) {
            System.out.println("WARNING: GEMINI_API_KEY is not set - AI calls will fail.");
        }
    }

    static class ApiHandler implements HttpHandler {        
        @Override
        
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // CORS so a browser frontend can call this directly during the demo
            exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
            if ("OPTIONS".equals(method)) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            try {
                String[] parts = path.split("/"); // ["", "api", "session", ...]

                if ("POST".equals(method) && path.equals("/api/session/start")) {
                    handleStart(exchange);
                    return;
                }
                if (parts.length >= 4 && "session".equals(parts[2])) {
                    String sessionId = parts[3];
                    Session session = SessionManager.get(sessionId);
                    if (session == null) {
                        writeJson(exchange, 404, "{\"error\":\"session not found\"}");
                        return;
                    }
                    String sub = parts.length >= 5 ? parts[4] : "";

                    if ("GET".equals(method) && "style-quiz".equals(sub)) {
                        handleGetStyleQuiz(exchange);
                        return;
                    }
                    if ("POST".equals(method) && "style-quiz".equals(sub)) {
                        handlePostStyleQuiz(exchange, session);
                        return;
                    }
                    if ("POST".equals(method) && "topic".equals(sub)) {
                        handleSetTopic(exchange, session);
                        return;
                    }
                    if ("POST".equals(method) && "answer".equals(sub)) {
                        handleAnswer(exchange, session);
                        return;
                    }
                    if ("GET".equals(method) && "dashboard".equals(sub)) {
                        handleDashboard(exchange, session);
                        return;
                    }
                }

                writeJson(exchange, 404, "{\"error\":\"not found\"}");
            } catch (Exception e) {
                e.printStackTrace();
                writeJson(exchange, 500, "{\"error\":" + MiniJson.quote(String.valueOf(e.getMessage())) + "}");
            }
        }

        // ---- handlers -------------------------------------------------------

        private void handleStart(HttpExchange exchange) throws IOException {
            Session session = SessionManager.create();
            writeJson(exchange, 200, "{\"sessionId\":" + MiniJson.quote(session.id) + "}");
        }

        private void handleGetStyleQuiz(HttpExchange exchange) throws IOException {
            StringBuilder sb = new StringBuilder("{\"questions\":[");
            for (int i = 0; i < StyleQuiz.QUESTIONS.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("{\"prompt\":").append(MiniJson.quote(StyleQuiz.QUESTIONS.get(i)));
                sb.append(",\"options\":[");
                List<String> opts = StyleQuiz.OPTIONS.get(i);
                for (int j = 0; j < opts.size(); j++) {
                    if (j > 0) sb.append(",");
                    sb.append(MiniJson.quote(opts.get(j)));
                }
                sb.append("]}");
            }
            sb.append("]}");
            writeJson(exchange, 200, sb.toString());
        }

        @SuppressWarnings("unchecked")
        private void handlePostStyleQuiz(HttpExchange exchange, Session session) throws IOException {
            Map<String, Object> body = (Map<String, Object>) MiniJson.parse(readBody(exchange));
            List<Object> rawAnswers = (List<Object>) body.get("answers");
            List<Integer> answers = new ArrayList<>();
            for (Object o : rawAnswers) answers.add(((Double) o).intValue());

            Map<String, Integer> tally = StyleQuiz.tally(answers);
            for (Map.Entry<String, Integer> e : tally.entrySet()) {
                session.styleScores.merge(e.getKey(), e.getValue(), Integer::sum);
            }
            session.recomputePreferredStyle();

            writeJson(exchange, 200, "{\"preferredStyle\":" + MiniJson.quote(session.preferredStyle)
                    + ",\"styleScores\":" + mapToJson(session.styleScores) + "}");
        }

        @SuppressWarnings("unchecked")
        private void handleSetTopic(HttpExchange exchange, Session session) throws IOException {
            Map<String, Object> body = (Map<String, Object>) MiniJson.parse(readBody(exchange));
            String topic = (String) body.get("topic");
            session.currentTopic = topic;

            int difficulty = session.getDifficulty(topic);
            Question q = ClaudeClient.generateQuestion(topic, difficulty);
            session.activeQuestion = q;
            session.questionStartTimeMs = System.currentTimeMillis();

            writeJson(exchange, 200, questionToJson(q));
        }

        @SuppressWarnings("unchecked")
        private void handleAnswer(HttpExchange exchange, Session session) throws IOException {
            Map<String, Object> body = (Map<String, Object>) MiniJson.parse(readBody(exchange));
            int answerIndex = ((Double) body.get("answerIndex")).intValue();

            Question q = session.activeQuestion;
            if (q == null) {
                writeJson(exchange, 400, "{\"error\":\"no active question - call /topic first\"}");
                return;
            }

            boolean correct = (answerIndex == q.correctIndex);
            long timeTaken = System.currentTimeMillis() - session.questionStartTimeMs;
            session.history.add(new QARecord(q.topic, q.difficulty, q.text, answerIndex, correct, timeTaken));

            // adaptive difficulty: feature 4
            int newDifficulty = session.getDifficulty(q.topic) + (correct ? 1 : -1);
            session.setDifficulty(q.topic, newDifficulty);

            // learning style module: reinforce whichever style most recently helped them recover
            String explanation = null;
            if (!correct) {
                explanation = ClaudeClient.generateExplanation(q, session.preferredStyle);
            }

            Question next = ClaudeClient.generateQuestion(q.topic, session.getDifficulty(q.topic));
            session.activeQuestion = next;
            session.questionStartTimeMs = System.currentTimeMillis();

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"correct\":").append(correct).append(",");
            sb.append("\"correctIndex\":").append(q.correctIndex).append(",");
            if (explanation != null) {
                sb.append("\"explanation\":").append(MiniJson.quote(explanation)).append(",");
            }
            sb.append("\"nextQuestion\":").append(questionToJson(next));
            sb.append("}");
            writeJson(exchange, 200, sb.toString());
        }

        private void handleDashboard(HttpExchange exchange, Session session) throws IOException {
            Map<String, int[]> perTopic = new LinkedHashMap<>(); // topic -> [correct, total]
            for (QARecord r : session.history) {
                int[] counts = perTopic.computeIfAbsent(r.topic, k -> new int[2]);
                counts[1]++;
                if (r.correct) counts[0]++;
            }

            StringBuilder statsSb = new StringBuilder("[");
            boolean first = true;
            for (Map.Entry<String, int[]> e : perTopic.entrySet()) {
                if (!first) statsSb.append(",");
                first = false;
                int correct = e.getValue()[0], total = e.getValue()[1];
                double accuracy = total == 0 ? 0 : (100.0 * correct / total);
                statsSb.append("{\"topic\":").append(MiniJson.quote(e.getKey()))
                        .append(",\"correct\":").append(correct)
                        .append(",\"total\":").append(total)
                        .append(",\"accuracyPct\":").append(String.format("%.1f", accuracy))
                        .append(",\"currentDifficulty\":").append(session.getDifficulty(e.getKey()))
                        .append("}");
            }
            statsSb.append("]");

            String feedback = ClaudeClient.generateSessionFeedback(session);
            String weeklyPlan = ClaudeClient.generateWeeklyPlan(session);

            StringBuilder sb = new StringBuilder("{");
            sb.append("\"preferredStyle\":").append(MiniJson.quote(session.preferredStyle)).append(",");
            sb.append("\"topicStats\":").append(statsSb).append(",");
            sb.append("\"feedback\":").append(MiniJson.quote(feedback)).append(",");
            sb.append("\"weeklyPlan\":").append(MiniJson.quote(weeklyPlan));
            sb.append("}");
            writeJson(exchange, 200, sb.toString());
        }

        // ---- helpers ----------------------------------------------------------

        private String questionToJson(Question q) {
            // Note: correctIndex intentionally omitted here so the client can't see the answer early;
            // it's only revealed in the /answer response.
            StringBuilder sb = new StringBuilder("{");
            sb.append("\"topic\":").append(MiniJson.quote(q.topic)).append(",");
            sb.append("\"difficulty\":").append(q.difficulty).append(",");
            sb.append("\"question\":").append(MiniJson.quote(q.text)).append(",");
            sb.append("\"options\":[");
            for (int i = 0; i < q.options.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(MiniJson.quote(q.options.get(i)));
            }
            sb.append("]}");
            return sb.toString();
        }

        private String mapToJson(Map<String, Integer> map) {
            String body = map.entrySet().stream()
                    .map(e -> MiniJson.quote(e.getKey()) + ":" + e.getValue())
                    .collect(Collectors.joining(","));
            return "{" + body + "}";
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream is = exchange.getRequestBody()) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }

        private void writeJson(HttpExchange exchange, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
