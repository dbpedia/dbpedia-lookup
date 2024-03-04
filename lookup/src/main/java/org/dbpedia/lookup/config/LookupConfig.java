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
public class LookupConfig {

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

	private LookupField[] lookupFields;
	
	private float prefixMatchBoost;
	
	private float fuzzyMatchBoost;
	
	private int fuzzyEditDistance;
			
	private int fuzzyPrefixLength;
			
	private int maxResults;
	
	private int logInterval;

	public int getLogInterval() {
		return logInterval;
	}

	public void setLogInterval(int logInterval) {
		this.logInterval = logInterval;
	}

	private int maxBufferedDocs;

	public int getMaxBufferedDocs() {
		return maxBufferedDocs;
	}

	public void setMaxBufferedDocs(int maxBufferedDocs) {
		this.maxBufferedDocs = maxBufferedDocs;
	}

	private int maxResultsCap;
	
	private float minScore;
	
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

	public LookupField[] getLookupFields() {
		return lookupFields;
	}

	public void setLookupFields(LookupField[] queryFields) {
		this.lookupFields = queryFields;
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

	public float getMinScore() {
		return minScore;
	}

	public void setMinScore(float minScore) {
		this.minScore = minScore;
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
	public static LookupConfig Load(String path) throws Exception {
		
		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		
		LookupConfig config = mapper.readValue(new File(path), LookupConfig.class);
  
		return config;
	}

    public LookupConfig copy() {
        return null;
    }
}

