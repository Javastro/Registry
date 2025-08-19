package org.javastro.ivoa.registry.internal;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.basex.core.BaseXException;
import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.Service;

import java.util.List;

public interface RegistryQueryInterface extends RegistryInterface {

   String xquery(String query) throws BaseXException;

   List<Resource> listResources();
   Resource getResource(Ivoid id) throws BaseXException;

   List<Service> listServices();
}
