package org.dbpedia.lookup;

import java.io.IOException;

public interface ILookupIndexer {

	/**
	 * Indexes resource with a literal in a field
	 * @param resource
	 * @param field 
	 * @param literal
	 * @throws IOException
	 */
	void indexField(String resource, String field, String literal, boolean isTokenized);
	
	
	/**
	 * Indexes resource as a relation
	 * @param resource
	 * @param relation
	 * @param object
	 * @throws IOException
	 */
	void indexRelation(String resource, String relation, String object);
	
	/**
	 * Increases the refCount field of a resource
	 * @param resource
	 */
	void increaseRefCount(String resource);
	
	/**
	 * Writes the queued changes to the index
	 */
	void commit();
	
	/**
	 * Clears the entire index
	 * @return
	 */
	boolean clearIndex();
}
