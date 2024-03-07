package org.dbpedia.lookup.server;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Hashtable;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.zookeeper.common.Time;
import org.dbpedia.lookup.Main;
import org.dbpedia.lookup.RequestUtils;
import org.dbpedia.lookup.config.LookupConfig;
import org.dbpedia.lookup.config.LookupField;
import org.dbpedia.lookup.config.QuerySettings;
import org.dbpedia.lookup.searcher.LookupSearcher;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LookupSearcherServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	private static final String VERSION_TAG = "v0.001";

	public static final String PATH_REFRESH = "/refresh";

	public static final String QUERY_SUFFIX_WEIGHT = "Weight";

	public static final String QUERY_SUFFIX_REQUIRED = "Required";

	public static final String QUERY_SUFFIX_EXACT = "Exact";

	public static final String QUERY_SUFFIX_TOKENIZE = "Tokenize";

	public static final String QUERY_SUFFIX_HIGHLIGHT = "Highlight";

	public static final String QUERY_SUFFIX_ALLOW_PARTIAL_MATCH = "AllowPartialMatch";

	private LookupSearcher searcher;

	private LookupConfig queryConfig;

	private Transformer xformer;

	public static final String CONFIG_PATH = "configpath";

	private static final String[] PARAM_QUERY = { "QueryString", "query" };

	private static final String PARAM_JOIN = "join";

	private String initializationError;

	final static Logger logger = LoggerFactory.getLogger(LookupSearcherServlet.class);

	@Override
	public void init() throws ServletException {

		System.out.println("Initializing Lookup Searcher " + VERSION_TAG);
		searcher = (LookupSearcher)getServletContext().getAttribute("SEARCHER");

		String configPath = getInitParameter(Main.CONFIG_PATH);
		try {
			queryConfig = LookupConfig.Load(configPath);

			for(LookupField field : queryConfig.getLookupFields()) {
				System.out.println(field.getName() + " -- " + field.getType() + " -- " + field.getWeight());
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		TransformerFactory transformerFactory = new net.sf.saxon.TransformerFactoryImpl();

		if (queryConfig.getFormatTemplate() != null) {

			try {
				Templates formatTemplate = transformerFactory.newTemplates(new StreamSource(
						new FileInputStream(queryConfig.getFormatTemplate())));

				xformer = formatTemplate.newTransformer();
			} catch (TransformerConfigurationException | FileNotFoundException e1) {
				// this is logged to catalina.out
				e1.printStackTrace();
			}
		}
	}

	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doPostOrGet(req, resp);
	}

	@Override
	protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		if(PATH_REFRESH.equals(req.getPathInfo())) {
			System.out.println("Refreshing index searcher");
			this.searcher.refresh();
			return;
		}

		doPostOrGet(req, resp);
	}

	/**
	 * Handler function for any post or get request
	 * 
	 * @param req
	 * @param resp
	 * @throws ServletException
	 * @throws IOException
	 */
	private void doPostOrGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

		if (initializationError != null) {
			throw new ServletException("The initialization of the servlet failed: " + initializationError);
		}

		String query = RequestUtils.getStringParameter(req, PARAM_QUERY, null);
		String join = RequestUtils.getStringParameter(req, PARAM_JOIN, null);

		QuerySettings settings = new QuerySettings(queryConfig);
		settings.parse(req);

		Hashtable<LookupField, String> queryMap = createQueryMap(req, query);

		logger.info("Search; " + req.getQueryString() + "; " + Time.currentWallTime() + ";");
		JSONObject result = searcher.search(settings, queryMap, join);

		if(result == null) {
			throw new ServletException("The index has not been created yet.");
		}

		if (settings.getFormat().equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_XML)) {

			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/xml");
			PrintWriter out = resp.getWriter();

			try {
				out.print(formatXml(result));
				out.close();
			} catch (TransformerException e) {
				throw new IOException(e.getMessage());
			}
		} else {
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/json");
			PrintWriter out = resp.getWriter();
			out.println(result.toString());
			out.close();
		}

	}

	private String formatXml(JSONObject result) throws TransformerException {

		String generatedXml = "<results>" + XML.toString(result) + "</results>";

		if (xformer == null) {
			return generatedXml;
		}

		StringWriter writer = new StringWriter();

		Source source = new StreamSource(new java.io.StringReader(generatedXml));
		Result target = new StreamResult(writer);
		xformer.transform(source, target);

		return writer.toString();
	}

	/**
	 * Create a map of query fields, each field pointing to a String value (the
	 * searched value)
	 * 
	 * @param req
	 * @param query
	 * @return
	 */
	private Hashtable<LookupField, String> createQueryMap(HttpServletRequest req, String query) {

		Hashtable<LookupField, String> result = new Hashtable<LookupField, String>();

		LookupField[] queryFields = queryConfig.getLookupFields();

		for (int i = 0; i < queryFields.length; i++) {

			LookupField queryField = queryFields[i].copy();

			String fieldRequired = req.getParameter(queryField.getName() + QUERY_SUFFIX_REQUIRED);

			if (fieldRequired != null) {
				queryField.setRequired(Boolean.parseBoolean(fieldRequired));
			}

			String fieldExact = req.getParameter(queryField.getName() + QUERY_SUFFIX_EXACT);

			if (fieldExact != null) {
				queryField.setExact(Boolean.parseBoolean(fieldExact));
			}

			String fieldTokenize = req.getParameter(queryField.getName() + QUERY_SUFFIX_TOKENIZE);

			if (fieldTokenize != null) {
				queryField.setTokenize(Boolean.parseBoolean(fieldTokenize));
			}

			String fieldHighlight = req.getParameter(queryField.getName() + QUERY_SUFFIX_HIGHLIGHT);

			if (fieldHighlight != null) {
				queryField.setHighlight(Boolean.parseBoolean(fieldHighlight));
			}

			String fieldAllowPartialMatch = req
					.getParameter(queryField.getName() + QUERY_SUFFIX_ALLOW_PARTIAL_MATCH);

			if (fieldAllowPartialMatch != null) {
				queryField.setAllowPartialMatch(Boolean.parseBoolean(fieldAllowPartialMatch));
			}

			String fieldWeight = req.getParameter(queryField.getName() + QUERY_SUFFIX_WEIGHT);

			if (fieldWeight != null) {
				try {
					float weight = Float.parseFloat(fieldWeight);
					queryField.setWeight(weight);
				} catch (Exception e) {
					/* Ignore */ }
			}

			if (query != null && queryField.isQueryByDefault()) {
				result.put(queryField, query);
			}

			String fieldQuery = req.getParameter(queryField.getName());
			
			if (fieldQuery != null) {
				try {
					fieldQuery = URLDecoder.decode(fieldQuery, "UTF-8");	
					result.put(queryField, fieldQuery);
				} catch (UnsupportedEncodingException e) {
					result.put(queryField, fieldQuery);
				}
				continue;
			}

			if (queryField.getAliases() != null) {
				for (String alias : queryField.getAliases()) {
					String aliasQuery = req.getParameter(alias);

					if (aliasQuery != null) {
						result.put(queryField, aliasQuery);
						break;
					}
				}
			}
		}

		return result;
	}

}
