package org.javastro.ivoa.registry.oaipmh.client;


/*
 * Created on 07/02/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import io.quarkus.rest.client.reactive.ClientQueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.concurrent.CompletionStage;


@RegisterRestClient
@Produces({MediaType.TEXT_XML,MediaType.APPLICATION_XML})
public interface OaiPMHClientInterface {

    @GET
    @ClientQueryParam(name = "verb", value = "Identify")
    CompletionStage<String> identify();

    @GET
    @ClientQueryParam(name = "verb", value = "ListMetadataFormats")
    CompletionStage<String> listMetadataFormats( @QueryParam("identifier") String identifier);


    @GET
    @ClientQueryParam(name = "verb", value = "ListSets")
    CompletionStage<String> listSets();

    @GET
    @ClientQueryParam(name = "verb", value = "ListIdentifiers")
    CompletionStage<String> listIdentifiers(
          @QueryParam("metadataPrefix") String metadataPrefix,
          @QueryParam("from") String from,
          @QueryParam("until") String until,
          @QueryParam("set") String set,
          @QueryParam("resumptionToken") String resumptionToken
    );

    @GET
    @ClientQueryParam(name = "verb", value = "ListRecords")
    CompletionStage<String> listRecords(
          @QueryParam("metadataPrefix") String metadataPrefix,
          @QueryParam("from") String from,
          @QueryParam("until") String until,
          @QueryParam("set") String set,
          @QueryParam("resumptionToken") String resumptionToken
    );

    @GET
    @ClientQueryParam(name = "verb", value = "GetRecord")
    CompletionStage<String> getRecord( @QueryParam("identifier")
       String identifier, @QueryParam("metadataPrefix") String metadataPrefix);


}
