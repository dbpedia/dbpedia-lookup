package org.dbpedia.lookup.impl.solr;

import java.io.IOException;
import java.util.HashMap;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.common.SolrInputDocument;
import org.dbpedia.lookup.ILookupIndexer;

/**
 * Solr-Based implementation of the ILookupIndexer interface. This is not working and not finished.
 * This is only here for archiving purposes or in case anyone needs a starting point for their own implementation.
 * @author Jan Forberg
 *
 */
public class SolrLookupIndexer implements ILookupIndexer {

	private HashMap<String, Integer> refCountMap;

	private HashMap<String, String> labelMap;
	
	private SolrClient solrClient;
	
	private String coreName;
	
	private SolrDocumentPool documentPool;
	
	private int updateCount;
	
	private int updateInterval;

	private String lastAction;

	public SolrLookupIndexer(String solrUrl, String coreName, int updateInterval) {
		
		documentPool = new SolrDocumentPool(updateInterval);
		refCountMap = new HashMap<String, Integer>(1);
		refCountMap.put("inc", 1);
		
		labelMap = new HashMap<String, String>(1);
		labelMap.put("add", null);
		
		solrClient = new HttpSolrClient.Builder(solrUrl)
        	.withConnectionTimeout(10000)
        	.withSocketTimeout(60000)
        	.build();
		
		this.coreName = coreName;
		this.updateInterval = updateInterval;
	
		
	}
	

	public void indexField(String key, String resource, String text, boolean isTokenized) {
		
		SolrInputDocument doc = documentPool.get();
		doc.addField("resource", resource);
		
	
		labelMap.put("add", text);
		doc.addField(key, labelMap);
		
		lastAction = "added label  '" + text + "'";
		
		try {
			solrClient.add(coreName, doc);
			update();
		} catch (SolrServerException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}
	
	public void addLabel(String resource, String label) {
		
		
		
		
	}
	
	public void increaseRefCount(String resource) {
		
		SolrInputDocument doc = documentPool.get();
		doc.addField("resource", resource);
		doc.addField("refCount", refCountMap);
		
		try {
			solrClient.add(coreName, doc);
		
		
			lastAction = "increased refCount of " + resource;
			update();
		
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void commit() {
		
		try {
			solrClient.commit(coreName);
		} catch (SolrServerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		System.out.println("Commiting after " + updateCount + " updates.");
		System.out.println("Last: " + lastAction);
		updateCount = 0;
		documentPool.reset();	
	}
	
	private void update() throws SolrServerException, IOException {
		
		updateCount++;
		
		if(updateCount >= updateInterval) {
			
			commit();
			
		}
	}

	public boolean clearIndex() {
		try {
			solrClient.deleteByQuery(coreName, "*:*");
			solrClient.commit(coreName);
			
			return true;
			
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}


	@Override
	public void indexRelation(String resource, String relation, String object) {
		// TODO Auto-generated method stub
		
	}

}
