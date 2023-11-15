package org.dbpedia.lookup;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

/**
 * Run to start a jetty server that hosts the lookup servlet and makes it accessible
 * via HTTP
 */
public class JettyLookupServer {

    private static final String CLI_OPT_CONFIG = "c";

    private static final String CLI_OPT_CONFIG_LONG = "config";

    private static final String CLI_OPT_CONFIG_HELP = "The path of the application configuration file.";
  
    private static final String CLI_OPT_HOME = "h";

    private static final String CLI_OPT_HOME_LONG = "home";

    private static final String CLI_OPT_HOME_HELP = "The path of the index.html";

    private static final String CLI_OPT_RESOURCE_BASE_PATH_DEFAULT = "./webapp";

    private static final String CLI_OPT_PORT = "p";

    private static final String CLI_OPT_PORT_LONG = "port";

    private static final String CLI_OPT_PORT_HELP = "The port that will be used to access the lookup servlet";

    private static Server server;

    private static org.slf4j.Logger log;

    /**
     * Run to start a jetty server that hosts the lookup servlet and makes it accessible
     * via HTTP
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {

        log = LoggerFactory.getLogger(JettyLookupServer.class);
        String configPath = null;
        String resourceBasePath = CLI_OPT_RESOURCE_BASE_PATH_DEFAULT;
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

            if(cmd.hasOption(CLI_OPT_PORT)) {
                String portString = cmd.getOptionValue(CLI_OPT_PORT);
                port = Integer.parseInt(portString);
            }

        } catch (org.apache.commons.cli.ParseException e1) {
            e1.printStackTrace();
        }

        if(configPath == null) {
            log.error("No config specified");
            return;
        }

        try {
            QueryConfig.Load(configPath);
        } catch(Exception e) {
            log.error("Unable to load the config file:");
            log.error(e.getMessage());
        }

        server = new Server(port);

        // Create a servlet handler and holder for the API and pass the config
        ServletHandler searchHandler = new ServletHandler();
        ServletHolder hodler = searchHandler.addServletWithMapping(LookupServlet.class, "/api/search/*");
        hodler.setInitParameter(LookupServlet.CONFIG_PATH, configPath);

        // Index page setup
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(false);
        resourceHandler.setWelcomeFiles(new String[] { "index.html" });
        resourceHandler.setResourceBase(resourceBasePath);
    
        // Add handlers for the index html and the API servlet
        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resourceHandler, searchHandler });
        server.setHandler(handlers);
        server.start();

        watchIndex(configPath);

        log.info("EXITING. STOPPING SERVER.");
        server.stop();
    }

    /**
     * Creates a watch service that watches for changes in the index directory. Any change in the index
     * structure will trigger a restart of the jetty server and thus a refresh of the index searcher.
     * @param configPath
     * @throws Exception
     * @throws IOException
     * @throws InterruptedException
     */
    private static void watchIndex(String configPath) throws Exception, IOException, InterruptedException {
        QueryConfig queryConfig = QueryConfig.Load(configPath);
        
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
         
    }
}
