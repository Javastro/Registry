package org.javastro.ivoa.registry.oaipmh;
/*
 * Created on 28/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class OaiProperties {



    public String responseTime;

    public String getResponseTime() {
       return Instant.now().atZone(ZoneId.of("UTC")).format(DateTimeFormatter.ISO_INSTANT);
    }
    public String regOaiEndpoint;

    public String regIvorn;

    public String regAdminEmail;

    public String requestVerb;

    public OaiProperties(String regOaiEndpoint, String regAdminEmail) {
        this.regOaiEndpoint = regOaiEndpoint;
        this.regAdminEmail = regAdminEmail;
    }


}
