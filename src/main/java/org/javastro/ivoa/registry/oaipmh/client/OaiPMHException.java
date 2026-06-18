package org.javastro.ivoa.registry.oaipmh.client;


import org.javastro.ivoa.entities.oai.oaipmh.OAIPMHerrorType;

import java.util.ArrayList;
import java.util.List;

public class OaiPMHException extends Exception {
    private List<OAIPMHerrorType> oaiErrors = new ArrayList<>();
    public OaiPMHException(String message) {
        super(message);
    }

    public OaiPMHException(String message, Throwable cause) {
        super(message, cause);
    }

   public OaiPMHException(List<OAIPMHerrorType> errors, String message) {
    super(message);
    this.oaiErrors = errors;
   }

   public List<OAIPMHerrorType> getOaiErrors() {
      return oaiErrors;
   }

   @Override
   public String getMessage() {
      return super.getMessage() + " " + oaiErrors.stream().map(OAIPMHerrorType::getValue).reduce("", (a, b) -> a + "; " + b);
   }
}
