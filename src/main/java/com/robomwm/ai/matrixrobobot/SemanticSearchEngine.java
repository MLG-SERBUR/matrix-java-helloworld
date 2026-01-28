package com.robomwm.ai.matrixrobobot;

import java.util.*;

/**
 * AI-free semantic search engine using local text similarity
 * Uses Jaccard similarity and word overlap for ranking
 */
public class SemanticSearchEngine {
    
    public static class MessageEmbedding {
        public String eventId;
        public String message;
        public String timestamp;
        public String sender;
        public double[] embedding;
        
        public MessageEmbedding(String eventId, String message, String timestamp, String sender, double[] embedding) {
            this.eventId = eventId;
            this.message = message;
            this.timestamp = timestamp;
            this.sender = sender;
            this.embedding = embedding;
        }
    }
    
    /**
     * Calculate Jaccard similarity between query and message
     * Returns score between 0.0 and 1.0
     */
    public static double calculateTextSimilarity(String query, String message) {
        String[] queryWords = query.toLowerCase().split("\\W+");
        String[] messageWords = message.toLowerCase().split("\\W+");
        
        Set<String> querySet = new HashSet<>();
        for (String word : queryWords) {
            if (word.length() > 2) querySet.add(word);
        }
        
        Set<String> messageSet = new HashSet<>();
        for (String word : messageWords) {
            if (word.length() > 2) messageSet.add(word);
        }
        
        if (querySet.isEmpty() || messageSet.isEmpty()) return 0.0;
        
        // Jaccard similarity: intersection / union
        Set<String> intersection = new HashSet<>(querySet);
        intersection.retainAll(messageSet);
        
        Set<String> union = new HashSet<>(querySet);
        union.addAll(messageSet);
        
        return (double) intersection.size() / union.size();
    }
    
    /**
     * Search for similar messages in the collection
     * @param query Search query
     * @param embeddings List of message embeddings to search through
     * @param topK Number of top results to return
     * @return List of matching messages sorted by relevance
     */
    public static List<MessageEmbedding> search(String query, List<MessageEmbedding> embeddings, int topK) {
        if (embeddings.isEmpty()) return new ArrayList<>();
        
        List<MessageEmbedding> results = new ArrayList<>();
        
        // Calculate similarity for each message
        for (MessageEmbedding embedding : embeddings) {
            double similarity = calculateTextSimilarity(query, embedding.message);
            if (similarity > 0.1) { // Threshold filter
                // Store similarity in the embedding array for sorting
                results.add(new MessageEmbedding(
                    embedding.eventId, 
                    embedding.message, 
                    embedding.timestamp, 
                    embedding.sender, 
                    new double[]{similarity}
                ));
            }
        }
        
        // Sort by similarity (descending)
        results.sort((a, b) -> Double.compare(b.embedding[0], a.embedding[0]));
        
        // Return top K results
        return results.subList(0, Math.min(topK, results.size()));
    }
    
    /**
     * Get a simple similarity score for debugging/analysis
     */
    public static String getSimilarityReport(String query, String message) {
        double score = calculateTextSimilarity(query, message);
        return String.format("Query: '%s' | Message: '%s' | Score: %.3f", query, message, score);
    }
}