package org.javastro.ivoa.registry.internal;


/*
 * Created on 07/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.Ivoid;

import java.time.ZonedDateTime;

public interface RegistryInterface {
   void open();

   void close();

   String oaiListIDs(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix) ;
   String oaiListRecords(ZonedDateTime start, ZonedDateTime end, String setName, String metadataPrefix);
   String oaiGetRecord(Ivoid id, String metadataPrefix) ;

}
