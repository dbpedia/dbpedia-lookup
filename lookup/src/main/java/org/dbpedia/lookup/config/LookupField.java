package org.dbpedia.lookup.config;

public class LookupField {
		
	private float weight;

	private String name;

	private String type;
	
	private String[] aliases;
	
	private boolean highlight;
	
	private boolean queryByDefault;

	private boolean isExact;

	private boolean isRequired;

	private boolean tokenize;
	
	private boolean allowPartialMatch;

	public boolean tokenize() { return tokenize; }

	public void setTokenize(boolean tokenize) { this.tokenize = tokenize; }

	public boolean isHighlight() {
		return highlight;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
	
	public boolean isExact() { return isExact; }

	public void setExact(boolean isExact) { this.isExact = isExact; }

	public void setHighlight(boolean highlight) {
		this.highlight = highlight;
	}

	public float getWeight() {
		return weight;
	}

	public void setWeight(float weight) {
		this.weight = weight;
	}

	

	public String getName() {
		return name;
	}

	public void setName(String fieldName) {
		this.name = fieldName;
	}

	public boolean isQueryByDefault() {
		return queryByDefault;
	}

	public void setQueryByDefault(boolean queryByDefault) {
		this.queryByDefault = queryByDefault;
	}

	public boolean isRequired() {
		return isRequired;
	}

	public void setRequired(boolean isRequired) {
		this.isRequired = isRequired;
	}

	/**
	 * Creates a copy of this query field config object
	 * @return
	 */
	public LookupField copy() {
		LookupField copy = new LookupField();
		copy.name = this.name;
		copy.weight = this.weight;
		copy.isRequired = this.isRequired;
		copy.queryByDefault = this.queryByDefault;
		copy.highlight = this.highlight;
		copy.isExact = this.isExact;
		copy.tokenize = this.tokenize;
		copy.aliases = this.aliases;
		copy.allowPartialMatch = this.allowPartialMatch;
		return copy;
	}

	public LookupField() {
		this.tokenize = true;
	}

	public String[] getAliases() {
		return aliases;
	}

	public void setAliases(String[] aliases) {
		this.aliases = aliases;
	}

	public boolean isAllowPartialMatch() {
		return allowPartialMatch;
	}

	public void setAllowPartialMatch(boolean allowPartialMatch) {
		this.allowPartialMatch = allowPartialMatch;
	}

}
