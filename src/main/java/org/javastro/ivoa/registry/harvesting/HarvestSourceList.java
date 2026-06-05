package org.javastro.ivoa.registry.harvesting;

/*
 * Created on 29/04/2026 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * JAXB root element that wraps a list of {@link HarvestSource} objects.
 * Serialized as {@code <harvestSources>} in the BaseX catalog document.
 */
@XmlRootElement(name = "harvestSources")
@XmlAccessorType(XmlAccessType.FIELD)
public class HarvestSourceList {

    @XmlElement(name = "source")
    private List<HarvestSource> sources = new ArrayList<>();

    public HarvestSourceList() {
    }

    public List<HarvestSource> getSources() {
        return sources;
    }

    public void setSources(List<HarvestSource> sources) {
        this.sources = sources;
    }
}
