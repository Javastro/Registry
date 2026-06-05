package org.javastro.ivoa.registry;


/*
 * Created on 07/07/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.oai.dublincore.Dc;
import org.javastro.ivoa.entities.oai.dublincore.simple.ElementType;
import org.javastro.ivoa.entities.oai.dublincore.simple.ObjectFactory;
import org.javastro.ivoa.entities.oai.oaipmh.DescriptionType;
import org.javastro.ivoa.entities.oai.oaipmh.SetType;
import org.javastro.ivoa.schema.Namespaces;

import java.util.List;

public class RegistrySet {

    final ObjectFactory dcfactory = new ObjectFactory();
    Namespaces dcNS =Namespaces.DC;

    public String getName() {
        return name;
    }

    final String name;

    public RegistrySet(String name, String description) {
        this.name = name;
        this.description = description;
    }

    final String description;


    public SetType asOAIPMH(){
        return new SetType(name,name, List.of(new DescriptionType(new Dc(List.of(dcfactory.createDescription(new ElementType(description,"en")))))));
    }

    static public final RegistrySet IVO_MANAGED = new RegistrySet("ivo_managed","This set returns the records that are solely managed by this\n" +
          "      registry--that is, records that created by this registry.");
    static public final RegistrySet IVO_HARVESTED = new RegistrySet("ivoa_harvested","This set returns the records that are harvested by this registry from other registries.");
    static public final RegistrySet IVO_ALL = new RegistrySet("ivo_all","This set returns all records in the registry.");
}
