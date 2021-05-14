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
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.ParseException;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
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
import org.dbpedia.lookup.impl.lucene.LuceneLookupIndexer;
import org.dbpedia.lookup.logging.ConsoleLogger;
import org.slf4j.Logger;

/**
 * The main class for index creation
 * @author Jan Forberg
 *
 */
public class IndexMain {
	
	enum IndexMode {
		BUILD_MEM,
		BUILD_DISK,
		INDEX_DISK,
		INDEX_SPARQL,
		NONE
	}

	public static final String DEFAULT_CONFIG_PATH = "/root/app-config.yml";
	
	private static final String DEFAULT_DATA_PATH = "./data";

	private static final String DEFAULT_TDB_PATH = "./tdb";
	
	private static final String CLI_OPT_DATA_PATH_LONG = "data";
	
	private static final String CLI_OPT_DATA_PATH_HELP = "The path of the RDF data to load.";
	
	private static final String CLI_OPT_TDB_PATH_LONG = "tdb";
	
	private static final String CLI_OPT_TDB_PATH_HELP = "The path of the tdb2 database structure.";
	
	private static final String CLI_OPT_CONFIG_PATH_LONG = "config";
	
	private static final String CLI_OPT_CONFIG_PATH_HELP = "The path of the application configuration file.";
	
	private static final String CLI_OPT_CLEAN_INDEX = "clean";

	private static final String CLI_OPT_CLEAN_INDEX_HELP = "When set, the already existing index will be cleared.";
	
	private static final String CLI_OPT_INDEX_MODE = "mode";

	private static final String CLI_OPT_INDEX_MODE_HELP = "The index mode. One of [BUILD_MEM, BUILD_DISK, INDEX_DISK, NONE].";
	
	private static Logger logger;
	
