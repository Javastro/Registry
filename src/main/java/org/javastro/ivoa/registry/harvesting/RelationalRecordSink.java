package org.javastro.ivoa.registry.harvesting;

import org.w3c.dom.Node;

/**
 * Optional destination (e.g. the RegTAP relational database) for individual harvested resources.
 * Implementations exist only when the corresponding feature is enabled.
 */
public interface RelationalRecordSink {

    /**
     * Add or replace a single resource.
     * @param resourceXml an {@code ri:Resources} document containing exactly one {@code ri:Resource}.
     * @throws Exception if the resource cannot be stored; the failure affects only this resource.
     */
    void addResource(String resourceXml) throws Exception;
    /**
     * Add or replace a single resource.
     * @param resourceXml DOM of a  {@code ri:Resource}.
     * @throws Exception if the resource cannot be stored; the failure affects only this resource.
     */
    void addResource(Node resourceXml) throws Exception;

    /**
     * Remove the resource with the given identifier.
     * @param identifier the IVOA identifier.
     */
    void deleteResource(String identifier);
}
