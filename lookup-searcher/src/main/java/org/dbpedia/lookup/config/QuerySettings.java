package org.dbpedia.lookup.config;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Configuration loaded from a YAML Document. Please refer to the configuration
 * documentation
 * at https://github.com/dbpedia/lookup-application
 * 
 * @author Jan Forberg
 *
 */
public class QuerySettings {

	public static final String EXACT_MATCH_BOOST = "exactMatchBoost";

	public static final String PREFIX_MATCH_BOOST = "prefixMatchBoost";

	public static final String MAX_RESULTS = "maxResults";

	public static final String FORMAT = "format";

	public static final String MIN_RELEVANCE_SCORE = "minRelevanceScore";

	public static final String FUZZY_MATCH_BOOST = "fuzzyMatchBoost";

	public static final String FUZZY_PREFIX_LENGTH = "fuzzyPrefixLength";

	public static final String FUZZY_EDIT_DISTANCE = "fuzzyEditDistance";

	private float exactMatchBoost;

	private float prefixMatchBoost;

	private int maxResults;

	private int maxResultsCap;

	private String format;

	private float minRelevanceScore;

	private float fuzzyMatchBoost;

	private int fuzzyPrefixLength = 2;

	private int fuzzyEditDistance = 1;

	public QuerySettings(QueryConfig config) {
		exactMatchBoost = config.getExactMatchBoost();
		prefixMatchBoost = config.getPrefixMatchBoost();
		maxResults = config.getMaxResults();
		maxResultsCap = config.getMaxResultsCap();
		format = config.getFormat();
		minRelevanceScore = config.getMinRelevanceScore();
		fuzzyEditDistance = config.getFuzzyEditDistance();
		fuzzyMatchBoost = config.getFuzzyMatchBoost();
		fuzzyPrefixLength = config.getFuzzyPrefixLength();

		if (format == null || format.equals("")) {
			format = QueryConfig.CONFIG_FIELD_FORMAT_XML;
		}

	}

	public int getFuzzyEditDistance() {
		return fuzzyEditDistance;
	}

	public int getFuzzyPrefixLength() {
		return fuzzyPrefixLength;
	}

	public float getFuzzyMatchBoost() {
		return fuzzyMatchBoost;
	}
	
	public float getMinRelevanceScore() {
		return minRelevanceScore;
	}

	public float getExactMatchBoost() {
		return exactMatchBoost;
	}

	public float getPrefixMatchBoost() {
		return prefixMatchBoost;
	}

	public int getMaxResult() {
		return maxResults;
	}

	public String getFormat() {
		return format;
	}

	public void parse(HttpServletRequest req) {

		exactMatchBoost = parseFloatParameter(req, EXACT_MATCH_BOOST, exactMatchBoost);
		prefixMatchBoost = parseFloatParameter(req, PREFIX_MATCH_BOOST, prefixMatchBoost);
		maxResults = parseIntParameter(req, MAX_RESULTS, maxResults);

		if (maxResultsCap > 0) {
			maxResults = Math.min(maxResults, maxResultsCap);
		}

		format = parseStringParameter(req, FORMAT, format);
		minRelevanceScore = parseFloatParameter(req, MIN_RELEVANCE_SCORE, minRelevanceScore);
		fuzzyMatchBoost = parseFloatParameter(req, FUZZY_MATCH_BOOST, fuzzyMatchBoost);
		fuzzyEditDistance = parseIntParameter(req, FUZZY_EDIT_DISTANCE, fuzzyEditDistance);
		fuzzyPrefixLength = parseIntParameter(req, FUZZY_PREFIX_LENGTH, fuzzyPrefixLength);
	}

	private float parseFloatParameter(HttpServletRequest req, String key, float defaultValue) {

		String result = req.getParameter(key);

		if (result == null) {
			return defaultValue;
		}

		try {
			return Float.parseFloat(result);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private int parseIntParameter(HttpServletRequest req, String key, int defaultValue) {

		String result = req.getParameter(key);

		if (result == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private String parseStringParameter(HttpServletRequest req, String key, String defaultValue) {

		String result = req.getParameter(key);

		if (result != null) {
			return result;
		}

		return defaultValue;
	}

}
