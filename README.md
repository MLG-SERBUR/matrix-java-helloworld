# Matrix Hello Bot (Java)

Simple minimal Matrix bot that sends a "Hello, world!" message to a room using the Matrix Client-Server HTTP API.

Prerequisites
- Java 17+
- Maven

 This is intentionally minimal and uses the homeserver HTTP API directly.
 For a long-running bot that listens for events use `/sync` or a higher-level SDK.

 Commands
 - `!testcommand` — bot replies `Hello, world!` in the same room.
 - `!export<N>h` — export the last N hours of chat from the room where the command was sent.
	 - Example: `!export12h` will write a file like `!room_alias...-last12h-<ts>.txt` in the current working directory.
	 - The bot will announce in the room when the export starts and when it completes.
 - `!arliai` or `!arliai <N>h` or `!arliai <N>h <question>` — queries Arli AI with the last N hours of chat logs (or all available history if N is not specified) and an optional question, then returns the summary or answer.
	 - Example: `!arliai`, `!arliai 12h`, or `!arliai 6h What was the main topic of discussion?` will send the chat logs to Arli AI for summarization/answering using the `Chat Gemma-3-27B-it` model.
	 - The Arli AI API URL is hardcoded as `https://api.arliai.com`. If an API key is required, it needs to be added to the `HttpRequest` as an `Authorization` header in the `queryArliAIWithChatLogs` method in `src/main/java/com/example/matrixbot/MatrixHelloBot.java`.

Prerequisites
- Java 17+
- Maven

Environment Variables
- `MATRIX_HOMESERVER_URL` — the URL of your Matrix homeserver (e.g., `https://matrix.org`)
- `MATRIX_ACCESS_TOKEN` — access token for the bot/user
- `ARLI_API_KEY` — API key for Arli AI (if required by your Arli AI plan)

Build
```bash
mvn -q -DskipTests package
```

Run
```bash
export MATRIX_HOMESERVER_URL="https://matrix.org"
export MATRIX_ACCESS_TOKEN="YOUR_ACCESS_TOKEN"
export ARLI_API_KEY="YOUR_ARLI_API_KEY" # If required
mvn exec:java -Dexec.mainClass="com.example.matrixbot.MatrixHelloBot" -Dexec.classpathScope=runtime
```

Notes
- This is intentionally minimal and uses the homeserver HTTP API directly.
- For a long-running bot that listens for events use `/sync` or a higher-level SDK.
