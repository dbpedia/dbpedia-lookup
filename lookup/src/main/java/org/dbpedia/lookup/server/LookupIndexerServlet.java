package org.dbpedia.lookup.server;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import org.apache.lucene.index.IndexWriter;
import org.dbpedia.lookup.config.IndexConfig;
import org.dbpedia.lookup.indexer.LookupIndexer;
import org.dbpedia.lookup.searcher.LookupSearcher;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LookupIndexerServlet extends HttpServlet {
	
	final static Logger logger = LoggerFactory.getLogger(LookupIndexerServlet.class);

	final static String PATH_RUN = "/run";

	final static String PATH_CLEAR = "/clear";
	
	final static String FORM_FIELD_VALUES = "values";

	final static String FORM_FIELD_CONFIG = "config";

	private IndexWriter indexWriter;

	private LookupIndexer indexer;

	private LookupSearcher searcher;

	@Override
	public void init() throws ServletException {
		indexer = (LookupIndexer)getServletContext().getAttribute("INDEXER");
		searcher = (LookupSearcher)getServletContext().getAttribute("SEARCHER");
	}

	@Override
	public void destroy() {
		try {
			indexWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		super.destroy();
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		String pathInfo = req.getPathInfo();

		if(PATH_RUN.equals(pathInfo)) {
			doRun(req, res);
		}

		if(PATH_CLEAR.equals(pathInfo)) {
			doClear(req, res);
		}
	}

	private void doClear(HttpServletRequest req, HttpServletResponse res) {
		indexer.clear();
		searcher.refresh();
		res.setStatus(HttpServletResponse.SC_OK);
	}

	private void doRun(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
		// Set up the temporary directory for storing uploaded files
		Path tempDirectory = Files.createTempDirectory("multipart");
		MultipartConfigElement multipartConfigElement = new MultipartConfigElement(tempDirectory.toString());

		// Set the multipart configuration for the request
		req.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);

		// Get the files from the multipart form
		Collection<Part> parts = req.getParts();

		IndexConfig indexConfig = null;
		String[] values = null;

		// Process files
		for (Part part : parts) {
			String partName = part.getName();
			byte[] partBytes = part.getInputStream().readAllBytes();
			String partValue = new String(partBytes, java.nio.charset.StandardCharsets.UTF_8);

			System.out.println("Form Field: " + part.getName());
			System.out.println("Form Value: " + partValue);

			if(FORM_FIELD_CONFIG.equals(partName)) {
				indexConfig = IndexConfig.FromString(partValue);
			}

			if(FORM_FIELD_VALUES.equals(partName)) {
				values = partValue.split(",");
			}
		}

		indexer.run(indexConfig, values);
		searcher.refresh();

		// Respond to the request
		res.setStatus(HttpServletResponse.SC_OK);
		return;
	}


	

	

}
