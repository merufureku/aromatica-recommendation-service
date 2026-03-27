package com.merufureku.aromatica.recommendation_service.services.impl;

import com.merufureku.aromatica.recommendation_service.config.UrlConfig;
import com.merufureku.aromatica.recommendation_service.dto.params.GetFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.responses.BaseResponse;
import com.merufureku.aromatica.recommendation_service.dto.responses.CollectionsResponse;
import com.merufureku.aromatica.recommendation_service.dto.responses.UserCollectionsResponse;
import com.merufureku.aromatica.recommendation_service.helper.RestExceptionHelper;
import com.merufureku.aromatica.recommendation_service.services.interfaces.ICollectionService;
import com.merufureku.aromatica.recommendation_service.utilities.TokenUtility;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static com.merufureku.aromatica.recommendation_service.constants.RecommendationCollectionConstants.COLLECTION_SERVICE;

@Service
@RequiredArgsConstructor
public class CollectionsService implements ICollectionService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final RestTemplate restTemplate;
    private final UrlConfig urlConfig;
    private final TokenUtility tokenUtility;
    private final RestExceptionHelper restExceptionHelper;

    @Override
    @CircuitBreaker(name = "collectionServiceCircuitBreaker")
    public BaseResponse<UserCollectionsResponse> getUserCollections(Integer userId, int version, String correlationId) {

        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getCollectionUrl())
                    .append("/internal/collections/")
                    .append(userId)
                    .append("?version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching User Collections from URL: {}", url.toString());

            ResponseEntity<BaseResponse<UserCollectionsResponse>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.GET, new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Successfully fetched User Collections for userId: {}", userId);

            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("Unexpected error fetching User Collections: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public BaseResponse<CollectionsResponse> getAllCollectionsFromSimilarFragrance(Integer excludedUserId, GetFragranceBatchParam param, int version, String correlationId) {

        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getCollectionUrl())
                    .append("/internal/collections/batch")
                    .append("?excludeUserId=")
                    .append(excludedUserId)
                    .append("&version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching all collections with users who have collected fragrance IDs: {} from URL: {}", param.fragranceIds(), url.toString());

            ResponseEntity<BaseResponse<CollectionsResponse>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.POST, new HttpEntity<>(param, getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Successfully fetched all users collections for fragrance IDs: {}", param.fragranceIds());

            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("Unexpected error fetching all users collections: {}", e.getMessage());
            throw e;
        }
    }

    private HttpHeaders getHeaders(){
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getToken());
        return headers;
    }

    private String getToken(){
        return tokenUtility.generateInternalToken(COLLECTION_SERVICE);
    }
}