	/**
	 * Run this method to create a lucene index
	 * @param args
	 * @throws IOException
	 */
	public static void main( String[] args ) throws IOException
	{
		long time = System.currentTimeMillis();
		
		logger = new ConsoleLogger();
	
		IndexMode mode = IndexMode.BUILD_MEM;
		String dataPath = DEFAULT_DATA_PATH;
		String configPath = DEFAULT_CONFIG_PATH;
		String tdbPath = DEFAULT_TDB_PATH;
		boolean cleanIndex = false;
		
		Options options = new Options();
		options.addOption(CLI_OPT_DATA_PATH_LONG, CLI_OPT_DATA_PATH_LONG, true, CLI_OPT_DATA_PATH_HELP);
		options.addOption(CLI_OPT_CONFIG_PATH_LONG, CLI_OPT_CONFIG_PATH_LONG, true, CLI_OPT_CONFIG_PATH_HELP);
		options.addOption(CLI_OPT_TDB_PATH_LONG, CLI_OPT_TDB_PATH_LONG, true, CLI_OPT_TDB_PATH_HELP);
		options.addOption(CLI_OPT_CLEAN_INDEX, CLI_OPT_CLEAN_INDEX,  true, CLI_OPT_CLEAN_INDEX_HELP);
		options.addOption(CLI_OPT_INDEX_MODE, CLI_OPT_INDEX_MODE, true, CLI_OPT_INDEX_MODE_HELP);

		CommandLineParser cmdParser = new DefaultParser();
		
		try {

			CommandLine cmd = cmdParser.parse(options, args);

			if(cmd.hasOption(CLI_OPT_DATA_PATH_LONG)) {
				dataPath = cmd.getOptionValue(CLI_OPT_DATA_PATH_LONG);
			}

			if(cmd.hasOption(CLI_OPT_CONFIG_PATH_LONG)) {
				configPath = cmd.getOptionValue(CLI_OPT_CONFIG_PATH_LONG);
			}
			
			if(cmd.hasOption(CLI_OPT_TDB_PATH_LONG)) {
				tdbPath = cmd.getOptionValue(CLI_OPT_TDB_PATH_LONG);
			}

		} catch (org.apache.commons.cli.ParseException e1) {
			e1.printStackTrace();
		}
		
		logger.info("=====================================================================");
		logger.info("Indexer started with the following parameters:");
		logger.info("=====================================================================");
		logger.info("DATA PATH:\t\t" + dataPath);
		logger.info("CONFIG PATH:\t\t" + configPath);
		logger.info("TDB PATH:\t\t" + tdbPath);
		
		try {

			// Load the configuration file
			final LookupConfig xmlConfig = LookupConfig.Load(configPath);
			logger.info("=====================================================================");
			logger.info("Configuration loaded...");
			logger.info("=====================================================================");
		
			try {
				mode = Enum.valueOf(IndexMode.class, xmlConfig.getIndexConfig().getIndexMode());
			} catch(Exception e) {
				logger.info("Index mode not specified, index mode has been set to NONE.");
				mode = IndexMode.NONE;
			}
		
			logger.info("CLEAN INDEX:\t\t" + xmlConfig.getIndexConfig().isCleanIndex());
			logger.info("INDEX MODE:\t\t" + mode);
			
			// Log the configuration file
			String contents = new String(Files.readAllBytes(Paths.get(configPath))); 
			logger.info(contents);
			
			switch(mode) {	
				case BUILD_MEM:
					logger.info("Index mode BUILD_MEM has been selected.");
					buildMem(dataPath, xmlConfig);
					break;
				case BUILD_DISK:
					logger.info("Index mode BUILD_DISK has been selected.");
					buildDisk(dataPath, tdbPath, xmlConfig);
					break;
				case INDEX_DISK:
					logger.info("Index mode INDEX_DISK has been selected.");
					indexDisk(tdbPath, xmlConfig);
					break;
				case INDEX_SPARQL:
					logger.info("Index mode INDEX_SPARQL has been selected.");
					indexSparql(xmlConfig);
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
			    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))
			));
		
		logger.info("=====================================================================");
		logger.info("Done indexing.");
		logger.info("=====================================================================");
		
	}

	
	private static void indexSparql(LookupConfig xmlConfig) {
		
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig.getIndexConfig(), logger);
		
		for(IndexField indexFieldConfig : xmlConfig.getIndexConfig().getIndexFields()) {	
			
			logger.info("=====================================================================");
			logger.info("Indexing path for field '" + indexFieldConfig.getFieldName() + "' and resource '" + indexFieldConfig.getResourceName() + "'");
			logger.info("=====================================================================");
				
			 try (QueryExecution qexec = QueryExecutionFactory.sparqlService(xmlConfig.getIndexConfig().getSparqlEndpoint(),
					 indexFieldConfig.getQuery())) {
			    ResultSet results = qexec.execSelect();
				
			    lookupIndexer.indexResult(results, indexFieldConfig);
				lookupIndexer.commit();
			 }

		}	
	}
	
	private static String query(String endpoint, String query) throws ParseException, IOException {
		
		HttpClient client = HttpClientBuilder.create().build();
		
		String body = "default-graph-uri=&format=application%2Fsparql-results%2Bjson&query=" + query;
		
		HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
		HttpPost request = new HttpPost(endpoint);
		request.setEntity(entity);
		request.setHeader("Content-type", "application/x-www-form-urlencoded");
		// request.addHeader("Accept",  accept);
		HttpResponse response = client.execute(request);
		HttpEntity responseEntity = response.getEntity();
		
		if(responseEntity != null) {
		    return EntityUtils.toString(responseEntity);
		}
		return null;

	}


	/**
	 * Creates the index by building a data set in memory.
	 * Once the data set is build, the indexable key-value pairs are fetched via SPARQL queries
	 * and sent to the Lucene indexer.
	 * @param dataPath the path of the data to load
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig the configuration file used for indexing
	 */
	private static void buildMem(String dataPath, LookupConfig xmlConfig) {
		Dataset dataset = DatasetFactory.create(); 
			
		File[] filesToLoad = getFilesToLoad(dataPath);

		if(filesToLoad == null || filesToLoad.length == 0) {
			logger.info("Nothing to load. Exiting.");
			return;
		}
		
		logger.info("=====================================================================");
		logger.info("Loading data at " + dataPath + " to in-memory dataset.");
		logger.info("=====================================================================");
	
		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {
			for(File file : filesToLoad) {
				logger.info("Loading file " + file.getPath() + "...");
				conn.load(file.getPath());
			}
		}
		
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig.getIndexConfig(), logger);
		indexData(dataset, lookupIndexer, xmlConfig);
	}

	/**
	 * Creates the index by building a TDB2 data set on disk using a parallel bulk loader.
	 * Once the data set is build, the indexable key-value pairs are fetched via SPARQL queries
	 * and sent to the Lucene indexer.
	 * @param dataPath the path of the data to load
	 * @param tdbPath the path of the TDB2 helper structure
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig the configuration file used for indexing
	 */
	private static void buildDisk(String dataPath, String tdbPath, LookupConfig xmlConfig) {
		
		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(tdbPath);
		
		// Clear the graph on disk
		datasetGraph.begin(ReadWrite.WRITE);
		datasetGraph.clear();
		datasetGraph.commit();
		datasetGraph.end();

		logger.info("Fetching files...");
		
		// Fetch the files to load
		File[] filesToLoad = getFilesToLoad(dataPath);

		if(filesToLoad == null || filesToLoad.length == 0) {
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
		logger.info("Loading data at " + dataPath);
		logger.info("=====================================================================");
		
		// Load with the parallel bulk loader to the TDB2 structure on disk
		for(File file : filesToLoad) {
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
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig.getIndexConfig(), logger);
		indexData(DatasetFactory.wrap(datasetGraph), lookupIndexer, xmlConfig);
	}
	
	/**
	 * Creates the index by using an existing TDB2 data set on disk.
	 * The indexable key-value pairs are fetched via SPARQL queries
	 * and sent to the Lucene indexer.
	 * @param tdbPath the path of the TDB2 helper structure
	 * @param cleanIndex indicates whether to clean the Lucene index before indexing
	 * @param xmlConfig the configuration file used for indexing
	 */
	private static void indexDisk(String tdbPath, LookupConfig xmlConfig) {
			
		logger.info("Connecting to TDB2 data set graph...");
		
		// Connect to TDB2 graph on disk
		DatasetGraph datasetGraph = DatabaseMgr.connectDatasetGraph(tdbPath);
		
		// Do the indexing
		LuceneLookupIndexer lookupIndexer = new LuceneLookupIndexer(xmlConfig.getIndexConfig(), logger);
		indexData(DatasetFactory.wrap(datasetGraph), lookupIndexer, xmlConfig);
	}


	private static void indexData(Dataset dataset, LuceneLookupIndexer lookupIndexer, LookupConfig xmlConfig) {
			
		for(IndexField indexFieldConfig : xmlConfig.getIndexConfig().getIndexFields()) {	
			
			
		}
		
		
		
		
		try (RDFConnection conn = RDFConnectionFactory.connect(dataset)) {
			
			for(IndexField indexFieldConfig : xmlConfig.getIndexConfig().getIndexFields()) {	
				
				logger.info("=====================================================================");
				logger.info("Indexing path for field '" + indexFieldConfig.getFieldName() + "' and resource '" + indexFieldConfig.getResourceName() + "'");
				logger.info("=====================================================================");
					
				
				
				Txn.executeRead(conn, ()-> {
					Query query = QueryFactory.create(indexFieldConfig.getQuery());
					ResultSet result = conn.query(query).execSelect();
				
					logger.info("Iterator created. Indexing...");
					
					long time = System.currentTimeMillis();
					lookupIndexer.indexResult(result, indexFieldConfig);
					long elapsed = System.currentTimeMillis() - time;
					
					logger.info("Path indexed in " + String.format("%d min, %d sec", 
						    TimeUnit.MILLISECONDS.toMinutes(elapsed),
						    TimeUnit.MILLISECONDS.toSeconds(elapsed) - 
						    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(elapsed))
						));
				});
				
				
				
				lookupIndexer.commit();
				dataset.close();
			}
			
			lookupIndexer.test();
			
			
		}
	}

	private static File[] getFilesToLoad(String dataPath) {
		File dataFile = new File(dataPath);
		File[] filesToLoad = new File[0];

		if(dataFile.isDirectory()) {
			filesToLoad = new File(dataPath).listFiles(new RDFFileFilter());
		} else {
			boolean accept = false;
			String fileName = dataFile.getName();
			accept |= fileName.contains(".ttl");
			accept |= fileName.contains(".turtle");
			accept |= fileName.contains(".n3");
			accept |= fileName.contains(".nt");

			if(accept) {
				filesToLoad = new File[] { dataFile };
			}
		}
		return filesToLoad;
	}
}
