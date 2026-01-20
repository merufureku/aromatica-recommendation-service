package com.merufureku.aromatica.recommendation_service.services;

import com.merufureku.aromatica.recommendation_service.dto.params.BaseParam;
import com.merufureku.aromatica.recommendation_service.dto.params.GetFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.responses.*;
import com.merufureku.aromatica.recommendation_service.enums.CustomStatusEnums;
import com.merufureku.aromatica.recommendation_service.exceptions.ServiceException;
import com.merufureku.aromatica.recommendation_service.helper.RecommendationHelper;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncFragranceClient;
import com.merufureku.aromatica.recommendation_service.services.async.AsyncVectorBuilder;
import com.merufureku.aromatica.recommendation_service.services.impl.RecommendationServiceImpl1;
import com.merufureku.aromatica.recommendation_service.services.interfaces.ICollectionService;
import com.merufureku.aromatica.recommendation_service.services.interfaces.IFragranceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RecommendationServiceImpl1Test {

    @InjectMocks
    private RecommendationServiceImpl1 recommendationService;

    @Mock
    private ICollectionService collectionsService;

    @Mock
    private IFragranceService fragranceService;

    @Mock
    private RecommendationHelper recommendationHelper;

    @Mock
    private AsyncFragranceClient asyncFragranceClient;

    @Mock
    private AsyncVectorBuilder asyncVectorBuilder;

    private static final Integer USER_ID = 1;
    private static final int LIMIT = 5;
    private static final BaseParam BASE_PARAM = new BaseParam(USER_ID, "corr-1");

    @Test
    void getCBFRecommendations_whenUserHasCollections_shouldReturnRecommendations() {
        // user collections
        var fragranceDetails = Collections.singletonList(
                new UserCollectionsResponse.FragranceDetails(1L, "name", "brand")
        );
        var collectionsResponse = new UserCollectionsResponse(USER_ID, fragranceDetails);
        var collectionsBaseResponse = new BaseResponse<>(HttpStatus.OK.value(), "success", collectionsResponse);

        when(collectionsService.getUserCollections(eq(USER_ID), anyInt(), anyString())).thenReturn(collectionsBaseResponse);

        // async note fetching
        var userNotes = new FragranceNoteListResponse(Collections.emptyList());
        var allNotes = new FragranceNoteListResponse(Collections.emptyList());
        when(asyncFragranceClient.getUserCollectionNotes(anySet(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(userNotes));
        when(asyncFragranceClient.getAllFragranceNotes(anySet(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(allNotes));

        // async vector building
        Map<Long, Float> userVector = new HashMap<>();
        Map<Long, Map<Long, Float>> allVectors = new HashMap<>();
        when(asyncVectorBuilder.buildUserVector(anyList()))
                .thenReturn(CompletableFuture.completedFuture(userVector));
        when(asyncVectorBuilder.buildAllPerfumeVectors(any(FragranceNoteListResponse.class)))
            .thenReturn(CompletableFuture.completedFuture(allVectors));

        // CBF calculation
        Map<Long, Float> cbfResult = new LinkedHashMap<>();
        cbfResult.put(1L, 0.9f);
        when(recommendationHelper.calculateCBFRecommendations(eq(userVector), eq(allVectors), eq(LIMIT)))
                .thenReturn(cbfResult);

        // fragrance metadata
        var detailedResponses = Collections.singletonList(
                new FragranceDetailedListResponse.FragranceDetailedResponse(1L, "name", "brand", "desc", "imageUrl", Collections.emptyList())
        );
        var detailedListResponse = new FragranceDetailedListResponse(detailedResponses);
        var fragranceBaseResponse = new BaseResponse<>(HttpStatus.OK.value(), "success", detailedListResponse);
        when(fragranceService.getPerfumes(any(GetFragranceBatchParam.class), anyInt(), anyString()))
                .thenReturn(fragranceBaseResponse);

        // final CBF response creation
        List<RecommendationResponse.Recommendations> cbfData = Collections.singletonList(
                new RecommendationResponse.Recommendations(1L, "name", "brand", "desc", "imageUrl",0.9f)
        );
        when(recommendationHelper.createCbfResponse(anyMap(), anyMap())).thenReturn(cbfData);

        BaseResponse<RecommendationResponse> response = recommendationService.getCBFRecommendations(USER_ID, LIMIT, BASE_PARAM);

        assertNotNull(response);
        assertEquals(HttpStatus.OK.value(), response.status());
        assertNotNull(response.data());
        assertEquals(1, response.data().recommendations().size());
    }
}
