package com.merufureku.aromatica.recommendation_service.dto.responses;

import java.util.List;

public record RecommendationResponse(List<Recommendations> recommendations) {

    public record Recommendations(Long fragranceId, String name, String brand, String description, String imageUrl, Float similarityScore){}

}
