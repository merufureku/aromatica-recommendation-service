package com.merufureku.aromatica.recommendation_service.services.impl;

import com.merufureku.aromatica.recommendation_service.config.UrlConfig;
import com.merufureku.aromatica.recommendation_service.dto.params.ExcludeFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.params.GetFragranceBatchParam;
import com.merufureku.aromatica.recommendation_service.dto.responses.BaseResponse;
import com.merufureku.aromatica.recommendation_service.dto.responses.FragranceDetailedListResponse;
import com.merufureku.aromatica.recommendation_service.dto.responses.FragranceNoteListResponse;
import com.merufureku.aromatica.recommendation_service.exceptions.ServiceException;
import com.merufureku.aromatica.recommendation_service.helper.RestExceptionHelper;
import com.merufureku.aromatica.recommendation_service.services.interfaces.IFragranceService;
import com.merufureku.aromatica.recommendation_service.utilities.TokenUtility;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import static com.merufureku.aromatica.recommendation_service.constants.RecommendationCollectionConstants.FRAGRANCE_SERVICE;
import static com.merufureku.aromatica.recommendation_service.enums.CustomStatusEnums.NOTE_NOT_EXIST;

@Service
@RequiredArgsConstructor
public class FragranceService implements IFragranceService {

    private final Logger logger = LogManager.getLogger(this.getClass());

    private final RestTemplate restTemplate;
    private final UrlConfig urlConfig;
    private final TokenUtility tokenUtility;
    private final RestExceptionHelper restExceptionHelper;

    @Override
    public BaseResponse<FragranceDetailedListResponse> getPerfumes(GetFragranceBatchParam param, int version, String correlationId) {
        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getFragranceUrl())
                    .append("/internal/fragrances/batch/full")
                    .append("?version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching perfumes from URL: {}", url.toString());

            ResponseEntity<BaseResponse<FragranceDetailedListResponse>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.POST, new HttpEntity<>(param, getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Received perfumes response with status code: {}", responseEntity.getStatusCode());

            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("Unexpected error occurred while fetching perfumes: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public BaseResponse<FragranceNoteListResponse> getPerfumeNotes(GetFragranceBatchParam param, int version, String correlationId) {
        try{
            var url = new StringBuilder();
            url
                    .append(urlConfig.getFragranceUrl())
                    .append("/internal/fragrances/batch/notes")
                    .append("?version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching perfume notes from URL: {}", url.toString());

            ResponseEntity<BaseResponse<FragranceNoteListResponse>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.POST, new HttpEntity<>(param, getHeaders()), new ParameterizedTypeReference<>() {}
            );

            logger.info("Received perfume notes response with status code: {}", responseEntity.getStatusCode());

            if (responseEntity.getBody() == null || responseEntity.getBody().data() == null) {
                throw new ServiceException(NOTE_NOT_EXIST);
            }
            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("Unexpected error occurred while fetching perfume notes: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    public BaseResponse<FragranceNoteListResponse> getPerfumeNotes(ExcludeFragranceBatchParam param, int version, String correlationId) {
        try {
            var url = new StringBuilder();
            url
                    .append(urlConfig.getFragranceUrl())
                    .append("/internal/fragrances/exclude/notes")
                    .append("?version=")
                    .append(version)
                    .append("&correlationId=")
                    .append(correlationId);

            logger.info("Fetching All perfume and notes from URL: {}", url.toString());

            ResponseEntity<BaseResponse<FragranceNoteListResponse>> responseEntity = restTemplate.exchange(
                    url.toString(), HttpMethod.POST, new HttpEntity<>(param, getHeaders()), new ParameterizedTypeReference<>() {}
            );

            if (responseEntity.getBody() == null || responseEntity.getBody().data() == null) {
                throw new ServiceException(NOTE_NOT_EXIST);
            }

            logger.info("Received All perfume and notes response with status code: {}", responseEntity.getStatusCode());
            return responseEntity.getBody();
        }
        catch (HttpClientErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (HttpServerErrorException ex){
            throw restExceptionHelper.handleException(ex);
        }
        catch (Exception e){
            logger.error("Unexpected error occurred while fetching all perfume notes: {}", e.getMessage());
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
        return tokenUtility.generateInternalToken(FRAGRANCE_SERVICE);
    }
}

