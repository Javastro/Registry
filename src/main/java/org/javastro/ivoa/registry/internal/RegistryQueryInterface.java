package org.javastro.ivoa.registry.internal;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.basex.core.BaseXException;

public interface RegistryQueryInterface extends RegistryInterface {

   String xquery(String query) throws BaseXException;
}
