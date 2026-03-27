package com.merufureku.aromatica.recommendation_service.services.impl;

import com.merufureku.aromatica.recommendation_service.dto.params.BaseParam;
import com.merufureku.aromatica.recommendation_service.dto.params.GetFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.responses.*;
import com.merufureku.aromatica.recommendation_service.exceptions.ServiceException;
import com.merufureku.aromatica.recommendation_service.helper.AsyncExceptionMapper;
import com.merufureku.aromatica.recommendation_service.helper.RecommendationHelper;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncCollectionClient;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncFragranceClient;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncReviewsClient;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncVectorBuilder;
import com.merufureku.aromatica.recommendation_service.services.interfaces.ICollectionService;
import com.merufureku.aromatica.recommendation_service.services.interfaces.IFragranceService;
import com.merufureku.aromatica.recommendation_service.services.interfaces.IRecommendationService;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.merufureku.aromatica.recommendation_service.constants.RecommendationCollectionConstants.MINIMUM_RATING;
import static com.merufureku.aromatica.recommendation_service.enums.CustomStatusEnums.NO_FRAGRANCE_TO_RECOMMEND;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl1 implements IRecommendationService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final ICollectionService collectionsService;
    private final IFragranceService fragranceService;
    private final RecommendationHelper recommendationHelper;
    private final AsyncFragranceClient asyncFragranceClient;
    private final AsyncReviewsClient asyncReviewsClient;
    private final AsyncCollectionClient asyncCollectionClient;
    private final AsyncVectorBuilder asyncVectorBuilder;

    @Override
    public BaseResponse<RecommendationResponse> getCBFRecommendations(Integer userId, int limit, BaseParam baseParam) {
        logger.info("Fetching CBF recommendations for user ID: {}", userId);

        var userCollections = collectionsService.getUserCollections(userId, 1, baseParam.correlationId()).data();
        if (userCollections.fragrances().isEmpty()){
            throw new ServiceException(NO_FRAGRANCE_TO_RECOMMEND);
        }

        var userCollectionIds = userCollections.fragrances()
                .stream().map(UserCollectionsResponse.FragranceDetails::fragranceId)
                .collect(Collectors.toSet());

        var userNotesFuture = asyncFragranceClient.getUserCollectionNotes(userCollectionIds, baseParam.correlationId());
        var allNotesFuture = asyncFragranceClient.getAllFragranceNotes(userCollectionIds, baseParam.correlationId());

        completeFuture(userNotesFuture, allNotesFuture);

        var userCollectionNotes = userNotesFuture.join();
        var allFragranceNotes = allNotesFuture.join();

        var userFragranceVectorsFuture = asyncVectorBuilder.buildUserVector(userCollectionNotes.fragranceNoteLists());
        var allFragranceVectorsFuture = asyncVectorBuilder.buildAllPerfumeVectors(allFragranceNotes);

        completeFuture(userFragranceVectorsFuture, allFragranceVectorsFuture);

        var userFragranceVector = userFragranceVectorsFuture.join();
        var allFragranceVector = allFragranceVectorsFuture.join();

        var cbfResult = recommendationHelper.calculateCBFRecommendations(userFragranceVector, allFragranceVector, limit);

        var recommendedFragrance = getRecommendedFragrance(cbfResult.keySet(), baseParam);

        var response = recommendationHelper.createCbfResponse(recommendedFragrance, cbfResult);

        logger.info("CBF recommendations for user ID: {} fetched successfully", userId);
        return new BaseResponse<>(HttpStatus.OK.value(),
                "Get CBF Recommended Perfume Success", new RecommendationResponse(response));
    }

    @Override
    public BaseResponse<RecommendationResponse> getCFRecommendations(Integer userId, int limit, BaseParam baseParam) {

        logger.info("Fetching CF recommendations for user ID: {}", userId);

        var userReviewsFuture = asyncReviewsClient.getUserReviews(userId, MINIMUM_RATING,1, baseParam.correlationId());
        var userCollectionsFuture = asyncCollectionClient.getUserCollection(userId, 1, baseParam.correlationId());
        completeFuture(userReviewsFuture, userCollectionsFuture);

        var userReviews = userReviewsFuture.join();
        var userCollections = userCollectionsFuture.join();

        var targetUserInteractions = recommendationHelper.targetUserInteraction(userCollections, userReviews);
        var targetUserAllPerfumes = targetUserInteractions.values().iterator().next().keySet();

        var allUserReviewsFuture = asyncReviewsClient.getReviews(
                userId, new GetFragranceBatchParam(targetUserAllPerfumes),
                MINIMUM_RATING, 1, baseParam.correlationId());

        var allUsersCollectionFuture = asyncCollectionClient.getAllCollectionsFromSimilarFragrance(
                userId, new GetFragranceBatchParam(targetUserAllPerfumes),
                1, baseParam.correlationId());
        completeFuture(allUserReviewsFuture, allUsersCollectionFuture);

        var allReviews = allUserReviewsFuture.join();
        var allCollections = allUsersCollectionFuture.join();

        var allUserInteractions = recommendationHelper.allUserInteraction(allCollections, allReviews);

        var similarityResult = recommendationHelper.getSimilarityScore(userId, targetUserInteractions, allUserInteractions);
        var topCandidateUsers = similarityResult.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        var topCandidatePerfumes = recommendationHelper.getTopCandidatePerfumes(topCandidateUsers, targetUserAllPerfumes, allUserInteractions, similarityResult, limit);
        var recommendedFragrance = getRecommendedFragrance(topCandidatePerfumes.keySet(), baseParam);

        var response = recommendationHelper.createCfResponse(recommendedFragrance, topCandidatePerfumes);

        return new BaseResponse<>(HttpStatus.OK.value(),
                "Get CF Recommended Perfume Success", new RecommendationResponse(response));
    }

    private Map<Long, FragranceDetailedListResponse.FragranceDetailedResponse> getRecommendedFragrance(Set<Long> fragranceIds, BaseParam baseParam) {
        var recommendedFragranceMetadata = fragranceService.getPerfumes(new GetFragranceBatchParam(fragranceIds), 1, baseParam.correlationId()).data();

        return recommendedFragranceMetadata.fragrances().stream()
                .collect(Collectors.toMap(
                        FragranceDetailedListResponse.FragranceDetailedResponse::fragranceId,
                        Function.identity()
                ));
    }

    private void completeFuture(CompletableFuture<?>... futures) {
        try{
            CompletableFuture.allOf(futures).join();
        } catch (Exception e){
            logger.error("Error completing futures", e);
            throw AsyncExceptionMapper.map(e);
        }
    }
}
