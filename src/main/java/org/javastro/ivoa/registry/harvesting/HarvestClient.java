package org.javastro.ivoa.registry.harvesting;


/*
 * Created on 03/02/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.resource.registry.iface.ResourceInstance;
import org.javastro.ivoa.entities.resource.registry.oaipmh.*;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHClient;
import org.jboss.logging.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A registry client that will directly return Registry objects.
 * Also deals with continuation tokens seamlessly.
 *
 * TODO it would be a good idea to have an asynchronous streaming style client too for really big harvests.
 */
public class HarvestClient {

   private static final Logger LOG = Logger.getLogger(OaiPMHClient.class);
   public final static String METADATA_PREFIX = "ivo_vor";
   public static final String IVO_MANAGED_SET = "ivo_managed";
   private static final org.slf4j.Logger log = LoggerFactory.getLogger(HarvestClient.class);
   final OaiPMHClient client;
   public HarvestClient(String url) {
      client = new OaiPMHClient(url,false);

   }

   /**
    * check that the registry is set up to be harvestable
    * @return true if OK
    */
   public boolean validate(){
      boolean retval = true;
      ListMetadataFormatsType mt = client.listMetadataFormats();

      if(mt.getMetadataFormats().stream().noneMatch(m -> m.getMetadataPrefix().equals("ivo_vor")))
      {
         retval = false;
         LOG.errorv("the Registry at {0} does not serve metadata format {1}",client.getUrl(), METADATA_PREFIX);
      }

      ListSetsType st = client.listSets();
      if(st.getSets().stream().noneMatch(m -> m.getSetSpec().equals(IVO_MANAGED_SET)))
      {
         retval = false;
         LOG.errorv("the Registry at {0} does not have a harvestable set {1}",client.getUrl(),IVO_MANAGED_SET );
      }

      return retval;
   }


   /**
    * The registry entry for the registry being harvested.
    * @return the resource describing the registry.
    */
   public Resource identify()
   {
      IdentifyType r = client.identify();

      return ((ResourceInstance) r.getDescriptions().get(0).getAny()).getValue();
   }


   public Resource getRecord(String identifier)
   {
      RecordType r = client.getRecord(identifier, METADATA_PREFIX);
      return ((ResourceInstance)r.getMetadata().getAny()).getValue();
   }

   public List<HeaderType> getIdentifiers(Instant from, Instant until){
      List<HeaderType> hh = new ArrayList<>();
      String resumptionToken = null;
      int total; //IMPL all this complexity with total and cursor is to deal with implementations that do not return cursor values and then only to provide a log!
      int cursor = 0;
      do {
         ListIdentifiersType li = client.listIdentifiers(resumptionToken==null?METADATA_PREFIX:null, IVO_MANAGED_SET, from, until, resumptionToken);
         hh.addAll(li.getHeaders());

         if(li.getResumptionToken()!=null)
         {
            total = li.getResumptionToken().getCompleteListSize().intValue();
            resumptionToken = li.getResumptionToken().getValue();
            cursor = li.getResumptionToken().getCursor()!= null?li.getResumptionToken().getCursor().intValue(): cursor+li.getHeaders().size();
         }
         else {
            resumptionToken = null;
            total = li.getHeaders().size();
         }
         LOG.infov("returned: {0} of {1}",cursor+li.getHeaders().size(),total);

      } while (resumptionToken != null);
      return hh;
   }

   public List<Resource> getRecords(Instant from, Instant until){
      Stream<Resource> sr = Stream.<Resource>empty();
      String resumptionToken = null;
      int total;
      int cursor = 0;
      do {
         ListRecordsType rec = client.listRecords(METADATA_PREFIX, IVO_MANAGED_SET, from, until, resumptionToken);
         sr = Stream.concat(sr, rec.getRecords().stream().map(r->((ResourceInstance)r.getMetadata().getAny()).getValue()));
         if(rec.getResumptionToken()!=null)
         {
            total = rec.getResumptionToken().getCompleteListSize().intValue();
            resumptionToken = rec.getResumptionToken().getValue();
            cursor = rec.getResumptionToken().getCursor()!= null?rec.getResumptionToken().getCursor().intValue(): cursor+rec.getRecords().size();

         }
         else {
            resumptionToken = null;
            total = rec.getRecords().size();
         }
         LOG.infov("returned: {0} of {1}",cursor+rec.getRecords().size(),total);
      } while (resumptionToken != null);
      return sr.collect(Collectors.toList());
   }

}
