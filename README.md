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
- `MATRIX_ACCESS_TOKEN` — access token for the bot/user
- `MATRIX_ROOM_ID` — room id (e.g. `!abcdef:matrix.org`) or room alias (e.g. `#room:matrix.org`)

Build
```bash
mvn -q -DskipTests package
```

Run
```bash
export MATRIX_HOMESERVER_URL="https://matrix.org"
export MATRIX_ACCESS_TOKEN="YOUR_ACCESS_TOKEN"
export MATRIX_ROOM_ID="!yourRoomId:matrix.org"
java -jar target/matrix-hello-bot-1.0.0.jar "Optional custom message here"
```

Notes
- This is intentionally minimal and uses the homeserver HTTP API directly.
- For a long-running bot that listens for events use `/sync` or a higher-level SDK.
