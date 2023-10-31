package org.dbpedia.lookup;

import org.json.JSONObject;

public interface ILookupSearcher {

	JSONObject search(QueryField[] fields, String[] queries, int maxHits, float minRelevance, String fieldFormat, String join);

	String[] findResourcesWithField(String field, boolean isStored);

	String[] findLinkedLiterals(String resources, String[] path);

	
	void close();
}
