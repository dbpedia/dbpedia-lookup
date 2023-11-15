package org.dbpedia.lookup.config;

public class IndexField {
	
	private String fieldName;
		
	private String documentVariable;
	
	private String query;
	
	private String type;

	public String getFieldName() {
		return fieldName;
	}

	public void setFieldName(String fieldName) {
		this.fieldName = fieldName;
	}

	public String getDocumentVariable() {
		return documentVariable;
	}

	public void setDocumentVariable(String documentVariable) {
		this.documentVariable = documentVariable;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
