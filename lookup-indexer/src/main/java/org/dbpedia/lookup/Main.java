package org.dbpedia.lookup;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The main class for index creation
 * 
 * @author Jan Forberg
 *
 */
public class Main {

	private static final String CLI_OPT_CONFIG_PATH = "c";

	private static final String CLI_OPT_CONFIG_PATH_LONG = "config";

	private static final String CLI_OPT_CONFIG_PATH_HELP = "The path of the application configuration file.";

	private static final String CLI_OPT_RESOURCE = "r";

	private static final String CLI_OPT_RESOURCE_LONG = "resource";

	private static final String CLI_OPT_RESOURCE_HELP = "The URI to the resource";

	private static final String CLI_OPT_RESOURCE_FILE = "f";

	private static final String CLI_OPT_RESOURCE_FILE_PATH = "resource file";

	private static final String CLI_OPT_RESOURCE_FILE_HELP = "The path to the resource file";

	private static Logger logger;

	/**
	 * Run this method to create a lucene index
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		long time = System.currentTimeMillis();

		logger = LoggerFactory.getLogger(Main.class);

		IndexMode mode = IndexMode.BUILD_MEM;
		String configPath = null;

		Options options = new Options();
		options.addOption(CLI_OPT_CONFIG_PATH, CLI_OPT_CONFIG_PATH_LONG, true, CLI_OPT_CONFIG_PATH_HELP);
		options.addOption(CLI_OPT_RESOURCE, CLI_OPT_RESOURCE_LONG, true, CLI_OPT_RESOURCE_HELP);
		options.addOption(CLI_OPT_RESOURCE_FILE, CLI_OPT_RESOURCE_FILE_PATH, true, CLI_OPT_RESOURCE_FILE_HELP);

		CommandLineParser cmdParser = new DefaultParser();
		String resourceURI = null;

		try {

			CommandLine cmd = cmdParser.parse(options, args);

			if (cmd.hasOption(CLI_OPT_CONFIG_PATH)) {
				configPath = cmd.getOptionValue(CLI_OPT_CONFIG_PATH);
			}
			
			if (cmd.hasOption(CLI_OPT_RESOURCE_FILE)) {
				resourceURI = cmd.getOptionValue(CLI_OPT_RESOURCE_FILE_PATH);
			}

			if (cmd.hasOption(CLI_OPT_RESOURCE)) {
				resourceURI = cmd.getOptionValue(CLI_OPT_RESOURCE);
			}

		} catch (org.apache.commons.cli.ParseException e1) {
			e1.printStackTrace();
		}

		if (configPath == null) {
			logger.info("No config file found - exiting.");
			return;
		}

		logger.info("=====================================================================");
		logger.info("Indexer started with the following parameters:");
		logger.info("=====================================================================");
		logger.info("CONFIG PATH:\t\t" + configPath);

		try {

			// Load the configuration file
			final IndexConfig indexConfig = IndexConfig.Load(configPath);
			logger.info("=====================================================================");
			logger.info("Configuration loaded...");
			logger.info("=====================================================================");

			try {
				mode = Enum.valueOf(IndexMode.class, indexConfig.getIndexMode());
			} catch (Exception e) {
				logger.info("Index mode not specified, index mode has been set to NONE.");
				mode = IndexMode.NONE;
			}

			logger.info("CLEAN INDEX:\t\t" + indexConfig.isCleanIndex());
			logger.info("INDEX MODE:\t\t" + mode);

			// Log the configuration file
			String contents = new String(Files.readAllBytes(Paths.get(configPath)));
			logger.info(contents);

			for (IndexField field: indexConfig.getIndexFields()) {
				String query = field.getQuery();
				String resourceName = field.getResourceName();

				String valuesClause = "";
				if (resourceURI != null) {
					valuesClause = String.format("VALUES ?%1$s { <%2$s> }", resourceName, resourceURI);
				}

				query = query.replace("%VALUES%", valuesClause);
				field.setQuery(query);
			}

			// Initialize ARQ
			ARQ.init();

			switch (mode) {
				case BUILD_MEM:
					logger.info("Index mode BUILD_MEM has been selected.");
					buildMem(indexConfig);
					break;
				case BUILD_DISK:
					logger.info("Index mode BUILD_DISK has been selected.");
					buildDisk(indexConfig);
					break;
				case INDEX_DISK:
					logger.info("Index mode INDEX_DISK has been selected.");
					indexDisk(indexConfig);
					break;
				case INDEX_SPARQL:
					logger.info("Index mode INDEX_SPARQL has been selected.");
					indexSparql(indexConfig);
					break;
				case NONE:
					logger.info("Index mode NONE has been selected. Done!");
					break;
			}

		} catch (Exception e) {

			e.printStackTrace(System.err);
			logger.info("=====================================================================");
			logger.info("Indexing failed.");
			logger.info("=====================================================================");
			return;
		}

		long elapsed = System.currentTimeMillis() - time;

		logger.info("Index created in " + String.format("%d min, %d sec",
				TimeUnit.MILLISECONDS.toMinutes(elapsed),
				TimeUnit.MILLISECONDS.toSeconds(elapsed) -
						TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))));

		logger.info("=====================================================================");
		logger.info("Done indexing.");
		logger.info("=====================================================================");
	}

	private static void indexSparql(IndexConfig xmlConfig) {

		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig, logger);

		for (IndexField indexFieldConfig : xmlConfig.getIndexFields()) {

			logger.info("=====================================================================");
			logger.info("Indexing path for field '" + indexFieldConfig.getFieldName() + "' and resource '"
					+ indexFieldConfig.getResourceName() + "'");
			logger.info("=====================================================================");

			try (QueryExecution qexec = QueryExecutionFactory.sparqlService(xmlConfig.getSparqlEndpoint(),
					indexFieldConfig.getQuery())) {
				ResultSet results = qexec.execSelect();

				lookupIndexer.indexResult(results, indexFieldConfig);
				lookupIndexer.commit();
			}
		}

		lookupIndexer.finish();
	}

	/**
	 * Creates the index by building a data set in memory.
	 * Once the data set is build, the indexable key-value pairs are fetched via
	 * SPARQL queries
	 * and sent to the Lucene indexer.
	 * 
	 * @param dataPath   the path of the data to load
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig  the configuration file used for indexing
	 */
	private static void buildMem(IndexConfig xmlConfig) {
		Dataset dataset = DatasetFactory.create();

		File[] filesToLoad = getFilesToLoad(xmlConfig.getDataPath());

		if (filesToLoad == null || filesToLoad.length == 0) {
			logger.info("Nothing to load. Exiting.");
			return;
		}

		logger.info("=====================================================================");
		logger.info("Loading data at " + xmlConfig.getDataPath() + " to in-memory dataset.");
		logger.info("=====================================================================");

		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {
			for (File file : filesToLoad) {
				logger.info("Loading file " + file.getPath() + "...");
				conn.load(file.getPath());
			}
		}

		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig, logger);
		indexData(dataset, lookupIndexer, xmlConfig);
	}

	/**
	 * Creates the index by building a TDB2 data set on disk using a parallel bulk
	 * loader.
	 * Once the data set is build, the indexable key-value pairs are fetched via
	 * SPARQL queries
	 * and sent to the Lucene indexer.
	 * 
	 * @param dataPath   the path of the data to load
	 * @param tdbPath    the path of the TDB2 helper structure
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig  the configuration file used for indexing
	 */
	private static void buildDisk(IndexConfig xmlConfig) {

		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(xmlConfig.getTdbPath());

		// Clear the graph on disk
		datasetGraph.begin(ReadWrite.WRITE);
		datasetGraph.clear();
		datasetGraph.commit();
		datasetGraph.end();

		logger.info("Fetching files...");

		// Fetch the files to load
		File[] filesToLoad = getFilesToLoad(xmlConfig.getDataPath());

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
		logger.info("Loading data at " + xmlConfig.getDataPath());
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
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig, logger);
		indexData(DatasetFactory.wrap(datasetGraph), lookupIndexer, xmlConfig);
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
	private static void indexDisk(IndexConfig indexConfig) {

		logger.info("Connecting to TDB2 data set graph...");

		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(indexConfig.getDataPath());

		// Do the indexing
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(indexConfig, logger);
		indexData(DatasetFactory.wrap(datasetGraph), lookupIndexer, indexConfig);
	}

	private static void indexData(Dataset dataset, LuceneLookupIndexer lookupIndexer, IndexConfig indexConfig) {

		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {

			for (IndexField indexFieldConfig : indexConfig.getIndexFields()) {

				logger.info("=====================================================================");
				logger.info("Indexing path for field '" + indexFieldConfig.getFieldName() + "' and resource '"
						+ indexFieldConfig.getResourceName() + "'");
				logger.info("=====================================================================");

				Txn.executeRead(conn, () -> {
					Query query = QueryFactory.create(indexFieldConfig.getQuery());
					ResultSet result = conn.query(query).execSelect();

					logger.info("Iterator created. Indexing...");

					long time = System.currentTimeMillis();
					lookupIndexer.indexResult(result, indexFieldConfig);
					long elapsed = System.currentTimeMillis() - time;

					logger.info("Path indexed in " + String.format("%d min, %d sec",
							TimeUnit.MILLISECONDS.toMinutes(elapsed),
							TimeUnit.MILLISECONDS.toSeconds(elapsed) -
									TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))));
				});

				lookupIndexer.commit();
				dataset.close();
			}

			lookupIndexer.finish();
			lookupIndexer.test();
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
