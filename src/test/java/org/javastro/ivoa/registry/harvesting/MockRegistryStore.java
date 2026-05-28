package org.javastro.ivoa.registry.harvesting;


import org.javastro.ivoa.entities.Ivoid;
import org.javastro.ivoa.registry.internal.RegistryStoreInterface;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockRegistryStore implements RegistryStoreInterface {

    private static final Logger log = LoggerFactory.getLogger(MockRegistryStore.class);

    @Override
    public void open() {
      log.info("MockRegistryStore opened");
    }

    @Override
    public void close() {
     log.info("MockRegistryStore closed");
    }

    @Override
    public void create(String content, String path) {
        log.info("MockRegistryStore created content at {}",path);
    }

    @Override
    public void createEntry(String xml, String path) {
     log.info("MockRegistryStore created xml: {}, path: {}", xml, path);

    }

    @Override
    public void deleteEntry(Ivoid id) {
         log.info("MockRegistryStore delete entry with id: {}", id);
    }

    @Override
    public String read(String path) {
        log.info("MockRegistryStore read xml: {}", path);
        return null;
    }

    @Override
    public boolean exists(String path) {
        log.info("MockRegistryStore exists xml: {}", path);
        return false;
    }


    @Override
    public void delete(String id) {
        log.info("MockRegistryStore delete id: {}", id);

    }
}
