package org.dbpedia.lookup.config;

import java.io.File;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Configuration loaded from a YAML Document. Please refer to the configuration documentation
 * at https://github.com/dbpedia/lookup-application
 * @author Jan Forberg
 *
 */
public class QueryConfig {

	public static final String CONFIG_FIELD_FORMAT_XML = "XML";
	
	public static final String CONFIG_FIELD_FORMAT_JSON = "JSON";

	public static final String CONFIG_FIELD_FORMAT_JSON_FULL = "JSON_FULL";

	public static final String CONFIG_FIELD_FORMAT_JSON_RAW = "JSON_RAW";

	public static final String CONFIG_FIELD_TYPE_NUMERIC = "numeric";
	
	public static final String CONFIG_FIELD_TYPE_STORED = "stored";
	
	public static final String CONFIG_FIELD_TYPE_STRING = "string";
	
	public static final String CONFIG_FIELD_TYPE_TEXT = "text";
	
	private String version;

	private String indexPath;

	private float exactMatchBoost;

	private QueryField[] queryFields;
	
	private float prefixMatchBoost;
	
	private float fuzzyMatchBoost;
	
	private int fuzzyEditDistance;
			
	private int fuzzyPrefixLength;
			
	private int maxResults;

	private int maxResultsCap;
	
	private float minRelevanceScore;
	
	private String boostFormula;
	
	private String format;
	
	private String formatTemplate;

	public String getIndexPath() {
		return indexPath;
	}

	public void setIndexPath(String indexPath) {
		this.indexPath = indexPath;
	}

	public float getExactMatchBoost() {
		return exactMatchBoost;
	}

	public void setExactMatchBoost(float exactMatchBoost) {
		this.exactMatchBoost = exactMatchBoost;
	}

	public QueryField[] getQueryFields() {
		return queryFields;
	}

	public void setQueryFields(QueryField[] queryFields) {
		this.queryFields = queryFields;
	}

	public float getPrefixMatchBoost() {
		return prefixMatchBoost;
	}

	public void setPrefixMatchBoost(float prefixMatchBoost) {
		this.prefixMatchBoost = prefixMatchBoost;
	}

	public float getFuzzyMatchBoost() {
		return fuzzyMatchBoost;
	}

	public void setFuzzyMatchBoost(float fuzzyMatchBoost) {
		this.fuzzyMatchBoost = fuzzyMatchBoost;
	}

	public int getFuzzyEditDistance() {
		return fuzzyEditDistance;
	}

	public void setFuzzyEditDistance(int fuzzyEditDistance) {
		this.fuzzyEditDistance = fuzzyEditDistance;
	}

	public int getFuzzyPrefixLength() {
		return fuzzyPrefixLength;
	}

	public void setFuzzyPrefixLength(int fuzzyPrefixLength) {
		this.fuzzyPrefixLength = fuzzyPrefixLength;
	}

	public int getMaxResults() {
		return maxResults;
	}

	public void setMaxResults(int maxResults) {
		this.maxResults = maxResults;
	}

	public String getBoostFormula() {
		return boostFormula;
	}

	public void setBoostFormula(String boostFormula) {
		this.boostFormula = boostFormula;
	}

	public float getMinRelevanceScore() {
		return minRelevanceScore;
	}

	public void setMinRelevanceScore(float minRelevanceScore) {
		this.minRelevanceScore = minRelevanceScore;
	}

	public String getFormat() {
		return format;
	}

	public void setFormat(String format) {
		this.format = format;
	}

	public String getFormatTemplate() {
		return formatTemplate;
	}

	public void setFormatTemplate(String formatTemplate) {
		this.formatTemplate = formatTemplate;
	}

	public int getMaxResultsCap() {
		return maxResultsCap;
	}

	public void setMaxResultsCap(int maxResultsCap) {
		this.maxResultsCap = maxResultsCap;
	}

	public String getVersion() {
		return version;
	}


	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * Loads the XML Configuration from file
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws Exception 
	 */
	public static QueryConfig Load(String path) throws Exception {
		
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		
		QueryConfig config = mapper.readValue(new File(path), QueryConfig.class);
  
		return config;
	}
}

