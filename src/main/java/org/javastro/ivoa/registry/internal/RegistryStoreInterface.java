package org.javastro.ivoa.registry.internal;
/*
 * Created on 30/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.basex.core.BaseXException;
import org.javastro.ivoa.entities.Ivoid;

public interface RegistryStoreInterface  {
    void open();

    void close();

    void create(String xml);// todo - add exception
    void create(String xml, String path);
    String read(String path) throws BaseXException;
    boolean exists(String path);

    void delete(Ivoid id);
}

