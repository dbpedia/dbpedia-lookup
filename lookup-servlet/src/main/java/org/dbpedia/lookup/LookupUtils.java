package org.dbpedia.lookup;

import org.apache.jena.graph.Node;

public class LookupUtils {

	/**
	 * Safely converts a Jena node into a string
	 * @param object
	 * @return
	 */
	public static String nodeToString(Node object) {

		if(object == null) {
			return "";
		}
		
		if(object.isLiteral()) {
			return object.getLiteralValue().toString();
		} 

		if(object.isURI()) {
			return object.getURI().toString();
		}

		return "";

	}
}
