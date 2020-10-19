package io.quarkus.rest.runtime.handlers;

import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import io.quarkus.rest.runtime.core.EncodedMediaType;
import io.quarkus.rest.runtime.core.LazyResponse;
import io.quarkus.rest.runtime.core.QuarkusRestRequestContext;
import io.quarkus.rest.runtime.jaxrs.QuarkusRestResponse;
import io.quarkus.rest.runtime.jaxrs.QuarkusRestResponseBuilder;

/**
 * Our job is to turn endpoint return types into Response instances
 */
public class ResponseHandler implements RestHandler {

    @Override
    public void handle(QuarkusRestRequestContext requestContext) throws Exception {
        Object result = requestContext.getResult();
        if (result instanceof Response) {
            boolean mediaTypeAlreadyExists = false;
            //we already have a response
            //set it explicitly
            Response.ResponseBuilder responseBuilder;
            Response existing = (Response) result;
            if (existing.getEntity() instanceof GenericEntity) {
                GenericEntity<?> genericEntity = (GenericEntity<?>) existing.getEntity();
                requestContext.setGenericReturnType(genericEntity.getType());
                responseBuilder = Response.fromResponse(existing).entity(genericEntity.getEntity());
            } else {
                // TCK says to use the entity type as generic type if we return a response
                if (existing.hasEntity())
                    requestContext.setGenericReturnType(existing.getEntity().getClass());
                //TODO: super inefficent
                responseBuilder = Response.fromResponse((Response) result);
                if ((result instanceof QuarkusRestResponse)) {
                    // needed in order to preserve entity annotations
                    QuarkusRestResponse quarkusRestResponse = (QuarkusRestResponse) result;
                    if (quarkusRestResponse.getEntityAnnotations() != null) {
                        requestContext.setAdditionalAnnotations(quarkusRestResponse.getEntityAnnotations());
                    }
                }
            }
            if (existing.getMediaType() != null) {
                requestContext.setResponseContentType(existing.getMediaType());
                mediaTypeAlreadyExists = true;
            }
            EncodedMediaType produces = requestContext.getResponseContentType();
            if (!mediaTypeAlreadyExists && produces != null) {
                responseBuilder.header(HttpHeaders.CONTENT_TYPE, produces.toString());
            }
            if ((responseBuilder instanceof QuarkusRestResponseBuilder)) {
                // avoid unnecessary copying of HTTP headers from the Builder to the Response
                requestContext
                        .setResponse(new LazyResponse.Existing(((QuarkusRestResponseBuilder) responseBuilder).build(false)));
            } else {
                requestContext.setResponse(new LazyResponse.Existing(responseBuilder.build()));
            }
        } else {
            requestContext.setResponse(new LazyResponse() {

                Response response;

                @Override
                public Response get() {
                    if (response == null) {
                        Response.ResponseBuilder responseBuilder;
                        if (result instanceof GenericEntity) {
                            GenericEntity<?> genericEntity = (GenericEntity<?>) result;
                            requestContext.setGenericReturnType(genericEntity.getType());
                            responseBuilder = QuarkusRestResponse.ok(genericEntity.getEntity());
                        } else if (result == null) {
                            // FIXME: custom status codes depending on method?
                            responseBuilder = QuarkusRestResponse.noContent();
                        } else {
                            // FIXME: custom status codes depending on method?
                            responseBuilder = QuarkusRestResponse.ok(result);
                        }
                        EncodedMediaType produces = requestContext.getResponseContentType();
                        if (produces != null) {
                            responseBuilder.header(HttpHeaders.CONTENT_TYPE, produces.toString());
                        }
                        if ((responseBuilder instanceof QuarkusRestResponseBuilder)) {
                            // avoid unnecessary copying of HTTP headers from the Builder to the Response
                            response = ((QuarkusRestResponseBuilder) responseBuilder).build(false);
                        } else {
                            response = responseBuilder.build();
                        }
                    }
                    return response;
                }

                @Override
                public boolean isCreated() {
                    return response != null;
                }
            });

        }
    }
}