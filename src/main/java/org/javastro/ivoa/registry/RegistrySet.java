package org.javastro.ivoa.registry;


/*
 * Created on 07/07/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.resource.registry.dublincore.Dc;
import org.javastro.ivoa.entities.resource.registry.dublincore.simple.ElementType;
import org.javastro.ivoa.entities.resource.registry.dublincore.simple.ObjectFactory;
import org.javastro.ivoa.entities.resource.registry.oaipmh.DescriptionType;
import org.javastro.ivoa.entities.resource.registry.oaipmh.SetType;
import org.javastro.ivoa.schema.Namespaces;

import java.util.List;

public class RegistrySet {

    final ObjectFactory dcfactory = new ObjectFactory();
    Namespaces dcNS =Namespaces.DC;

    final String name;

    public RegistrySet(String name, String description) {
        this.name = name;
        this.description = description;
    }

    final String description;


    public SetType asOAIPMH(){
        return new SetType(name,name, List.of(new DescriptionType(new Dc(List.of(dcfactory.createDescription(new ElementType(description,"en")))))));
    }

    static final RegistrySet IVO_MANAGED = new RegistrySet("ivo_managed","This set returns the records that are solely managed by this\n" +
          "      registry--that is, records that created by this registry.");
}
