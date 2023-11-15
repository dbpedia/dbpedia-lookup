package org.dbpedia.lookup.config;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Configuration loaded from a YAML Document. Please refer to the configuration
 * documentation
 * at https://github.com/dbpedia/lookup-application
 * 
 * @author Jan Forberg
 *
 */

public class IndexConfig {

	private String version;

	private List<IndexField> indexFields;

	private String indexPath;

	private String indexMode;

	private String tdbPath;

	private String dataPath;

	private String sparqlEndpoint;

	private boolean cleanIndex;

	private int cacheSize;

	private int commitInterval;

	public String getTdbPath() {
		return tdbPath;
	}

	public void setTdbPath(String tdbPath) {
		this.tdbPath = tdbPath;
	}

	public String getDataPath() {
		return dataPath;
	}

	public void setDataPath(String dataPath) {
		this.dataPath = dataPath;
	}

	public String getVersion() {
		return version;
	}

	public void setVersion(String version) {
		this.version = version;
	}

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

	/**
	 * Loads the XML Configuration from file
	 * 
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws Exception
	 */
	public static IndexConfig Load(String path) throws Exception {

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

		IndexConfig config = mapper.readValue(new File(path), IndexConfig.class);

		return config;
	}

}
