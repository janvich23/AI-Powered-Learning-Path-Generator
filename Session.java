package com.dsalearning;

import java.util.*;

public class Session {

    public final String id;
    public String currentTopic;

    // difficulty per topic, 1 (easiest) - 5 (hardest); default starting difficulty = 2
    public final Map<String, Integer> difficultyByTopic = new HashMap<>();

    // tallies from the learning-style quiz + ongoing adjustment
    public final Map<String, Integer> styleScores = new HashMap<>();

    public String preferredStyle = "written";

    public final List<QARecord> history = new ArrayList<>();

    // the question currently awaiting an answer from the student
    public Question activeQuestion;
    public long questionStartTimeMs;

    public Session(String id) {
        this.id = id;
        styleScores.put("visual", 0);
        styleScores.put("auditory", 0);
        styleScores.put("written", 0);
        styleScores.put("action", 0);
    }

    public int getDifficulty(String topic) {
        return difficultyByTopic.getOrDefault(topic, 2);
    }

    public void setDifficulty(String topic, int d) {
        difficultyByTopic.put(topic, Math.max(1, Math.min(5, d)));
    }

    /** Recomputes preferredStyle as whichever style currently has the highest tally. */
    public void recomputePreferredStyle() {
        String best = "written";
        int bestScore = Integer.MIN_VALUE;
        for (Map.Entry<String, Integer> e : styleScores.entrySet()) {
            if (e.getValue() > bestScore) {
                bestScore = e.getValue();
                best = e.getKey();
            }
        }
        preferredStyle = best;
    }
}
