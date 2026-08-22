package com.dsalearning;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class Client {

    private static final String API_KEY = System.getenv("GEMINI_API_KEY");
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;
    private static final HttpClient http = HttpClient.newHttpClient();

    // ---- low-level call ----------------------------------------------------

    private static String callGemini(String systemPrompt, String userPrompt) throws IOException, InterruptedException {
        if (API_KEY == null || API_KEY.isEmpty()) {
            throw new IllegalStateException("GEMINI_API_KEY environment variable is not set");
        }

        // Combine system and user prompt for Gemini
        String combinedPrompt = systemPrompt + "\n\n" + userPrompt;

        String body = "{"
                + "\"contents\":[{\"parts\":[{\"text\":" + MiniJson.quote(combinedPrompt) + "}]}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Gemini API error " + response.statusCode() + ": " + response.body());
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parsed = (Map<String, Object>) MiniJson.parse(response.body());
        @SuppressWarnings("unchecked")
        List<Object> candidates = (List<Object>) parsed.get("candidates");
        @SuppressWarnings("unchecked")
        Map<String, Object> candidate = (Map<String, Object>) candidates.get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) candidate.get("content");
        @SuppressWarnings("unchecked")
        List<Object> parts = (List<Object>) content.get("parts");
        @SuppressWarnings("unchecked")
        Map<String, Object> firstPart = (Map<String, Object>) parts.get(0);

        return (String) firstPart.get("text");
    }

    private static String stripFences(String s) {
        s = s.trim();
        if (s.startsWith("```")) {
            int firstNewline = s.indexOf('\n');
            if (firstNewline >= 0) s = s.substring(firstNewline + 1);
            int lastFence = s.lastIndexOf("```");
            if (lastFence >= 0) s = s.substring(0, lastFence);
        }
        return s.trim();
    }

    // ---- feature 1 + 4: question generation, adaptive difficulty -----------

    public static Question generateQuestion(String topic, int difficulty) {
        String system = "You are a Data Structures and Algorithms (DSA) tutor for college-level computer "
                + "engineering students. Respond with ONLY a single JSON object, no markdown code fences, no "
                + "preamble or trailing text, matching exactly this schema: "
                + "{\"question\":\"...\",\"options\":[\"...\",\"...\",\"...\",\"...\"],\"correctIndex\":0}. "
                + "correctIndex is the 0-based index of the correct option. "
                + "Difficulty is on a 1-5 scale: 1 = basic definitions/recall, 3 = applying the concept to a "
                + "small example, 5 = complexity analysis / edge cases / combining concepts.";
        String user = "Generate one multiple-choice DSA question on the topic '" + topic
                + "' at difficulty level " + difficulty + " out of 5. Exactly 4 options.";
        try {
            String raw = stripFences(callGemini(system, user));
            @SuppressWarnings("unchecked")
            Map<String, Object> obj = (Map<String, Object>) MiniJson.parse(raw);
            String qtext = (String) obj.get("question");
            @SuppressWarnings("unchecked")
            List<Object> optsRaw = (List<Object>) obj.get("options");
            List<String> opts = new ArrayList<>();
            for (Object o : optsRaw) opts.add((String) o);
            int correctIndex = ((Double) obj.get("correctIndex")).intValue();
            return new Question(qtext, opts, correctIndex, topic, difficulty);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate question: " + e.getMessage(), e);
        }
    }

    // ---- feature 2: learning-style-adapted explanation ----------------------

    public static String generateExplanation(Question q, String style) {
        String styleInstruction;
        switch (style) {
            case "visual":
                styleInstruction = "Explain using a described diagram or step-by-step visual layout - lay it "
                        + "out with indentation/arrows/ASCII structure in text, since this channel is text-only.";
                break;
            case "auditory":
                styleInstruction = "Explain in a conversational, spoken-style narration, as if talking the "
                        + "student through it out loud, short sentences suitable for text-to-speech.";
                break;
            case "action":
                styleInstruction = "Explain by giving the student a small hands-on exercise or a manual trace "
                        + "they should walk through themselves step by step to discover the answer.";
                break;
            default:
                styleInstruction = "Explain in clear, structured written prose with the reasoning laid out "
                        + "step by step.";
        }
        String system = "You are a DSA tutor. The student answered a question incorrectly. " + styleInstruction
                + " Keep the whole explanation under 150 words.";
        String user = "Question: " + q.text + "\nOptions: " + q.options
                + "\nCorrect answer: " + q.options.get(q.correctIndex)
                + "\nExplain why this is correct and address the likely misconception behind a wrong guess.";
        try {
            return callGemini(system, user);
        } catch (Exception e) {
            return "Could not generate explanation (" + e.getMessage() + ")";
        }
    }

    // ---- feature 3 + 5: feedback and weekly plan -----------------------------

    public static String generateSessionFeedback(Session session) {
        StringBuilder historyStr = new StringBuilder();
        for (QARecord r : session.history) {
            historyStr.append("- topic=").append(r.topic)
                    .append(", difficulty=").append(r.difficulty)
                    .append(", correct=").append(r.correct)
                    .append(", timeSec=").append(r.timeTakenMs / 1000)
                    .append("\n");
        }
        String system = "You are an encouraging but honest DSA tutor giving a student a progress summary "
                + "based on their recent quiz session.";
        String user = "Session history:\n" + historyStr
                + "\nGive a short (under 120 words) feedback summary: what they're doing well, what to focus "
                + "on, and one concrete next step.";
        try {
            return callGemini(system, user);
        } catch (Exception e) {
            return "Could not generate feedback (" + e.getMessage() + ")";
        }
    }

    public static String generateWeeklyPlan(Session session) {
        StringBuilder topicStats = new StringBuilder();
        for (Map.Entry<String, Integer> e : session.difficultyByTopic.entrySet()) {
            topicStats.append("- ").append(e.getKey()).append(": current difficulty ").append(e.getValue()).append("/5\n");
        }
        String system = "You are a study planner for a college computer engineering student focused on DSA "
                + "(Data Structures and Algorithms).";
        String user = "Student's current topic difficulty levels:\n" + topicStats
                + "\nPreferred learning style: " + session.preferredStyle
                + "\nGenerate a simple 1-week study plan (5 days), 1-2 goals per day, prioritizing weaker "
                + "topics first, plain text with one line per day. Under 150 words total. Note this is a "
                + "projected plan, not one that accounts for the student's personal calendar.";
        try {
            return callGemini(system, user);
        } catch (Exception e) {
            return "Could not generate weekly plan (" + e.getMessage() + ")";
        }
    }
}