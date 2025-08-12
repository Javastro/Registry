package org.javastro.ivoa.registry.internal;
/*
 * Created on 30/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.Ivoid;

public interface RegistryStoreInterface extends RegistryInterface {

    void create(String xml); // todo - add exception
    void delete(Ivoid id);
}

