package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 29/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.xml.bind.annotation.adapters.XmlAdapter;

import java.time.Instant;

/**
 * JAXB adapter that maps {@link Instant} to/from an ISO-8601 string.
 */
public class InstantXmlAdapter extends XmlAdapter<String, Instant> {

    @Override
    public Instant unmarshal(String v) {
        if (v == null || v.isEmpty()) {
            return null;
        }
        return Instant.parse(v);
    }

    @Override
    public String marshal(Instant v) {
        return v != null ? v.toString() : null;
    }
}
