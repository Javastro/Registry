package org.javastro.ivoa.registry.oaipmh;


/*
 * Created on 07/07/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import org.javastro.ivoa.entities.IvoaJAXBContextFactory;

public class CustomJaxbContext{

@Singleton
@Produces
JAXBContext jaxbContext() throws JAXBException {

   return IvoaJAXBContextFactory.newInstance();
}
}
