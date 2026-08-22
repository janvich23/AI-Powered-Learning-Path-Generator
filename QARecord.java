package com.dsalearning;

public class QARecord {
    public final String topic;
    public final int difficulty;
    public final String questionText;
    public final int userAnswerIndex;
    public final boolean correct;
    public final long timeTakenMs;

    public QARecord(String topic, int difficulty, String questionText,
                     int userAnswerIndex, boolean correct, long timeTakenMs) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.questionText = questionText;
        this.userAnswerIndex = userAnswerIndex;
        this.correct = correct;
        this.timeTakenMs = timeTakenMs;
    }
}
