# Recommendation Service

The brain of the platform - generates personalized fragrance recommendations using two algorithms: Content-Based Filtering (CBF) and Collaborative Filtering (CF).

## Tech Stack

- Spring Boot 3.x
- Spring Security
- PostgreSQL
- Resilience4j (circuit breakers)
- Gradle
- Docker

## Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/recommendations/cbf?limit=10` | Content-based recommendations |
| GET | `/recommendations/cf?limit=10` | Collaborative filtering recommendations |

Both require authentication. Limit is 1-10.

## The Algorithms

### Content-Based Filtering (CBF)

Recommends fragrances with similar notes to what you already have in your collection.

How it works:
1. Get all fragrances in user's collection
2. Extract all notes and build a "preference vector"
3. Notes are weighted by position:
   - Base notes: 2.0x (these define the fragrance character)
   - Middle notes: 1.5x
   - Top notes: 1.0x
4. Compare against all other fragrances
5. Return top matches not already in collection

### Collaborative Filtering (CF)

Recommends fragrances that similar users liked.

How it works:
1. Get user's collection + reviews (rating >= 4.0)
2. Find other users who collected the same fragrances
3. Calculate similarity scores based on overlap
4. Get highly-rated fragrances from similar users
5. Weight by: similarity score × rating

Minimum overlap: 2 shared fragrances between users

Rating weights for scoring:
- 4.8+ rating = 1.5x
- 4.5+ rating = 1.25x
- 4.0+ rating = 1.0x
- Collection only (no review) = 0.75x

## Response Format

```json
{
  "status": 200,
  "message": "Get CBF Recommended Perfume Success",
  "data": {
    "recommendations": [
      {
        "fragranceId": 49,
        "name": "Baccarat Rouge 540",
        "brand": "Maison Francis Kurkdjian",
        "description": "A luminous and sophisticated fragrance...",
        "imageUrl": "https://...",
        "score": 0.875
      }
    ]
  }
}
```

## Service Communication

This service doesn't have its own fragrance/user data - it calls the other services:

```
Recommendation Service
        │
        ├── Collection Service (get user collections)
        ├── Fragrance Service (get fragrance notes)
        └── Review Service (get user ratings)
```

Each call uses a separate internal JWT token.

## Circuit Breakers

Using Resilience4j to handle failures gracefully. If a downstream service is down, the circuit opens and returns 503 immediately instead of waiting and blocking threads.

## Environment Variables

```
DB_URL=jdbc:postgresql://localhost:5432/your_db
DB_USERNAME=your_username
DB_PASSWORD=your_password
ACCESS_SECRET=base64_encoded_key

# Internal service auth
INTERNAL_FRAGRANCE_SECRET=key1
INTERNAL_COLLECTION_SECRET=key2
INTERNAL_REVIEW_SECRET=key3

# Service URLs
COLLECTION_SERVICE_URL=http://localhost:8083/api/collection-service
FRAGRANCE_SERVICE_URL=http://localhost:8082/api/fragrance-service
REVIEWS_SERVICE_URL=http://localhost:8084/api/review-service
```

## Running Locally

Make sure the other services are running first, then:

```bash
./gradlew bootRun
```

Swagger UI: `http://localhost:8085/api/recommendation-service/swagger-ui`

## Docker

```bash
docker build -t recommendation-service .
docker run -p 8085:8085 --env-file .env recommendation-service
```

## Project Structure

```
src/main/java/.../recommendation_service/
├── config/          # Security, RestTemplate, URLs
├── controller/      # Recommendation endpoints
├── dto/             # Request/response objects
├── helper/          # Algorithm logic, similarity calculations
├── services/
│   ├── impl/
│   │   ├── RecommendationServiceImpl1.java  # Main logic
│   │   ├── CollectionsService.java          # HTTP client
│   │   ├── FragrancesService.java           # HTTP client
│   │   ├── ReviewsService.java              # HTTP client
│   │   └── Async*Client.java                # Async wrappers
│   └── interfaces/
└── utilities/       # Token generation for internal calls
```

## Performance Notes

- Service calls are made in parallel using CompletableFuture
- Circuit breakers prevent cascade failures
- Configurable limit (max 10) prevents over-fetching

## Requirements for Good Recommendations

- **CBF**: User needs at least 1 fragrance in collection
- **CF**: Need at least 2 shared fragrances with other users to find similarity

## Related Services

- [Auth Service](https://github.com/merufureku/aromatica-auth-service) - Authentication
- [Fragrance Service](https://github.com/merufureku/aromatica-fragrance-service) - Perfume catalog
- [Collection Service](https://github.com/merufureku/aromatica-collection-service) - User collections
- [Review Service](https://github.com/merufureku/aromatica-review-service) - Gets review/rating data