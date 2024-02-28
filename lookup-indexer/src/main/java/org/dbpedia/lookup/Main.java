package org.dbpedia.lookup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.dbpedia.lookup.config.IndexConfig;
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

	private static final String CLI_OPT_VALUES = "v";

	private static final String CLI_OPT_VALUES_LONG = "values";

	private static final String CLI_OPT_VALUES_HELP = "White space separated values that will be inserted into the passed SPARQL queries";

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

		// Parse command line interface parameters
		Options options = new Options();
		options.addOption(CLI_OPT_CONFIG_PATH, CLI_OPT_CONFIG_PATH_LONG, true, CLI_OPT_CONFIG_PATH_HELP);
		options.addOption(CLI_OPT_VALUES, CLI_OPT_VALUES_LONG, true, CLI_OPT_VALUES_HELP);

		CommandLineParser cmdParser = new DefaultParser();

		// Initialize vars and try to fill from CLI
		String configPath = null;
		String[] values = null;

		try {

			CommandLine cmd = cmdParser.parse(options, args);

			if (cmd.hasOption(CLI_OPT_CONFIG_PATH)) {
				configPath = cmd.getOptionValue(CLI_OPT_CONFIG_PATH);
			}

			if (cmd.hasOption(CLI_OPT_VALUES)) {
				String valuesString = cmd.getOptionValue(CLI_OPT_VALUES);

				if (valuesString != null) {
					values = valuesString.split(" ");
				}

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
			
			// Log the configuration file
			String contents = new String(Files.readAllBytes(Paths.get(configPath)));
			logger.info(contents);
			
			// Load the configuration file
			final IndexConfig indexConfig = IndexConfig.Load(configPath);
			logger.info("=====================================================================");
			logger.info("Configuration loaded...");
			logger.info("=====================================================================");
			
			LookupIndexer indexer = new LookupIndexer(logger, indexConfig);
			indexer.run(values);

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
}
