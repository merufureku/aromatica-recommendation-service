package com.merufureku.aromatica.recommendation_service.services.impl;

import com.merufureku.aromatica.recommendation_service.config.UrlConfig;
import com.merufureku.aromatica.recommendation_service.dto.params.GetFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.responses.BaseResponse;
import com.merufureku.aromatica.recommendation_service.dto.responses.GetAllReviews;
import com.merufureku.aromatica.recommendation_service.helper.RestExceptionHelper;
import com.merufureku.aromatica.recommendation_service.services.interfaces.IReviewService;
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

import static com.merufureku.aromatica.recommendation_service.constants.RecommendationCollectionConstants.REVIEW_SERVICE;

@Service
@RequiredArgsConstructor
public class ReviewService implements IReviewService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final RestTemplate restTemplate;
    private final UrlConfig urlConfig;
    private final TokenUtility tokenUtility;
    private final RestExceptionHelper restExceptionHelper;

    @Override
    @CircuitBreaker(name = "reviewServiceCircuitBreaker")
    public BaseResponse<GetAllReviews> getUserReviews(int userId, int minRating, int version, String correlationId) {

        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getReviewUrl())
                    .append("/internal/reviews/")
                    .append(userId)
                    .append("?minRating=")
                    .append(minRating)
                    .append("&version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching user reviews from URL: {}", url.toString());

            ResponseEntity<BaseResponse<GetAllReviews>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.GET, new HttpEntity<>(getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Fetching user reviews from URL: {} success", url.toString());

            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("An unexpected error occurred while fetching user reviews: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public BaseResponse<GetAllReviews> getReviews(Integer excludedUserId, GetFragranceBatchParam param, int minRating, int version, String correlationId) {

        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getReviewUrl())
                    .append("/internal/reviews")
                    .append("?minRating=")
                    .append(minRating)
                    .append("&excludedUserId=")
                    .append(excludedUserId)
                    .append("&version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching reviews for fragrance IDs: {} from URL: {}", param.fragranceIds(), url.toString());

            ResponseEntity<BaseResponse<GetAllReviews>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.POST, new HttpEntity<>(param, getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Fetching reviews for fragrance IDs: {} from URL: {} success", param.fragranceIds(), url.toString());

            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("An unexpected error occurred while fetching reviews: {}", e.getMessage());
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
        return tokenUtility.generateInternalToken(REVIEW_SERVICE);
    }
}
