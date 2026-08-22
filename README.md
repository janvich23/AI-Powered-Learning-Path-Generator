# DSA Adaptive Learning Path - Backend (Java, no external deps)

Pure JDK (17+) HTTP backend for the 3-hour hackathon scope: single subject (DSA),
adaptive MCQ assessment, learning-style detection, style-adapted explanations,
session feedback, and a simulated weekly plan/dashboard.

No Maven/Gradle, no third-party libraries (a hand-rolled `MiniJson` class handles
all JSON parsing/writing) — just `javac` and `java`.

## Build & run

```bash
cd dsa-learning-backend
javac -d out src/com/dsalearning/*.java
GEMINI_API_KEY=sk-ant-your-key-here java -cp out com.dsalearning.Main
```

Server starts on `http://localhost:8080`.

## Flow / endpoints

1. **`POST /api/session/start`**
   Creates a session. Response: `{ "sessionId": "..." }`. Use this id in every
   subsequent call.

2. **`GET /api/session/{id}/style-quiz`**
   Returns the 5-question learning-style self-report quiz (visual/auditory/
   written/action). No AI call — it's static content, scored by index mapping.

3. **`POST /api/session/{id}/style-quiz`**
   Body: `{ "answers": [0, 3, 1, 2, 0] }` — one 0-3 index per question, in the
   order returned above.
   Response: `{ "preferredStyle": "...", "styleScores": {...} }`.

4. **`POST /api/session/{id}/topic`**
   Body: `{ "topic": "Arrays" }` — sets the subject topic and generates the
   first AI-authored MCQ question at the student's current difficulty
   (starts at 2/5 for a new topic).
   Response: the question (options included, correct answer withheld).

5. **`POST /api/session/{id}/answer`**
   Body: `{ "answerIndex": 2 }` — grades the current active question.
   - Correct → difficulty +1 (capped at 5)
   - Wrong → difficulty -1 (floored at 1), and an AI explanation is generated
     in the student's `preferredStyle`
   Response includes whether they were correct, the correct index, an
   explanation (if wrong), and the next question at the new difficulty.

6. **`GET /api/session/{id}/dashboard`**
   Response: per-topic accuracy/difficulty stats, an AI-generated feedback
   paragraph, and an AI-generated 5-day study plan.

## What's real vs. simulated 

- **Real**: adaptive difficulty within a session, learning-style detection,
  style-adapted explanations, per-question timing, AI-generated feedback.
- **Simulated**: the "weekly plan" is generated fresh from one session's data
  — there's no real multi-week tracking or calendar integration. Say so
  explicitly when you demo it; don't imply it's tracking real elapsed weeks.
- **Session storage is in-memory only** (a `ConcurrentHashMap`). Restarting
  the server wipes all sessions. Fine for a demo, not for production — swap
  in a real DB (Postgres/SQLite) if you extend this later.
- **Grading is MCQ-only**, not free-text evaluation, to keep grading
  instant/local rather than needing another AI round trip per answer.

## Extending later (not in scope for the 3-hour build)

- Persist sessions to a real database
- Free-text answer grading via an additional AI call
- Real calendar integration for the weekly plan
- Multi-subject support beyond DSA
- Auth / multi-user accounts
