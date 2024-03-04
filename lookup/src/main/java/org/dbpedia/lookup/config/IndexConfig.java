package org.dbpedia.lookup.config;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
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

	private IndexMode indexMode;

	private String tdbPath;

	private String dataPath;

	private String sparqlEndpoint;

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

	public IndexMode getIndexMode() {
		return indexMode;
	}

	public void setIndexMode(IndexMode indexMode) {
		this.indexMode = indexMode;
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
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws Exception
	 */
	public static IndexConfig Load(String path) throws JsonParseException, JsonMappingException, IOException {

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

		IndexConfig config = mapper.readValue(new File(path), IndexConfig.class);

		return config;
	}

	/**
	 * Loads the XML Configuration from file
	 * 
	 * @param path The path of the file
	 * @return True if the configuration has been loaded correctly, false otherwise
	 * @throws IOException 
	 * @throws JsonMappingException 
	 * @throws JsonParseException 
	 * @throws Exception
	 */
	public static IndexConfig FromString(String content) throws JsonParseException, JsonMappingException, IOException {

		ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
		IndexConfig config = mapper.readValue(content, IndexConfig.class);
		return config;
	}
}
