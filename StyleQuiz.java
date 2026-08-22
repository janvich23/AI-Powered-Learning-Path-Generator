package com.dsalearning;

import java.util.*;

/**
 * A fixed 5-question self-report quiz used to seed a student's initial
 * learning style. Each question's options are always ordered
 * [visual, auditory, written, action], so an answer index maps directly
 * to a style via STYLE_ORDER.
 */
public class StyleQuiz {

    public static final List<String> STYLE_ORDER = List.of("visual", "auditory", "written", "action");

    public static final List<String> QUESTIONS = List.of(
            "When learning a new algorithm, I prefer to...",
            "To remember how a data structure works, I find it easiest to...",
            "When stuck on a problem, my first instinct is to...",
            "I understand recursion best when...",
            "When reviewing before an exam, I like to..."
    );

    public static final List<List<String>> OPTIONS = List.of(
            List.of(
                    "See a diagram of how it works step by step",
                    "Have someone talk me through it",
                    "Read detailed written notes or documentation",
                    "Try implementing it myself and learn by doing"
            ),
            List.of(
                    "Picture it visually (boxes, arrows, trees)",
                    "Explain it out loud to myself or someone else",
                    "Write out the definition and properties",
                    "Build it by hand / trace through operations on paper"
            ),
            List.of(
                    "Draw out the problem or look for a diagram",
                    "Talk through the problem out loud",
                    "Re-read the problem statement and notes carefully",
                    "Start coding and iterate until something works"
            ),
            List.of(
                    "I can see the call stack unwind visually",
                    "Someone walks me through an example verbally",
                    "I read a clear written breakdown of the base/recursive case",
                    "I trace through a small example by hand myself"
            ),
            List.of(
                    "Look at diagrams, flashcards, or mind maps",
                    "Listen to a recap or talk it through with someone",
                    "Re-read written notes and summaries",
                    "Do practice problems"
            )
    );

    /** Tallies a list of answer indices (0-3 each) into per-style scores. */
    public static Map<String, Integer> tally(List<Integer> answerIndices) {
        Map<String, Integer> scores = new HashMap<>();
        for (String style : STYLE_ORDER) scores.put(style, 0);
        for (Integer idx : answerIndices) {
            if (idx == null || idx < 0 || idx >= STYLE_ORDER.size()) continue;
            String style = STYLE_ORDER.get(idx);
            scores.merge(style, 1, Integer::sum);
        }
        return scores;
    }
}
