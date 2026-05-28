package org.javastro.ivoa.registry.internal;
/*
 * Created on 30/05/2023 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.basex.core.BaseXException;
import org.javastro.ivoa.entities.Ivoid;

public interface RegistryStoreInterface  {
    /**
     * open the store.
     */
    void open();

    /**
     * close the store.
     */
    void close();

    /**
     * create a new entry in the store. The
     * @param content the content to be stored.
     * @param path the path within the store to store the content. The path should be a valid path within the store, and should include the filename (e.g. "managed/base.xml").
     */
   void create(String content, String path);


    /**
     * creates/updates the given registry entry in the registry store.
     * The entry is given as an XML string, which should be a valid registry entry (i.e. it should conform to the VOResource schema).
     *  The entry is stored in the standard managed location.
     * @param xml the registry entry to be stored.
     */
    default void createEntry(String xml) {
        createEntry(xml, "managed/base.xml");
    }

    /**
     * creates/updates the given registry entry in the registry store. The entry is given as an XML string, which should be a valid registry entry (i.e. it should conform to the VOResource schema). The path parameter is
     * used to specify the location of the entry in the registry store. The location
     * needs to have a VOResources root element already in place.
     *
     * @param xml the registry entry to be stored.
     * @param path the path to store the entry in the registry store.
     *
     */
    void createEntry(String xml, String path); // TODO - add exception - there is no signal if this fails...

    /**
     * read the content of the given path from the store.
     * @param path the path to read from the store. The path should be a valid path within the store, and should include the filename (e.g. "managed/base.xml").
     * @return the content of the given path.
     * @throws BaseXException if there is an error reading from the store.
     */
    String read(String path) throws BaseXException; //TODO really do not want BaseX specific extension.

    /**
     * checks if the given path exists in the store.
     * @param path the path to chack for existence. The path should be a valid path within the store, and should include the filename (e.g. "managed/base.xml").
     * @return true if the path already exists.
     */
    boolean exists(String path);

    void delete(Ivoid id);
}

