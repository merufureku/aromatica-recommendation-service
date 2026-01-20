package com.merufureku.aromatica.recommendation_service.helper;

import com.merufureku.aromatica.recommendation_service.dto.responses.*;
import com.merufureku.aromatica.recommendation_service.exceptions.ServiceException;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static com.merufureku.aromatica.recommendation_service.constants.RecommendationCollectionConstants.*;
import static com.merufureku.aromatica.recommendation_service.enums.CustomStatusEnums.NO_FRAGRANCE_TO_RECOMMEND;
import static com.merufureku.aromatica.recommendation_service.enums.CustomStatusEnums.NO_USER_COLLECTION;

@Service
public class RecommendationHelper {

    public Map<Long, Float> getCollectionVector(List<FragranceNoteListResponse.FragranceNoteList> fragranceNoteLists) {

        if (fragranceNoteLists.isEmpty()) {
            throw new ServiceException(NO_USER_COLLECTION);
        }

        Map<Long, Float> noteVector = new HashMap<>();

        for (FragranceNoteListResponse.FragranceNoteList noteList: fragranceNoteLists) {
            for (NoteResponse note: noteList.noteResponseList()) {
                float noteWeight = getNoteWeight(note);
                noteVector.merge(note.id(), noteWeight, Float::sum);
            }
        }

        return noteVector;
    }

    public Map<Long, Map<Long, Float>> getAllPerfumesVector(FragranceNoteListResponse allPerfumes){

        if (allPerfumes.fragranceNoteLists().isEmpty()) {
            throw new ServiceException(NO_FRAGRANCE_TO_RECOMMEND);
        }

        Map<Long, Map<Long, Float>> allPerfumeVector = new HashMap<>();

        for (FragranceNoteListResponse.FragranceNoteList noteList: allPerfumes.fragranceNoteLists()) {

            Map<Long, Float> noteVector = new HashMap<>();

            for (NoteResponse note: noteList.noteResponseList()) {
                float noteWeight = getNoteWeight(note);
                noteVector.merge(note.id(), noteWeight, Float::sum);
            }

            allPerfumeVector.put(noteList.fragranceId(), noteVector);
        }

        return allPerfumeVector;
    }

    public Map<Long, Float> calculateCBFRecommendations(Map<Long, Float> userCollectionVector,
                                                        Map<Long, Map<Long, Float>> allPerfumesVector,
                                                        int limit) {

        Map<Long, Float> similarityScores = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Float>> perfumeEntry : allPerfumesVector.entrySet()) {
            Long perfumeId = perfumeEntry.getKey();
            float similarityScore = getSimilarityScore(userCollectionVector, perfumeEntry);

            similarityScores.put(perfumeId, similarityScore);
        }

