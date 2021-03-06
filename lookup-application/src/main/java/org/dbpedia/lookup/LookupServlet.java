package org.dbpedia.lookup;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Hashtable;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Templates;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.zookeeper.common.Time;
import org.dbpedia.lookup.LookupConfig.QueryConfig;
import org.dbpedia.lookup.impl.lucene.LuceneLookupSearcher;
import org.json.JSONObject;
import org.json.XML;


public class LookupServlet extends HttpServlet
{

	/**
	 * 
	 */
	private static final long serialVersionUID = 102831973239L;
	
	public static final String QUERY_SUFFIX_WEIGHT = "Weight";
	
	public static final String QUERY_SUFFIX_REQUIRED = "Required";

	private LuceneLookupSearcher searcher;

	private LookupConfig config;

	private QueryConfig queryConfig;

	private Transformer xformer;

	
	private static final String[] PARAM_MAX_RESULTS = { "MaxHits", "maxResults" };
	
	private static final String[] PARAM_FORMAT = { "format" };
	
	private static final String[] PARAM_QUERY = { "QueryString", "query" };
	
	private static final String[] PARAM_MIN_RELEVANCE = { "minRelevance" };
	
	private String initializationError;
	
	final static Logger logger = LogManager.getLogger(LookupServlet.class);

	@Override
	public void init() throws ServletException {

		
		try {
			
			initializationError = null;
			
			config = LookupConfig.Load(IndexMain.DEFAULT_CONFIG_PATH);
			
			queryConfig = config.getQueryConfig();

			searcher = new LuceneLookupSearcher(config.getIndexConfig().getIndexPath(), config);
		} catch (Exception e) {
			
			// this is logged to catalina.out
			e.printStackTrace();
			logger.error(e.toString());
			
			initializationError = e.toString();
		} 	
		
        TransformerFactory transformerFactory = new net.sf.saxon.TransformerFactoryImpl();

         if(queryConfig.getFormatTemplate() != null) {
        	 
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

	private void doPostOrGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
		if(initializationError != null) {
			throw new ServletException("The initialization of the servlet failed: " + initializationError);
		}
		
		String query = getStringParamter(req, PARAM_QUERY, null);
		
		Hashtable<QueryField, String> queryMap = createQueryMap(req, query);
				
		int maxResults = getIntParamter(req, PARAM_MAX_RESULTS,  queryConfig.getMaxResults());
		
		if(queryConfig.getMaxResultsCap() > 0) {
			maxResults = Math.min(maxResults, queryConfig.getMaxResultsCap());
		}
		
		String format = getStringParamter(req, PARAM_FORMAT, queryConfig.getFormat());
		float minRelevance = getFloatParamter(req, PARAM_MIN_RELEVANCE, queryConfig.getMinRelevanceScore());
		
		if(format == null || format.equals("")) {
			format = LookupConfig.CONFIG_FIELD_FORMAT_XML;
		}
		
		logger.info("Search; " + req.getQueryString() + "; " + Time.currentWallTime() + ";");
		
		JSONObject result = searcher.search(queryMap, maxResults, minRelevance, format);
		
		if(format.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_XML)) {
			
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/xml");
			PrintWriter out = resp.getWriter();
			
			try {
				out.print(formatXml(result));
				out.close();
			} catch (TransformerException e) {
				throw new IOException(e.getMessage());
			}
		}
		else {
			resp.setCharacterEncoding("UTF-8");
			resp.setContentType("application/json");
			PrintWriter out = resp.getWriter();
			out.println(result.toString());
			out.close();
		}
		
	}

	private String formatXml(JSONObject result) throws TransformerException {
		
		String generatedXml = "<results>" + XML.toString(result) + "</results>";
		
		if(xformer == null) {
			return generatedXml;
		}
		
		StringWriter writer = new StringWriter();
		
		Source source = new StreamSource(new java.io.StringReader(generatedXml));
		Result target = new StreamResult(writer);
		xformer.transform(source, target);
		
		return writer.toString();
	}

	/**
	 * Create a map of query fields, each field pointing to a String value (the searched value)
	 * @param req
	 * @param query
	 * @return
	 */
	private Hashtable<QueryField, String> createQueryMap(HttpServletRequest req, String query) {
		
		Hashtable<QueryField, String> result = new Hashtable<QueryField, String>();
		
		QueryField[] queryFields = queryConfig.getQueryFields();
		
		for(int i = 0; i < queryFields.length; i++) {
			
			QueryField queryField = queryFields[i].copy();
			
			String fieldRequired = req.getParameter(queryField.getFieldName() + QUERY_SUFFIX_REQUIRED);
			
			if(fieldRequired != null) {				
				queryField.setRequired(Boolean.parseBoolean(fieldRequired));
			}
			
			String fieldWeight = req.getParameter(queryField.getFieldName() + QUERY_SUFFIX_WEIGHT);
			
			if(fieldWeight != null) {
				try {
					float weight = Float.parseFloat(fieldWeight);
					queryField.setWeight(weight);
				} catch(Exception e) { /* Ignore */ }
			}
			
			if(query != null && queryField.isQueryByDefault()) {
				result.put(queryField, query);
			}
			
			
			String fieldQuery = req.getParameter(queryField.getFieldName());
			
			if(fieldQuery != null) {
				result.put(queryField, fieldQuery);
				continue;
			}
			
			if(queryField.getAliases() != null) {
				for(String alias : queryField.getAliases()) {
					String aliasQuery = req.getParameter(alias);
					
					if(aliasQuery != null) {
						result.put(queryField, aliasQuery);
						break;
					}
				}
			}
		}
		
		return result;
	}

	private float getFloatParamter(HttpServletRequest req, String[] keys, float defaultValue) {
		
		for(String key : keys) {
			
			String result = req.getParameter(key);
			
			if(result == null) {
				continue;
			}
			
			try {
				return Float.parseFloat(result);
			} catch(NumberFormatException e) {
				continue;
			}
		}
		
		return defaultValue;
	}

	private String getStringParamter(HttpServletRequest req, String[] keys, String defaultValue) {
		
		for(String key : keys) {
			
			String result = req.getParameter(key);
			
			if(result != null) {
				return result;
			}
		}
		
		return defaultValue;
	}
	
	private int getIntParamter(HttpServletRequest req, String[] keys, int defaultValue) {

		for(String key : keys) {
		
			String result = req.getParameter(key);
			
			if(result == null) {
				continue;
			}
			
			try {
				return Integer.parseInt(result);
			} catch(NumberFormatException e) {
				continue;
			}
		}
		
		return defaultValue;
	}

}

