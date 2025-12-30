package com.example.matrixbot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;

public class MatrixHelloBot {
    public static void main(String[] args) throws Exception {
        String homeserver = System.getenv("MATRIX_HOMESERVER_URL");
        String accessToken = System.getenv("MATRIX_ACCESS_TOKEN");

        if (homeserver == null || accessToken == null) {
            System.err.println("Missing environment variables: MATRIX_HOMESERVER_URL, MATRIX_ACCESS_TOKEN");
            System.exit(2);
        }

        String url = homeserver.endsWith("/") ? homeserver.substring(0, homeserver.length() - 1) : homeserver;
        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        // attempt to discover our own user id to avoid replying to self
        String userId = null;
        try {
            HttpRequest whoami = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/_matrix/client/v3/account/whoami"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> whoamiResp = client.send(whoami, HttpResponse.BodyHandlers.ofString());
            if (whoamiResp.statusCode() == 200) {
                JsonNode whoamiJson = mapper.readTree(whoamiResp.body());
                userId = whoamiJson.path("user_id").asText(null);
                System.out.println("Detected user id: " + userId);
            } else {
                System.out.println("whoami returned: " + whoamiResp.statusCode());
            }
        } catch (Exception e) {
            System.out.println("whoami failed: " + e.getMessage());
        }

        String since = null;
        // Perform an initial short /sync to obtain a since token so we don't re-process
        // historical events that happened before this process started.
        try {
            HttpRequest initSync = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/_matrix/client/v3/sync?timeout=0"))
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .build();
            HttpResponse<String> initResp = client.send(initSync, HttpResponse.BodyHandlers.ofString());
            if (initResp.statusCode() == 200) {
                JsonNode initRoot = mapper.readTree(initResp.body());
                since = initRoot.path("next_batch").asText(null);
                System.out.println("Primed since token: " + since);
            } else {
                System.out.println("Initial sync returned: " + initResp.statusCode());
            }
        } catch (Exception e) {
            System.out.println("Initial sync failed: " + e.getMessage());
        }

        System.out.println("Starting /sync loop (listening for '!testcommand')...");

        while (true) {
            try {
                String syncUrl = url + "/_matrix/client/v3/sync?timeout=30000" + (since != null ? "&since=" + URLEncoder.encode(since, StandardCharsets.UTF_8) : "");
                HttpRequest syncReq = HttpRequest.newBuilder()
                        .uri(URI.create(syncUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();

                HttpResponse<String> syncResp = client.send(syncReq, HttpResponse.BodyHandlers.ofString());
                if (syncResp.statusCode() != 200) {
                    System.out.println("/sync returned: " + syncResp.statusCode() + " - " + syncResp.body());
                    Thread.sleep(2000);
                    continue;
                }

                JsonNode root = mapper.readTree(syncResp.body());
                since = root.path("next_batch").asText(since);

                JsonNode rooms = root.path("rooms").path("join");
                Iterator<String> roomIds = rooms.fieldNames();
                while (roomIds.hasNext()) {
                    String roomId = roomIds.next();
                    JsonNode roomNode = rooms.path(roomId);
                    JsonNode timelineNode = roomNode.path("timeline");
                    String prevBatch = timelineNode.path("prev_batch").asText(null);
                    JsonNode timeline = timelineNode.path("events");
                    if (timeline.isArray()) {
                        for (JsonNode ev : timeline) {
                            if (!"m.room.message".equals(ev.path("type").asText(null))) continue;
                            String body = ev.path("content").path("body").asText(null);
                            String sender = ev.path("sender").asText(null);
                            if (body == null) continue;
                            String trimmed = body.trim();
                            if ("!testcommand".equals(trimmed)) {
                                if (userId != null && userId.equals(sender)) continue;
                                System.out.println("Received !testcommand in " + roomId + " from " + sender);
                                sendText(client, mapper, url, accessToken, roomId, "Hello, world!");
                            } else if (trimmed.matches("!export\\d+h")) {
                                if (userId != null && userId.equals(sender)) continue;
                                int hours = Integer.parseInt(trimmed.replaceAll("\\D+", ""));
                                System.out.println("Received export command in " + roomId + " from " + sender + " (" + hours + "h)");
                                // run export in a new thread so we don't block the sync loop
                                final String fb = prevBatch;
                                new Thread(() -> exportRoomHistory(client, mapper, url, accessToken, roomId, hours, fb)).start();
                            } else if (trimmed.matches("!ollama(\\d+)h")) {
                                if (userId != null && userId.equals(sender)) continue;
                                int hours = Integer.parseInt(trimmed.replaceAll("[^0-9]", ""));
                                System.out.println("Received ollama chat logs command in " + roomId + " from " + sender + " (" + hours + "h)");
                                final String fb = prevBatch;
                                new Thread(() -> queryOllamaWithChatLogs(client, mapper, url, accessToken, roomId, hours, fb)).start();
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.out.println("Error during sync loop: " + e.getMessage());
                try { Thread.sleep(2000); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    private static void sendText(HttpClient client, ObjectMapper mapper, String url, String accessToken, String roomId, String message) {
        try {
            String txnId = "m" + Instant.now().toEpochMilli();
            String encodedRoom = URLEncoder.encode(roomId, StandardCharsets.UTF_8);
            String endpoint = url + "/_matrix/client/v3/rooms/" + encodedRoom + "/send/m.room.message/" + txnId;
            Map<String, String> payload = Map.of("msgtype", "m.text", "body", message);
            String json = mapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Sent reply to " + roomId + " -> " + resp.statusCode());
        } catch (Exception e) {
            System.out.println("Failed to send message: " + e.getMessage());
        }
    }

    private static void exportRoomHistory(HttpClient client, ObjectMapper mapper, String url, String accessToken, String roomId, int hours, String fromToken) {
        try {
            long now = System.currentTimeMillis();
            long cutoff = now - (long) hours * 3600L * 1000L;
            String safeRoom = roomId.replaceAll("[^A-Za-z0-9._-]", "_");
            String filename = safeRoom + "-last" + hours + "h-" + now + ".txt";

            sendText(client, mapper, url, accessToken, roomId, "Starting export of last " + hours + "h to " + filename);

            // If we don't have a pagination token, try to get one via a short sync
            if (fromToken == null) {
                try {
                    HttpRequest syncReq = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/_matrix/client/v3/sync?timeout=0"))
                            .header("Authorization", "Bearer " + accessToken)
                            .GET()
                            .build();
                    HttpResponse<String> syncResp = client.send(syncReq, HttpResponse.BodyHandlers.ofString());
                    if (syncResp.statusCode() == 200) {
                        JsonNode root = mapper.readTree(syncResp.body());
                        fromToken = root.path("rooms").path("join").path(roomId).path("timeline").path("prev_batch").asText(null);
                    }
                } catch (Exception ignore) {
                }
            }

            java.util.List<String> lines = new java.util.ArrayList<>();
            String token = fromToken;
            int totalEvents = 0;
            int safety = 0;

            while (token != null && safety < 100) {
                safety++;
                String messagesUrl = url + "/_matrix/client/v3/rooms/" + URLEncoder.encode(roomId, StandardCharsets.UTF_8)
                        + "/messages?from=" + URLEncoder.encode(token, StandardCharsets.UTF_8) + "&dir=b&limit=100";
                HttpRequest msgReq = HttpRequest.newBuilder()
                        .uri(URI.create(messagesUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> msgResp = client.send(msgReq, HttpResponse.BodyHandlers.ofString());
                if (msgResp.statusCode() != 200) break;
                JsonNode root = mapper.readTree(msgResp.body());
                JsonNode chunk = root.path("chunk");
                if (!chunk.isArray() || chunk.size() == 0) break;
                String next = root.path("end").asText(null);

                for (JsonNode ev : chunk) {
                    if (!"m.room.message".equals(ev.path("type").asText(null))) continue;
                    long ts = ev.path("origin_server_ts").asLong(0);
                    if (ts < cutoff) {
                        // reached older than cutoff — stop collecting
                        token = null;
                        break;
                    }
                    String sender = ev.path("sender").asText("unknown");
                    String body = ev.path("content").path("body").asText("");
                    java.time.Instant instant = java.time.Instant.ofEpochMilli(ts);
                    String timeStr = java.time.ZonedDateTime.ofInstant(instant, java.time.ZoneId.systemDefault()).toString();
                    lines.add("[" + timeStr + "] <" + sender + "> " + body);
                    totalEvents++;
                    if (totalEvents >= 10000) { token = null; break; }
                }

                token = token == null ? null : next;
            }

            // reverse to chronological order (we paginated backwards)
            java.util.Collections.reverse(lines);

            try (java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.FileWriter(filename))) {
                for (String l : lines) w.write(l + "\n");
            }

            sendText(client, mapper, url, accessToken, roomId, "Export complete: " + filename + " (" + totalEvents + " messages)");
            System.out.println("Exported " + totalEvents + " messages to " + filename);
        } catch (Exception e) {
            System.out.println("Export failed: " + e.getMessage());
            try { sendText(client, mapper, url, accessToken, roomId, "Export failed: " + e.getMessage()); } catch (Exception ignore) {}
        }
    }

    private static void queryOllamaWithChatLogs(HttpClient client, ObjectMapper mapper, String url, String accessToken, String roomId, int hours, String fromToken) {
        try {
            sendText(client, mapper, url, accessToken, roomId, "Querying Ollama with chat logs from last " + hours + "h...");

            java.util.List<String> chatLogs = fetchRoomHistory(client, mapper, url, accessToken, roomId, hours, fromToken);
            if (chatLogs.isEmpty()) {
                sendText(client, mapper, url, accessToken, roomId, "No chat logs found for the last " + hours + "h.");
                return;
            }

            String prompt = "Analyze the following chat logs and provide a summary or answer any questions based on the context:\n\n" + String.join("\n", chatLogs);

            // Make HTTP POST request to Ollama API
            String ollamaUrl = System.getenv("OLLAMA_API_URL"); // Expecting Ollama API URL as an environment variable
            if (ollamaUrl == null) {
                sendText(client, mapper, url, accessToken, roomId, "OLLAMA_API_URL environment variable is not set.");
                return;
            }

            Map<String, Object> ollamaPayload = Map.of(
                "model", "phi3", // Assuming 'phi3' model is available in Ollama
                "prompt", prompt,
                "stream", false
            );
            String jsonPayload = mapper.writeValueAsString(ollamaPayload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaUrl + "/api/generate"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode ollamaResponse = mapper.readTree(response.body());
                String ollamaAnswer = ollamaResponse.path("response").asText("No response from Ollama.");
                sendText(client, mapper, url, accessToken, roomId, "Ollama's response: " + ollamaAnswer);
            } else {
                sendText(client, mapper, url, accessToken, roomId, "Failed to get response from Ollama. Status: " + response.statusCode() + ", Body: " + response.body());
            }

        } catch (Exception e) {
            System.out.println("Failed to query Ollama with chat logs: " + e.getMessage());
            sendText(client, mapper, url, accessToken, roomId, "Error querying Ollama: " + e.getMessage());
        }
    }

    private static java.util.List<String> fetchRoomHistory(HttpClient client, ObjectMapper mapper, String url, String accessToken, String roomId, int hours, String fromToken) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        long now = System.currentTimeMillis();
        long cutoff = now - (long) hours * 3600L * 1000L;

        // If we don't have a pagination token, try to get one via a short sync
        if (fromToken == null) {
            try {
                HttpRequest syncReq = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/_matrix/client/v3/sync?timeout=0"))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> syncResp = client.send(syncReq, HttpResponse.BodyHandlers.ofString());
                if (syncResp.statusCode() == 200) {
                    JsonNode root = mapper.readTree(syncResp.body());
                    JsonNode roomNode = root.path("rooms").path("join").path(roomId);
                    if (!roomNode.isMissingNode()) {
                         fromToken = roomNode.path("timeline").path("prev_batch").asText(null);
                    }
                }
            } catch (Exception ignore) {
                // ignore errors here, we'll just start fetching from the latest available if sync fails
            }
        }

        String token = fromToken;
        int safety = 0;

        while (token != null && safety < 100) {
            safety++;
            try {
                String messagesUrl = url + "/_matrix/client/v3/rooms/" + URLEncoder.encode(roomId, StandardCharsets.UTF_8)
                        + "/messages?from=" + URLEncoder.encode(token, StandardCharsets.UTF_8) + "&dir=b&limit=100";
                HttpRequest msgReq = HttpRequest.newBuilder()
                        .uri(URI.create(messagesUrl))
                        .header("Authorization", "Bearer " + accessToken)
                        .GET()
                        .build();
                HttpResponse<String> msgResp = client.send(msgReq, HttpResponse.BodyHandlers.ofString());
                if (msgResp.statusCode() != 200) {
                    System.out.println("Failed to fetch messages: " + msgResp.statusCode() + " - " + msgResp.body());
                    break;
                }
                JsonNode root = mapper.readTree(msgResp.body());
                JsonNode chunk = root.path("chunk");
                if (!chunk.isArray() || chunk.size() == 0) break;

                for (JsonNode ev : chunk) {
                    if (!"m.room.message".equals(ev.path("type").asText(null))) continue;
                    long originServerTs = ev.path("origin_server_ts").asLong(0);
                    if (originServerTs < cutoff) {
                        token = null; // Reached messages older than cutoff
                        break;
                    }
                    String body = ev.path("content").path("body").asText(null);
                    String sender = ev.path("sender").asText(null);
                    if (body != null && sender != null) {
                        lines.add("[" + Instant.ofEpochMilli(originServerTs) + "] <" + sender + "> " + body);
                    }
                }
                if (token != null) {
                     token = root.path("end").asText(null);
                }

            } catch (Exception e) {
                System.out.println("Error fetching room history: " + e.getMessage());
                break;
            }
        }
        java.util.Collections.reverse(lines);
        return lines;
    }
}
