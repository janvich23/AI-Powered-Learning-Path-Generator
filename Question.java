package com.dsalearning;

import java.util.List;

public class Question {
    public final String text;
    public final List<String> options;
    public final int correctIndex;
    public final String topic;
    public final int difficulty;

    public Question(String text, List<String> options, int correctIndex, String topic, int difficulty) {
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
        this.topic = topic;
        this.difficulty = difficulty;
    }
}
