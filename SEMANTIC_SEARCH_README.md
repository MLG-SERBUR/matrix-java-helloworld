# Semantic Search Feature

## Overview
The Matrix bot now includes an AI-free semantic search capability that uses local text similarity to find relevant messages in chat history.

## Files Added/Modified

### New Files:
- `src/main/java/com/example/matrixbot/SemanticSearchEngine.java` - Core semantic search engine

### Modified Files:
- `src/main/java/com/example/matrixbot/MatrixHelloBot.java` - Added command handler and integration

## Command Usage

### Syntax:
```
!semantic <timezone> <duration>h <query>
```

### Parameters:
- **timezone**: PST, PDT, MST, MDT, CST, CDT, EST, EDT, UTC, GMT
- **duration**: Number of hours to search back in time
- **query**: Search terms to find relevant messages

### Examples:
```
!semantic PST 24h machine learning
!semantic EST 48h python data analysis
!semantic UTC 72h neural networks
```

## How It Works

1. **Fetches chat history** from the specified room for the given time period
2. **Analyzes text similarity** using Jaccard similarity (word overlap)
3. **Ranks results** by relevance score (0.0-1.0)
4. **Returns formatted results** with:
   - Timestamp
   - Sender
   - Message content
   - Similarity score
   - Clickable message link

## Features

### Local Implementation
- Uses local text similarity algorithms
- No external AI services required
- Completely private and offline

### Similarity Scoring
- Jaccard similarity coefficient
- Score range: 0.0 (no match) to 1.0 (perfect match)
- Threshold filter: only returns results with score > 0.1

### Result Format
```
Semantic Search Results

Query: "machine learning"
Time range: last 24 hours

[2024-12-30 10:20 PST] <@user:matrix.org> (score: 0.33)
Machine learning models require training data
https://matrix.to/#/!room:domain.org/event_123

[2024-12-30 10:30 PST] <@user:matrix.org> (score: 0.33)
Neural networks are a type of machine learning
https://matrix.to/#/!room:domain.org/event_456
```

## Technical Details

### SemanticSearchEngine Class
- `calculateTextSimilarity(query, message)` - Calculates Jaccard similarity
- `search(query, embeddings, topK)` - Finds top K most relevant messages
- `MessageEmbedding` - Data structure for message storage

### Algorithm
1. Tokenize query and message into words
2. Filter out short words (< 3 characters)
3. Calculate word overlap (intersection/union)
4. Return similarity score

### Performance
- O(n) complexity for searching n messages
- Memory efficient (no large embedding vectors)
- Fast execution (no network calls)

## Help Command
The `!help` command now includes semantic search documentation:

```
**!semantic <timezone> <duration>h <query>** - Semantic search using local embeddings
  - Timezone: PST, PDT, MST, MDT, CST, CDT, EST, EDT, UTC, GMT
  - Duration: Number of hours to search
  - Query: Search terms to find relevant messages
  - Returns: Top 5 most relevant messages with similarity scores
```

## Requirements
- No additional dependencies required
- Uses existing Jackson library for JSON parsing
- Compatible with existing bot configuration

## Configuration
No additional configuration needed. The semantic search works with your existing `config.json` file.

## Limitations
- Uses simulated event IDs (pseudo IDs based on message content)
- Simple word-based similarity (no semantic understanding)
- Limited to top 5 results per search
- No persistent embedding storage (searches are performed on-demand)

## Future Enhancements
- Persistent embedding storage for faster searches
- TF-IDF weighting for better relevance
- Phrase matching and proximity scoring
- Synonym expansion
- Stemming/lemmatization