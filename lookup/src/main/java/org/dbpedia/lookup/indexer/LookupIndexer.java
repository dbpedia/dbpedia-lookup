package org.dbpedia.lookup.indexer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.jena.query.ARQ;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFactory;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.system.Txn;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.loader.DataLoader;
import org.apache.jena.tdb2.loader.LoaderFactory;
import org.apache.jena.tdb2.loader.base.MonitorOutput;
import org.apache.lucene.index.IndexWriter;
import org.dbpedia.lookup.RDFFileFilter;
import org.dbpedia.lookup.config.IndexConfig;
import org.dbpedia.lookup.config.IndexField;
import org.dbpedia.lookup.config.LookupConfig;
import org.slf4j.Logger;

/**
 * The main class for index creation
 * 
 * @author Jan Forberg
 *
 */
public class LookupIndexer {

	private Logger logger;

    private LuceneIndexWriter indexWriter;

	private static final String VALUES_PLACEHOLDER_STRING = "#VALUES#";

	private static final String VALUES_CLAUSE_TEMPLATE = "VALUES ?%1$s { %2$s }";

	public static final String ERROR_INVALID_INDEX_MODE = "Unkown specified index mode, exiting. Use either BUILD_MEM, BUILD_DISK, INDEX_DISK or INDEX_SPARQL_ENDPOINT.";

	public LookupIndexer(Logger logger, LookupConfig config, IndexWriter indexWriter) throws IOException {
		this.logger = logger;
		this.indexWriter = new LuceneIndexWriter(logger, indexWriter, config);
	}

	public LookupIndexer(Logger logger, IndexConfig config) {
		this.logger = logger;
		// indexWriter = new LuceneIndexWriter(logger, config.getIndexPath(), config.getMaxBufferedDocs());
	}

	public void run(IndexConfig config, String[] values) throws IOException {
		
		try {
			// Update queries based on URIs specified in the CLI
			updateQueriesForResources(values, config);

			// Initialize ARQ
			ARQ.init();

			// Switch over the index mode to execute on of the following methods:
			switch (config.getIndexMode()) {
				case INDEX_IN_MEMORY:
					indexInMemory(config);
					break;
				case BUILD_AND_INDEX_ON_DISK:
					buildDisk(config);
					break;
				case INDEX_ON_DISK:
					indexDisk(config);
					break;
				case INDEX_SPARQL_ENDPOINT:
					indexSparqlEndpoint(config);
					break;
			}
			
			indexWriter.commit();
			indexWriter.finish();

		} catch (Exception e) {
			indexWriter.cleanUp();
			throw (e);
		}
	}

	public void clear() {
		indexWriter.clear();
	}

	public Logger getLogger() {
		return logger;
	}

	/**
	 * If one or more resources are specified via the CLI, all queries will be
	 * updated to only
	 * query for key-value pairs for those specific resources. This is achived by
	 * inserting a
	 * VALUES clause into each query which restricts the key to the set of specified
	 * URIs
	 * 
	 * @param resourceURI
	 * @param indexConfig
	 */
	private void updateQueriesForResources(String[] values, final IndexConfig indexConfig) {
		// Create the values string. Any occurance of the the string %VALUES% will be
		// replaced
		// with the values string before querying
		String valuesURIString = "";

		// The values string will be created from the whitespace separated URIs passed
		// as the
		// resourceURI argument (-r)
		if (values != null) {

			for (int i = 0; i < values.length; i++) {
				valuesURIString += "<" + values[i] + "> ";
			}
		}

		for (IndexField field : indexConfig.getIndexFields()) {
			String query = field.getQuery();
			String resourceName = field.getDocumentVariable();
			String valuesClause = "";

			if (values != null) {
				valuesClause = String.format(VALUES_CLAUSE_TEMPLATE, resourceName, valuesURIString);
			}

			query = query.replace(VALUES_PLACEHOLDER_STRING, valuesClause);
			logger.info("Updated Query: " + query);
			field.setQuery(query);
		}
	}

	/**
	 * Indexing based on an already existing SPARQL endpoint
	 */
	private void indexSparqlEndpoint(IndexConfig config) {

		
		// Iterate over all index fields
		for (IndexField indexFieldConfig : config.getIndexFields()) {

			logger.info("=====================================================================");
			logger.info("Indexing field '" + indexFieldConfig.getFieldName() + "'");
			logger.info("=====================================================================");

			try (QueryExecution qexec = QueryExecutionFactory.sparqlService(config.getSparqlEndpoint(),
					indexFieldConfig.getQuery())) {

				// Query endpoint and get results (key-value pairs)
				org.apache.jena.query.ResultSet results = qexec.execSelect();

				// Send to indexer
				indexWriter.indexResult(results, indexFieldConfig);
			}
		}

	}

