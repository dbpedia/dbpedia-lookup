package org.dbpedia.lookup.impl.solr;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.dbpedia.lookup.ILookupSearcher;
import org.dbpedia.lookup.QueryField;
import org.json.JSONObject;

/**
 * Solr-Based implementation of the ILookupSearcher interface. This is not working and not finished.
 * This is only here for archiving purposes or in case anyone needs a starting point for their own implementation.
 * @author Jan Forberg
 *
 */
public class SolrLookupSearcher implements ILookupSearcher {

	 
	private HttpSolrClient solrClient;
	private String coreName;

	public SolrLookupSearcher(String solrUrl, String coreName) {
		
		solrClient = new HttpSolrClient.Builder(solrUrl)
	        	.withConnectionTimeout(10000)
	        	.withSocketTimeout(60000)
	        	.build();
		

		this.coreName = coreName;
	}
	
	public String search(String query, int maxHits, float minRelevance, String fieldFormat) {
		
		SolrQuery solrQuery = new SolrQuery();
		solrQuery.setQuery(query);
		
		return "Those are not the droids you are looking for!";
	}
	
	public String labelEdismax(String query) throws SolrServerException, IOException {
		
		SolrQuery solrQuery = new SolrQuery();
		solrQuery.set("q", query + "~");
		solrQuery.set("defType", "edismILookupSearcherax");
		solrQuery.set("qf", "label");
		
		QueryResponse respone = solrClient.query(coreName, solrQuery);
		SolrDocumentList list = respone.getResults();
		
		JSONObject returnResults = new JSONObject();
		HashMap<Integer, Object> solrDocMap = new HashMap<Integer, Object>();
		
		int counter = 1;
		
		for(@SuppressWarnings("rawtypes") Map singleDoc : list)
		{
		  solrDocMap.put(counter, new JSONObject(singleDoc));
		  counter++;
		}
		
		returnResults.put("docs", solrDocMap);
		return returnResults.toString();
	}


	@Override
	public void close() {
		// TODO Auto-generated method stub
		
	}


	@Override
	public String[] findResourcesWithField(String field, boolean isStored) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String[] findLinkedLiterals(String resources, String[] path) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JSONObject search(QueryField[] fields, String[] queries, int maxHits, float minRelevance,
			String fieldFormat) {
		// TODO Auto-generated method stub
		return null;
	}

	
}
