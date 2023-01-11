package org.dbpedia.lookup;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
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



public class JettyLookupServer {

    private static final String CLI_OPT_CONFIG_PATH = "c";

    private static final String CLI_OPT_CONFIG_PATH_LONG = "config";

    private static final String CLI_OPT_CONFIG_PATH_HELP = "The path of the application configuration file.";

    private static Server server;

    private static org.slf4j.Logger log;

    public static void main(String[] args) throws Exception {

        log = LoggerFactory.getLogger(JettyLookupServer.class);
        String configPath = null;

        Options options = new Options();
        options.addOption(CLI_OPT_CONFIG_PATH,
                CLI_OPT_CONFIG_PATH_LONG, true,
                CLI_OPT_CONFIG_PATH_HELP);

        CommandLineParser cmdParser = new DefaultParser();

        try {

            CommandLine cmd = cmdParser.parse(options, args);

            if (cmd.hasOption(CLI_OPT_CONFIG_PATH)) {
                configPath = cmd.getOptionValue(CLI_OPT_CONFIG_PATH);
            }

        } catch (org.apache.commons.cli.ParseException e1) {
            e1.printStackTrace();
        }

        server = new Server(8082);

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        ServletHolder hodler = handler.addServletWithMapping(LookupServlet.class, "/api/search/*");
        hodler.setInitParameter(LookupServlet.CONFIG_PATH, configPath);
        server.start();

        watchIndex(configPath);

        log.info("EXITING. STOPPING SERVER.");
        server.stop();
    }

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