	/**
	 * Creates the index by building an Apache Jena dataset in memory.
	 * Once the dataset is built, the indexable key-value pairs are fetched via
	 * SPARQL queries and sent to the Lucene indexer. This method is only
	 * recommended for indexing smaller files
	 * as it uses a considerable amount of RAM
	 * 
	 * @param dataPath   The path of the data to load
	 * @param cleanIndex Indicates whether to delete any existing Lucene index in
	 *                   the target folder before indexing
	 * @param xmlConfig  The configuration file used for indexing
	 */
	private void indexInMemory(IndexConfig config) {
		Dataset dataset = DatasetFactory.create();

		// Find the files to load in the specified data path
		File[] filesToLoad = getFilesToLoad(config.getDataPath());

		if (filesToLoad == null || filesToLoad.length == 0) {
			logger.info("Nothing to load. Exiting.");
			return;
		}

		logger.info("=====================================================================");
		logger.info("Loading data at " + config.getDataPath() + " to in-memory dataset.");
		logger.info("=====================================================================");

		// Load to in-memory triple store
		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {
			for (File file : filesToLoad) {
				logger.info("Loading file " + file.getPath() + "...");
				conn.load(file.getPath());
			}
		}

		indexData(dataset, config);
	}

	/**
	 * Creates the index by building a TDB2 data set on disk using a parallel bulk
	 * loader.
	 * Once the data set is build, the indexable key-value pairs are fetched via
	 * SPARQL queries and sent to the Lucene indexer.
	 * 
	 * @param dataPath   the path of the data to load
	 * @param tdbPath    the path of the TDB2 on-disk structure
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig  the configuration file used for indexing
	 */
	private void buildDisk(IndexConfig config) {

		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(config.getTdbPath());

		// Clear the graph on disk
		datasetGraph.begin(ReadWrite.WRITE);
		datasetGraph.clear();
		datasetGraph.commit();
		datasetGraph.end();

		logger.info("Fetching files...");

		// Fetch the files to load
		File[] filesToLoad = getFilesToLoad(config.getDataPath());

		if (filesToLoad == null || filesToLoad.length == 0) {
			logger.info("Nothing to load. Exiting.");
			return;
		}

		logger.info("Creating bulk loader...");

		// Create a parallel loader
		DataLoader loader = LoaderFactory.parallelLoader(datasetGraph, new MonitorOutput() {
			@Override
			public void print(String fmt, Object... args) {
				logger.info(String.format(fmt, args));
			}
		});

		logger.info("=====================================================================");
		logger.info("Loading data at " + config.getDataPath());
		logger.info("=====================================================================");

		// Load with the parallel bulk loader to the TDB2 structure on disk
		for (File file : filesToLoad) {
			loader.startBulk();
			try {
				logger.info("Loading file " + file.getPath() + "...");
				loader.load(file.getPath());
				loader.finishBulk();
			} catch (RuntimeException ex) {
				ex.printStackTrace();
				loader.finishException(ex);
				throw ex;
			}
		}

		// Do the indexing
		indexData(DatasetFactory.wrap(datasetGraph), config);
	}

	/**
	 * Creates the index by using an existing TDB2 data set on disk.
	 * The indexable key-value pairs are fetched via SPARQL queries
	 * and sent to the Lucene indexer.
	 * 
	 * @param tdbPath    the path of the TDB2 helper structure
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig  the configuration file used for indexing
	 */
	private void indexDisk(IndexConfig config) {

		logger.info("Connecting to TDB2 data set graph...");

		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(config.getDataPath());

		// Do the indexing
		indexData(DatasetFactory.wrap(datasetGraph), config);
	}

	/**
	 * indexConfig
	 * Used by all dataset based indexing methods. Runs the configured queries
	 * against the the loaded
	 * Datasets and passes the resulting key-value results to the lookup indexer
	 * 
	 * @param dataset
	 * @param lookupIndexer
	 * @param indexConfig
	 */
	private void indexData(Dataset dataset, IndexConfig config) {

		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {

			for (IndexField indexFieldConfig : config.getIndexFields()) {

				logger.info("=====================================================================");
				logger.info("Indexing path for field '" + indexFieldConfig.getFieldName() + "'");
				logger.info("=====================================================================");

				Txn.executeRead(conn, () -> {
					Query query = QueryFactory.create(indexFieldConfig.getQuery());
					ResultSet result = conn.query(query).execSelect();

					logger.info("Iterator created. Indexing...");

					long time = System.currentTimeMillis();
					indexWriter.indexResult(result, indexFieldConfig);
					long elapsed = System.currentTimeMillis() - time;

					logger.info("Path indexed in " + String.format("%d min, %d sec",
							TimeUnit.MILLISECONDS.toMinutes(elapsed),
							TimeUnit.MILLISECONDS.toSeconds(elapsed) -
									TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))));
				});

				dataset.close();
			}
		}
	}

	private static File[] getFilesToLoad(String dataPath) {
		File dataFile = new File(dataPath);
		File[] filesToLoad = new File[0];

		if (dataFile.isDirectory()) {
			filesToLoad = new File(dataPath).listFiles(new RDFFileFilter());
		} else {
			boolean accept = false;
			String fileName = dataFile.getName();
			accept |= fileName.contains(".ttl");
			accept |= fileName.contains(".turtle");
			accept |= fileName.contains(".n3");
			accept |= fileName.contains(".nt");

			if (accept) {
				filesToLoad = new File[] { dataFile };
			}
		}
		return filesToLoad;
	}

	
}