        return similarityScores.entrySet().stream()
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .collect(LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll
                );
    }

    private float getSimilarityScore(Map<Long, Float> userCollectionVector,
                                            Map.Entry<Long, Map<Long, Float>> perfumeEntry) {

        Map<Long, Float> perfumeVector = perfumeEntry.getValue();

        float similarityScore = 0.0f;

        for (Map.Entry<Long, Float> noteEntry : userCollectionVector.entrySet()) {
            Long collectionNoteId = noteEntry.getKey();
            Float collectionNoteWeight = noteEntry.getValue();

            Float perfumeNoteWeight = perfumeVector.getOrDefault(collectionNoteId, 0.0f);
            similarityScore += collectionNoteWeight * perfumeNoteWeight;
        }
        return similarityScore;
    }

    public List<RecommendationResponse.Recommendations> createCbfResponse(Map<Long, FragranceDetailedListResponse.FragranceDetailedResponse> fragranceMap,
                                                                          Map<Long, Float> cbfResult) {
        return cbfResult.entrySet().stream()
                .map(entry -> {

                    Long id = entry.getKey();
                    Float score = entry.getValue();

                    var fragrance = fragranceMap.get(id);

                    return new RecommendationResponse.Recommendations(
                            fragrance.fragranceId(),
                            fragrance.name(),
                            fragrance.brand(),
                            fragrance.description(),
                            fragrance.imageUrl(),
                            score
                    );
                })
                .toList();
    }

    public List<RecommendationResponse.Recommendations> createCfResponse(
            Map<Long, FragranceDetailedListResponse.FragranceDetailedResponse> fragranceMap,
            Map<Long, Float> cfResult) {

        List<RecommendationResponse.Recommendations> recommendations = fragranceMap.entrySet().stream()
                .map(entry -> {
                    Long id = entry.getKey();
                    var fragrance = entry.getValue();
                    Float score = cfResult.getOrDefault(id, 0.0f);

                    return new RecommendationResponse.Recommendations(
                            fragrance.fragranceId(),
                            fragrance.name(),
                            fragrance.brand(),
                            fragrance.description(),
                            fragrance.imageUrl(),
                            score
                    );
                })
                .toList();

        return recommendations.stream()
                .sorted(Comparator.comparing(RecommendationResponse.Recommendations::similarityScore).reversed())
                .toList();
    }


    public Map<Integer, Map<Long, Float>> targetUserInteraction(UserCollectionsResponse userCollectionsResponse,
                                                                GetAllReviews userReviews){

        Map<Long, Float> interactions = new HashMap<>();

        for (UserCollectionsResponse.FragranceDetails fragranceDetails : userCollectionsResponse.fragrances()) {
            interactions.merge(fragranceDetails.fragranceId(), 1.0F, Float::sum);
        }

        for (GetAllReviews.Interactions review : userReviews.interactions()) {

            long fragranceId = review.fragranceId();
            float interactionWeight = getInteractionWeight(review.rating());

            interactions.merge(fragranceId, interactionWeight, Float::sum);
        }

        Map<Integer, Map<Long, Float>> userInteractions = new HashMap<>();
        userInteractions.put(userCollectionsResponse.userId(), interactions);

        return userInteractions;
    }

    public Map<Integer, Map<Long, Float>> allUserInteraction(CollectionsResponse collectedFragrances,
                                                             GetAllReviews userReviews) {

        Map<Integer, Map<Long, Float>> userInteractions = new HashMap<>();

        for (CollectionsResponse.FragranceDetails collection : collectedFragrances.fragrances()) {
            Integer userId = collection.userId();
            Long fragranceId = collection.fragranceId();

            userInteractions.computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(fragranceId, 1.0F, Float::sum);
        }

        for (GetAllReviews.Interactions review : userReviews.interactions()) {

            Integer userId = review.userId();
            Long fragranceId = review.fragranceId();
            float weight = getInteractionWeight(review.rating());

            userInteractions
                    .computeIfAbsent(userId, k -> new HashMap<>())
                    .merge(fragranceId, weight, Float::sum);
        }

        return userInteractions;
    }

    public Map<Integer, Float> getSimilarityScore(Integer targetUserId,
                                                  Map<Integer, Map<Long, Float>> targetUserInteraction,
                                                  Map<Integer, Map<Long, Float>> allUserInteractions) {

        Map<Integer, Float> similarityResult = new HashMap<>();

        Map<Long, Float> targetInteractions =
                targetUserInteraction.values().iterator().next();

        // Precompute target magnitude for normalization
        float targetMagnitude = 0.0f;
        for (float w : targetInteractions.values()) {
            targetMagnitude += w * w;
        }
        targetMagnitude = (float) Math.sqrt(targetMagnitude);

        for (Map.Entry<Integer, Map<Long, Float>> entry : allUserInteractions.entrySet()) {

            Integer otherUserId = entry.getKey();
            if (otherUserId.equals(targetUserId)) continue;

            Map<Long, Float> otherInteractions = entry.getValue();

            float overlapScore = 0.0f;
            int overlapCount = 0;

            for (Map.Entry<Long, Float> other : otherInteractions.entrySet()) {
                Float targetWeight = targetInteractions.get(other.getKey());
                if (targetWeight != null) {
                    overlapScore += Math.min(targetWeight, other.getValue());
                    overlapCount++;
                }
            }

            // Noise filtering
            if (overlapCount < 2 || overlapScore <= 0) continue;

            // Normalize
            float normalizedScore = overlapScore / targetMagnitude;

            similarityResult.put(otherUserId, normalizedScore);
        }

        return similarityResult;
    }


    public Map<Long, Float> getTopCandidatePerfumes(
            List<Integer> topSimilarUsers,
            Set<Long> excludedPerfumes,
            Map<Integer, Map<Long, Float>> allUserInteractions,
            Map<Integer, Float> similarityResult,
            int limit){

        Map<Long, Float> perfumesScore = new HashMap<>();

        for (Integer userId : topSimilarUsers) {
            Map<Long, Float> userInteractions = allUserInteractions.get(userId);
            if (userInteractions != null) {

                for (Map.Entry<Long, Float> entry : userInteractions.entrySet()) {
                    Long perfumeId = entry.getKey();
                    Float score = entry.getValue();

                    if (excludedPerfumes.contains(perfumeId)) continue;

                    Float similarity = similarityResult.get(userId);
                    if (similarity == null || similarity <= 0f) continue;

                    perfumesScore.merge(perfumeId, score * similarity, Float::sum);
                }
            }
        }

        return perfumesScore.entrySet().stream()
                .sorted(Map.Entry.<Long, Float>comparingByValue().reversed())
                .limit(limit)
                .collect(
                        LinkedHashMap::new,
                        (m, e) -> m.put(e.getKey(), e.getValue()),
                        LinkedHashMap::putAll
                );

    }

    public Set<Long> getHighestRecommendedPerfumes(Map<Long, Float> allUsersInteractions, Set<Long> excludedPerfumes, int limit) {

        return allUsersInteractions.entrySet()
                .stream()
                .filter(entry -> !excludedPerfumes.contains(entry.getKey()))
                .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                .limit(limit)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static float getInteractionWeight(int rating) {
        if (rating >= 4.8) {
            return EXCELLENT;
        } else if (rating >= 4.5) {
            return GREAT;
        } else {
            return AVERAGE;
        }
    }

    private static float getNoteWeight(NoteResponse note) {
        return switch (note.type().toLowerCase()) {
            case "top" -> TOP_NOTE_SCORE;
            case "middle" -> MIDDLE_NOTE_SCORE;
            case "base" -> BASE_NOTE_SCORE;
            default -> 0.0f;
        };
    }

}
