package org.dbpedia.lookup;

import java.io.File;
import java.util.List;

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

	private QueryConfig queryConfig;
	
	private IndexConfig indexConfig;
	
	public QueryConfig getQueryConfig() {
		return queryConfig;
	}



	public void setQueryConfig(QueryConfig queryConfig) {
		this.queryConfig = queryConfig;
	}



	public IndexConfig getIndexConfig() {
		return indexConfig;
	}



	public void setIndexConfig(IndexConfig indexConfig) {
		this.indexConfig = indexConfig;
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

	

	public static class IndexConfig {
	
		private List<IndexField> indexFields;
		
		private String indexPath;
		
		private String indexMode;
		
		private String sparqlEndpoint;
		
		private boolean cleanIndex;
		
		private int cacheSize;
		
		private int commitInterval;
		
		public List<IndexField> getIndexFields() {
			return indexFields;
		}

		public void setIndexFields(List<IndexField> indexFields) {
			this.indexFields = indexFields;
		}

		public String getIndexPath() {
			return indexPath;
		}

		public void setIndexPath(String indexPath) {
			this.indexPath = indexPath;
		}

		public int getCacheSize() {
			return cacheSize;
		}

		public void setCacheSize(int cacheSize) {
			this.cacheSize = cacheSize;
		}

		public int getCommitInterval() {
			return commitInterval;
		}

		public void setCommitInterval(int commitInterval) {
			this.commitInterval = commitInterval;
		}

		public String getIndexMode() {
			return indexMode;
		}

		public void setIndexMode(String indexMode) {
			this.indexMode = indexMode;
		}

		public boolean isCleanIndex() {
			return cleanIndex;
		}

		public void setCleanIndex(boolean cleanIndex) {
			this.cleanIndex = cleanIndex;
		}

		public String getSparqlEndpoint() {
			return sparqlEndpoint;
		}

		public void setSparqlEndpoint(String sparqlEndpoint) {
			this.sparqlEndpoint = sparqlEndpoint;
		}
	}
	
	
	public static class QueryConfig {
		
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
	}
}

