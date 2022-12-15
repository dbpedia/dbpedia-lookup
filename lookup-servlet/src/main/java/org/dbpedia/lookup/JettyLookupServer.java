package org.dbpedia.lookup;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import static java.nio.file.StandardWatchEventKinds.*;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;

public class JettyLookupServer {

    private static final String CLI_OPT_CONFIG_PATH = "c";

    private static final String CLI_OPT_CONFIG_PATH_LONG = "config";

    private static final String CLI_OPT_CONFIG_PATH_HELP = "The path of the application configuration file.";

    private static Server server;

    public static void main(String[] args) throws Exception {

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

        QueryConfig queryConfig = QueryConfig.Load(configPath);
        WatchService watcher = FileSystems.getDefault().newWatchService();

        Path indexDirectory = Paths.get(queryConfig.getIndexPath());
        indexDirectory.register(watcher,
                ENTRY_CREATE,
                ENTRY_DELETE,
                ENTRY_MODIFY);


        System.out.println("WAITING FOR INDEX CHANGES.");

        while (true) {

            Thread.sleep(1000);
            WatchKey key;
            key = watcher.poll();

            if(key == null) {
                continue;
            }

            for (WatchEvent<?> event: key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
        
                if (kind == OVERFLOW) {
                    continue;
                }

                System.out.println("INDEX CHANGED - RESTARTING SERVER");
                server.stop();
                server.start();
                System.out.println("SERVER UP AND READY.");
                break;
            }
        
            boolean valid = key.reset();

            if(!valid) {
                break;
            }
        }

        System.out.println("EXITING. STOPPING SERVER.");
        server.stop();
    }
}
