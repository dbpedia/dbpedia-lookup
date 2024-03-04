package org.dbpedia.lookup;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.dbpedia.lookup.config.LookupConfig;
import org.dbpedia.lookup.config.LookupField;
import org.dbpedia.lookup.indexer.LookupIndexer;
import org.dbpedia.lookup.indexer.NGramAnalyzer;
import org.dbpedia.lookup.indexer.StringPhraseAnalyzer;
import org.dbpedia.lookup.indexer.UriAnalyzer;
import org.dbpedia.lookup.searcher.LookupSearcher;
import org.dbpedia.lookup.server.LookupIndexerServlet;
import org.dbpedia.lookup.server.LookupSearcherServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Run to start a jetty server that hosts the lookup servlet and makes it
 * accessible
 * via HTTP
 */
public class Main {

	final static Logger logger = LoggerFactory.getLogger(LookupIndexerServlet.class);

    public static final String CONFIG_PATH = "configpath";

    private static final String CLI_OPT_CONFIG = "c";

    private static final String CLI_OPT_CONFIG_LONG = "config";

    private static final String CLI_OPT_CONFIG_HELP = "The path of the application configuration file.";

    private static final String CLI_OPT_HOME = "h";

    private static final String CLI_OPT_HOME_LONG = "home";

    private static final String CLI_OPT_HOME_HELP = "The path of the index.html";

    private static final String CLI_OPT_PORT = "p";

    private static final String CLI_OPT_PORT_LONG = "port";

    private static final String CLI_OPT_PORT_HELP = "The port that will be used to access the lookup servlet";

    private static Server server;

    private static Logger log;

    /**
     * Run to start a jetty server that hosts the lookup servlet and makes it
     * accessible
     * via HTTP
     * 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        log = LoggerFactory.getLogger(Server.class);
        String configPath = null;
        String resourceBasePath = null;
        int port = 8082;

        Options options = new Options();
        options.addOption(CLI_OPT_CONFIG, CLI_OPT_CONFIG_LONG, true, CLI_OPT_CONFIG_HELP);
        options.addOption(CLI_OPT_HOME, CLI_OPT_HOME_LONG, true, CLI_OPT_HOME_HELP);
        options.addOption(CLI_OPT_PORT, CLI_OPT_PORT_LONG, true, CLI_OPT_PORT_HELP);

        CommandLineParser cmdParser = new DefaultParser();

        try {

            CommandLine cmd = cmdParser.parse(options, args);

            if (cmd.hasOption(CLI_OPT_CONFIG)) {
                configPath = cmd.getOptionValue(CLI_OPT_CONFIG);
            }

            if (cmd.hasOption(CLI_OPT_HOME)) {
                resourceBasePath = cmd.getOptionValue(CLI_OPT_HOME);
            }

            if (cmd.hasOption(CLI_OPT_PORT)) {
                String portString = cmd.getOptionValue(CLI_OPT_PORT);
                port = Integer.parseInt(portString);
            }

        } catch (org.apache.commons.cli.ParseException e1) {
            e1.printStackTrace();
        }

        if (configPath == null) {
            log.error("No config specified");
            return;
        }
        LookupConfig lookupConfig = null;
		
        try {
            lookupConfig = LookupConfig.Load(configPath);
        } catch (Exception e) {
            log.error("Unable to load the config file:");
            log.error(e.getMessage());
            return;
        }

        if (resourceBasePath == null) {
            File configFile = new File(configPath);
            resourceBasePath = configFile.getParent().toString();
        }
       
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(createAnalyzer(lookupConfig));
        indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
        indexWriterConfig.setMaxBufferedDocs(lookupConfig.getMaxBufferedDocs());

        String indexPath = lookupConfig.getIndexPath();
        File indexFile = new File(indexPath);

        if (!indexFile.isAbsolute()) {
            File configFile = new File(configPath);
            String configDirectory = configFile.getParent();
            indexPath = configDirectory + "/" + lookupConfig.getIndexPath();
            lookupConfig.setIndexPath(indexPath);
        }

        File file = new File(indexPath);
        file.mkdirs();

        FSDirectory targetDirectory = FSDirectory.open(Paths.get(indexPath));

        IndexWriter indexWriter = new IndexWriter(targetDirectory, indexWriterConfig);
        LookupIndexer indexer = new LookupIndexer(logger, lookupConfig, indexWriter);
        LookupSearcher searcher = new LookupSearcher(lookupConfig);

        server = new Server(port);

        // Take care of CORS requests
        ServletContextHandler context = new ServletContextHandler();

        context.setAttribute("INDEXER", indexer);
        context.setAttribute("SEARCHER", searcher);

        context.setContextPath("/");
        context.setWelcomeFiles(new String[] { "index.html" });
        context.setResourceBase(resourceBasePath);

        FilterHolder cors = context.addFilter(CrossOriginFilter.class, "/*", EnumSet.of(DispatcherType.REQUEST));
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

        // Create a servlet handler and holder for the API and pass the config
        ServletHolder searchServletHolder = context.addServlet(LookupSearcherServlet.class, "/api/search/*");
        searchServletHolder.setInitParameter(CONFIG_PATH, configPath);

        // Create a servlet handler and holder for the API and pass the config
        ServletHolder indexServletHolder = context.addServlet(LookupIndexerServlet.class, "/api/index/*");
        indexServletHolder.setInitParameter(CONFIG_PATH, configPath);

        ServletHolder def = new ServletHolder("default", DefaultServlet.class);
        def.setInitParameter("resourceBase", resourceBasePath);
        def.setInitParameter("dirAllowed", "false");
        context.addServlet(def, "/");

        // Add handlers for the index html and the API servlet
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { context });
        server.setHandler(handlers);
        server.start();

        boolean running = true;
        
        while (running) {
            Thread.sleep(1000);
            continue;
        }

        log.info("EXITING. STOPPING SERVER.");
        server.stop();
    }

    /**
     * Creates a watch service that watches for changes in the index directory. Any
     * change in the index
     * structure will trigger a restart of the jetty server and thus a refresh of
     * the index searcher.
     * 
     * @param configPath
     * @throws Exception
     * @throws IOException
     * @throws InterruptedException
  
    private static void watchIndex(String configPath) throws Exception, IOException, InterruptedException {
        LookupConfig queryConfig = LookupConfig.Load(configPath);

        WatchService watcher = FileSystems.getDefault().newWatchService();

        File indexDirectoryFile = new File(queryConfig.getIndexPath());
        indexDirectoryFile.mkdirs();

        Path indexDirectory = Paths.get(queryConfig.getIndexPath());
        indexDirectory.register(watcher,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        while (true) {

            Thread.sleep(1000);
            WatchKey key = watcher.poll();

            if (key == null) {
                continue;
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();

                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }

                server.stop();
                server.start();
                break;
            }

            if (!key.isValid()) {
                log.info("key is INVALID!");
            }

            key.reset();
        }

    }   */

    private static Analyzer createAnalyzer(LookupConfig config) {

		Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();

		for (LookupField field : config.getLookupFields()) {

			String fieldType = field.getType();

			if (fieldType == null) {
				continue;
			}

			if (fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_STRING)) {
				analyzerPerField.put(field.getName(), new StringPhraseAnalyzer());
			}

			if(fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_NGRAM)) {
				analyzerPerField.put(field.getName(), new NGramAnalyzer());
			}

			if(fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_URI)) {
				analyzerPerField.put(field.getName(), new UriAnalyzer());
			}
		}

		Analyzer analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
		return analyzer;
	}
}
