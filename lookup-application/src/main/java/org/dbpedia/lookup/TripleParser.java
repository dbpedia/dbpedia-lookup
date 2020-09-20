package org.dbpedia.lookup;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.jena.graph.Triple;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.StreamRDF;
import org.apache.jena.sparql.core.Quad;
import org.slf4j.Logger;

public abstract class TripleParser {
	
	private static String LOG_MESSAGE_READING_FROM = "Reading from ";
	
	private static String LOG_MESSAGE_FORMAT_NOT_SUPPORTED = "The dataset query contained files of an unsupported format: ";
	
	private static String LOG_MESSAGE_SKIPPING = "Skipping this file...";
	
	/**
	 * Called whenever the parse
	 * @param triple
	 */
	public abstract void emitTriple(Triple triple);

	/**
	 * Parses triples from a list of RDF files
	 * @param files
	 * @param logger
	 * @throws IOException
	 */
	public void parseTriples(File[] files, Logger logger) throws IOException {
		
		if(files == null)
			return;
	
		StreamRDF inputHandler = new StreamRDF() {

			public void start() {}

			public void quad(Quad arg0) {}

			public void prefix(String arg0, String arg1) {}

			public void finish() {}

			public void base(String arg0) {}

			public void triple(Triple arg0) {
				emitTriple(arg0);
			}
		};

		for(File file : files) {
			
			if(logger != null)
				logger.info(LOG_MESSAGE_READING_FROM + file.getName());
	
			Lang language = getLanguageFromFileExtension(file);
			
			if(isLanguageSupported(language)) {

				BufferedInputStream in = new BufferedInputStream(new FileInputStream(file));
				BZip2CompressorInputStream bzipIn = new BZip2CompressorInputStream(in);
	
				try
				{
					RDFParser.create()
					.source(bzipIn)
					.lang(RDFLanguages.N3)
					.parse(inputHandler);
					
					bzipIn.close();
	
				} catch(RiotException e) {
					if(logger != null)
						logger.error(e.getMessage());
				}
			} else {
				
				if(logger != null) {
					
					logger.warn(LOG_MESSAGE_FORMAT_NOT_SUPPORTED +  file.getName());
					logger.info(LOG_MESSAGE_SKIPPING);
				
				}
			}
		}
	}

	/**
	 * Checks whether the language is supported by the parser
	 * @param language
	 * @return
	 */
	private boolean isLanguageSupported(Lang language) {
		return language == RDFLanguages.TTL || language == RDFLanguages.N3;
	}


	/**
	 * Checks the file extension and returns a Jena language
	 * @param file
	 * @return
	 */
	private Lang getLanguageFromFileExtension(File file) {
		
		if(file.getName().contains(".ttl") || file.getName().contains(".turtle")) {
			return RDFLanguages.TTL;
		}
		
		if(file.getName().contains(".nt") || file.getName().contains(".n3")) {
			return RDFLanguages.N3;
		}
		
		return null;
	}
}
