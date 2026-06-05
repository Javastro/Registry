package org.javastro.ivoa.registry.harvesting;


/*
 * Created on 03/02/2025 by Paul Harrison (paul.harrison@manchester.ac.uk).
 */

import org.javastro.ivoa.entities.resource.Resource;
import org.javastro.ivoa.entities.oai.oaipmh.*;
import org.javastro.ivoa.registry.XMLUtils;
import org.javastro.ivoa.registry.oaipmh.client.OaiPMHClient;
import org.jboss.logging.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Node;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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
   private final XMLUtils xmlUtils = new XMLUtils();

   private String resumptionToken = null;
   private int total;
   private int cursor = 0;
   private final int harvestChunkSize;


   /**
    * Create a new harvest client for the given registry OAI-PMH URL.
    *
    * The client creates objects for the responses and will do XML validation if required.
    * Note, however, that failed validation will fail the operations. Otherwise it is somewhat lax in its parsing.
    *
    * OAI-PMH services are able to chunk their responses - the client will automatically handle the continuation. It is possible to
    * limit the number of records returned in a single call to getRecords, which may be useful for very large harvests.
    *
    * @param url The oai-pmh endpoint of the registry to harvest.
    * @param doXMLValidation if true do XML validation of the responses.
    * @param harvestChunkSize the maximum number of records to return in a single call to getRecords - 0 means return whatever the registry gives in a single response.
    */
   public HarvestClient(String url, boolean doXMLValidation, int harvestChunkSize) {
      this.harvestChunkSize = harvestChunkSize;
      client = new OaiPMHClient(url, doXMLValidation);
      resetCursor();
   }

   public void resetCursor() {
      resumptionToken = null;
      total = 0;
      cursor = 0;
   }
   public boolean hasMoreRecords() {
      return resumptionToken != null && !resumptionToken.isBlank();
   }

   /**
    * check that the registry is set up to be harvestable
    * @return true if OK
    */
   public boolean validate(){
      boolean retval = true;
      ListMetadataFormatsType mt = client.listMetadataFormats();

      if(mt == null || mt.getMetadataFormats().stream().noneMatch(m -> m.getMetadataPrefix().equals("ivo_vor")))
      {
         retval = false;
         LOG.errorv("the Registry at {0} does not serve metadata format {1}",client.getUrl(), METADATA_PREFIX);
      }

      ListSetsType st = client.listSets();
      if(st == null || st.getSets().stream().noneMatch(m -> m.getSetSpec().equals(IVO_MANAGED_SET)))
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

      return xmlUtils.OaiIdentifyToResource(r) ;
   }


   public Resource getRecord(String identifier)
   {
      RecordType r = client.getRecord(identifier, METADATA_PREFIX);
      return xmlUtils.OaiMetadataToResource(r) ;
   }

   public List<HeaderType> getIdentifiers(Instant from, Instant until){
      return getIdentifiers(from, until, IVO_MANAGED_SET);
   }

   public List<HeaderType> getIdentifiers(Instant from, Instant until, String set){
      List<HeaderType> hh = new ArrayList<>();

      do {
         ListIdentifiersType li = client.listIdentifiers(resumptionToken==null?METADATA_PREFIX:null, set, from, until, resumptionToken);
         if (li == null) {
            // OAI error response (e.g. noRecordsMatch) — no identifiers to harvest
            LOG.infov("listIdentifiers returned no result (OAI error or empty response) for {0}", client.getUrl());
            break;
         }
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

   /** Returns true for OAI-PMH records that contain a parseable VOResource metadata element. */
   private static boolean isHarvestableRecord(RecordType r) {
      return r.getMetadata() != null && r.getMetadata().getAny() instanceof Node && ((Node)r.getMetadata().getAny()).hasChildNodes();//TODO this is quite a weak test
   }

   public List<RecordType> getRecords(Instant from, Instant until){
      return getRecords(from, until, IVO_MANAGED_SET);
   }

   public List<RecordType> getRecords(Instant from, Instant until, String set){
      List<RecordType> sr = new ArrayList<>();
      do {
         ListRecordsType rec = client.listRecords(METADATA_PREFIX, set, from, until, resumptionToken);
         if (rec == null || rec.getRecords().isEmpty()) {
            // OAI error response (e.g. noRecordsMatch) — no records to harvest
            LOG.infov("listRecords returned no result (OAI error or empty response) for {0}", client.getUrl());
            break;
         }
         sr.addAll(rec.getRecords());
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
         LOG.infov("returned: {0} of {1}",cursor,total);
      } while (sr.size() < harvestChunkSize && !(sr.size() == total)); // allow fairly big in memory store to build up
      return sr;
   }
 


}
