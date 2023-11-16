package org.dbpedia.lookup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
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
import org.dbpedia.lookup.config.QueryConfig;
import org.dbpedia.lookup.config.QueryField;
import org.dbpedia.lookup.config.QuerySettings;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HTTP Servlet with handlers for the single lookup API request "/api/search"
 * Post-processes requests and sends the query to the LuceneLookupSearcher,
 * which translates requests into lucene queries
 */
public class LookupServlet extends HttpServlet {

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;

	public static final String QUERY_SUFFIX_WEIGHT = "Weight";

	public static final String QUERY_SUFFIX_REQUIRED = "Required";

	public static final String QUERY_SUFFIX_EXACT = "Exact";

	public static final String QUERY_EXACT_MATCH_BOOST = "exactMatchBoost";

	public static final String QUERY_PREFIX_MATCH_BOOST = "prefixMatchBoost";

	private LuceneLookupSearcher searcher;

	private QueryConfig queryConfig;

	private Transformer xformer;

	public static final String CONFIG_PATH = "configpath";

	private static final String[] PARAM_QUERY = { "QueryString", "query" };

	private static final String[] PARAM_JOIN = { "join" };

	private String initializationError;

	final static Logger logger = LoggerFactory.getLogger(LookupServlet.class);

	@Override
	public void init() throws ServletException {

		try {

			initializationError = null;

			String configPath = getInitParameter(CONFIG_PATH);
			queryConfig = QueryConfig.Load(configPath);

			String indexPath = queryConfig.getIndexPath();
			File indexFile = new File(indexPath);

			if (!indexFile.isAbsolute()) {
				File configFile = new File(configPath);
				String configDirectory = configFile.getParent();
				indexPath = configDirectory + "/" + queryConfig.getIndexPath();
			}

			// Create the searcher that handles the search requests on the index structure
			searcher = new LuceneLookupSearcher(indexPath, queryConfig);

		} catch (Exception e) {
			// this is logged to catalina.out
			e.printStackTrace();
			logger.error(e.toString());
			initializationError = e.toString();
		}

		// If specified, the json outputs of the API can be transformed into XML
		// using a template. This has been implemented to support backwards
		// compatibility
		// with the ancient and long deprecated DBpedia Lookup app
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

		String query = getStringParamter(req, PARAM_QUERY, null);
		String join = getStringParamter(req, PARAM_JOIN, null);

		QuerySettings settings = new QuerySettings(queryConfig);
		settings.parse(req);

		Hashtable<QueryField, String> queryMap = createQueryMap(req, query);
		
		logger.info("Search; " + req.getQueryString() + "; " + Time.currentWallTime() + ";");

		JSONObject result = searcher.search(settings, queryMap, join);

		if (settings.getFormat().equalsIgnoreCase(QueryConfig.CONFIG_FIELD_FORMAT_XML)) {

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
	private Hashtable<QueryField, String> createQueryMap(HttpServletRequest req, String query) {

		Hashtable<QueryField, String> result = new Hashtable<QueryField, String>();

		QueryField[] queryFields = queryConfig.getQueryFields();

		for (int i = 0; i < queryFields.length; i++) {

			QueryField queryField = queryFields[i].copy();

			String fieldRequired = req.getParameter(queryField.getFieldName() + QUERY_SUFFIX_REQUIRED);

			if (fieldRequired != null) {
				queryField.setRequired(Boolean.parseBoolean(fieldRequired));
			}

			String fieldExact = req.getParameter(queryField.getFieldName() + QUERY_SUFFIX_EXACT);

			if (fieldExact != null) {
				queryField.setExact(Boolean.parseBoolean(fieldExact));
			}

			String fieldWeight = req.getParameter(queryField.getFieldName() + QUERY_SUFFIX_WEIGHT);

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

			String fieldQuery = req.getParameter(queryField.getFieldName());

			if (fieldQuery != null) {
				result.put(queryField, fieldQuery);
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

	private String getStringParamter(HttpServletRequest req, String[] keys, String defaultValue) {

		for (String key : keys) {

			String result = req.getParameter(key);

			if (result != null) {
				return result;
			}
		}

		return defaultValue;
	}

	

}
