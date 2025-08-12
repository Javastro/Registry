package org.javastro.ivoa.registry;


/*
 * Created on 11/08/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.javastro.ivoa.entities.IvoaJAXBContextFactory;
import org.javastro.ivoa.entities.IvoaJAXBUtils;
import org.javastro.ivoa.entities.resource.registry.Registry;
import org.javastro.ivoa.schema.Namespaces;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Objects;


public class MarshallTest {



   @Test
   public void testClean() throws JAXBException, IOException, SAXException {
      Registry thisRegistry = IvoaJAXBUtils.unmarshall(Objects.requireNonNull(this.getClass().getResourceAsStream("/RegistryTemplate.xml")), Registry.class);
      String out = new XMLUtils().marshall(thisRegistry);
      System.out.println(out);

   }

   @Test
   public void rawtest() throws JAXBException, IOException, SAXException {
      Registry thisRegistry = IvoaJAXBUtils.unmarshall(Objects.requireNonNull(this.getClass().getResourceAsStream("/RegistryTemplate.xml")), Registry.class);
      JAXBContext context = IvoaJAXBContextFactory.newInstance();
      Marshaller marshaller = context.createMarshaller();
      marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
      marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
      StringWriter writer = new StringWriter();
      Namespaces ns = Namespaces.RI;
      JAXBElement<Registry> el = new JAXBElement<>(
            new QName(ns.getNamespace(), "Resource", ns.getPrefix()),
            Registry.class, thisRegistry);
      marshaller.marshal(el, writer);
      System.out.println(writer.toString());
   }
}
