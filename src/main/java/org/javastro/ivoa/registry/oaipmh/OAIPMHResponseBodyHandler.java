package org.javastro.ivoa.registry.oaipmh;


/*
 * Created on 08/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.MessageBodyWriter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

@Provider
public class OAIPMHResponseBodyHandler implements MessageBodyWriter<OAIPMHResponse> {
   @Override
   public boolean isWriteable(Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType) {
      return type == OAIPMHResponse.class;
   }

   @Override
   public void writeTo(OAIPMHResponse oaipmhResponse, Class<?> type, Type genericType, Annotation[] annotations, MediaType mediaType, MultivaluedMap<String, Object> httpHeaders, OutputStream entityStream) throws IOException, WebApplicationException {
       entityStream.write(oaipmhResponse.response.getBytes(StandardCharsets.UTF_8));
   }
}
