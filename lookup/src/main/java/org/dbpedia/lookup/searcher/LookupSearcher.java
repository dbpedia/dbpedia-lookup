package org.dbpedia.lookup.searcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.join.JoinUtil;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.store.FSDirectory;
import org.dbpedia.lookup.Constants;
import org.dbpedia.lookup.config.LookupConfig;
import org.dbpedia.lookup.config.LookupField;
import org.dbpedia.lookup.config.QuerySettings;
import org.json.JSONArray;
import org.json.JSONObject;;

/**
 * Constructs queries and runs searches on the lucene index
 * 
 * @author Jan Forberg
 *
 */
public class LookupSearcher {

	private static final String FIELD_DOCUMENTS = "docs";

	private static final String FIELD_RESULT = "result";

	private static final String FIELD_DOCUMENT_ID = "id";

	private static final String FIELD_COUNT = "count";

	private static final String FIELD_VALUE = "value";

	private static final String FIELD_HIGHLIGHT = "highlight";

	private IndexSearcher searcher;

	private DoubleValuesSource boostValueSource;

	private SimpleHTMLFormatter formatter;

	private DirectoryReader reader;

	private StandardAnalyzer analyzer;

	private LookupConfig config;

	private FSDirectory directory;

	/**
	 * Creates a new lookup searcher
	 * 
	 * @param filePath The file path of the index
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public LookupSearcher(LookupConfig config)
			throws IOException, java.text.ParseException {

		this.config = config;
	
		this.formatter = new SimpleHTMLFormatter();
		this.analyzer = new StandardAnalyzer();

		refresh();

		createBoostSource(config);
	}

	public void refresh() {

		if(directory != null) {
			try {
				directory.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		if(reader != null) {
			try {
				reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		try {
			this.directory = FSDirectory.open(new File(config.getIndexPath()).toPath());	
			this.reader = DirectoryReader.open(directory);
			this.searcher = new IndexSearcher(reader);
			System.out.println("Searcher has been refreshed");
		} catch (IOException e) {
			System.out.println("Searcher could not find an index to read at " + config.getIndexPath() + ".");
		}
	
	}

	private void createBoostSource(LookupConfig config) {

		String boostFormula = config.getBoostFormula();

		if (boostFormula == null) {
			return;
		}

		try {
			Expression expression = JavascriptCompiler.compile(boostFormula);
			SimpleBindings bindings = new SimpleBindings();

			for (String variable : expression.variables) {
				System.out.println("Adding double value source for " + variable);
				bindings.add(variable, DoubleValuesSource.fromLongField(variable));
			}

			this.boostValueSource = expression.getDoubleValuesSource(bindings);

		} catch (final Exception e) {
			System.err.println("Failed to create boost value source:");
			System.err.println(e.getMessage());
			this.boostValueSource = null;
		}
	}

	/**
	 * Searches the index based on a given query
	 * 
	 * @param queryMap Maps a queryfield for a field specific query
	 * @param maxHits  max number of search results
	 * @return
	 */
	public JSONObject search(QuerySettings settings, Hashtable<LookupField, String> queryMap,
			String join) {

		LookupField[] fields = new LookupField[queryMap.size()];
		String[] queries = new String[queryMap.size()];

		int i = 0;

		for (Entry<LookupField, String> entry : queryMap.entrySet()) {
			fields[i] = entry.getKey();
			queries[i] = entry.getValue();
			i++;
		}

		return search(fields, queries, settings, join);
	}

	/**
	 * Searches the index based on a given query
	 * 
	 * @param maxHits The maximum amount of search results
	 * @return The search results as a string
	 */
	public JSONObject search(LookupField[] fields, String[] queries, QuerySettings settings,
			String join) {

		if(this.searcher == null) {
			return null;
		}

		try {
			if (fields.length != queries.length) {
				return null;
			}

			StandardAnalyzer analyzer = new StandardAnalyzer();

			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

			for (int i = 0; i < fields.length; i++) {

				String field = fields[i].getName();
				String query = queries[i];
				boolean required = fields[i].isRequired();
				boolean allowPartialMatch = fields[i].isAllowPartialMatch();
				boolean isExact = fields[i].isExact();

				if(fields[i].getType() == Constants.CONFIG_FIELD_TYPE_NUMERIC) {

					if(query.contains(",")) {
						String[] conditionStrings = query.split(",");

						if(conditionStrings.length != 2) {
							continue;
						}

						int lowerBound = parseIntWithFallback(conditionStrings[0], Integer.MIN_VALUE);
						int upperBound = parseIntWithFallback(conditionStrings[1], Integer.MAX_VALUE);

						Query rangeQuery = LongPoint.newRangeQuery(field, lowerBound, upperBound);
						
						queryBuilder.add(rangeQuery, Occur.MUST);
					}

					continue;					
				}

				List<String> tokens;

				if (fields[i].tokenize()) {
					tokens = analyze(query, analyzer);
				} else {
					tokens = new ArrayList<String>();

					if (isExact) {
						tokens.add(query);
					} else {
						tokens.add(query.toLowerCase());
					}
				}

				BooleanQuery.Builder tokenQueryBuilder = new BooleanQuery.Builder();

				for (String token : tokens) {

					Query boostQuery = new BoostQuery(createQueryFromToken(field, fields[i].isExact(),
							token, settings), fields[i].getWeight());

					tokenQueryBuilder = tokenQueryBuilder.add(boostQuery,
							allowPartialMatch ? Occur.SHOULD : Occur.MUST);
				}

				queryBuilder = queryBuilder.add(tokenQueryBuilder.build(), required ? Occur.MUST : Occur.SHOULD);
			}

			analyzer.close();

			Query query = queryBuilder.build();

			if (boostValueSource != null) {
				query = FunctionScoreQuery.boostByValue(query, boostValueSource);
			}
			
			TopDocs joinDocs = null;

			if (join != null) {

				joinDocs = this.searcher.search(query, 10000);
				

				query = JoinUtil.createJoinQuery(FIELD_DOCUMENT_ID, false, join,
					query, this.searcher, ScoreMode.None);

				System.out.println(query);
			}

			return getReturnResults(fields, settings, query, join, joinDocs);

		} catch (IOException e) {

			System.out.println("querying went wrong");
			e.printStackTrace();
		}

		return null;
	}

	private JSONObject getReturnResults(LookupField[] fields, QuerySettings settings, Query query, String join, TopDocs joinDocs) throws IOException {
		ArrayList<ScoredDocument> documents = runQuery(query, settings.getMaxResult());
		JSONArray documentArray = new JSONArray();
		HashMap<String, Float> joinScoreMap = null;

		if(join != null) {
			joinScoreMap = new HashMap<>();
			StoredFields storedFields = searcher.storedFields();
			ScoreDoc[] hits = joinDocs.scoreDocs;
	
			for (int i = 0; i < hits.length; i++) {
	
				int docId = hits[i].doc;
				Document document = storedFields.document(docId);
				IndexableField field = document.getField(FIELD_DOCUMENT_ID);
	
				if(field == null) {
					continue;
				}
	
				joinScoreMap.put(field.stringValue(), hits[i].score);
			}
		}


		for (ScoredDocument document : documents) {

			if(join != null) {

				float score = 0;
				IndexableField[] joinFields = document.getDocument().getFields(join);

				for(IndexableField joinField : joinFields) {
					String value = joinField.stringValue();

					if(joinScoreMap.containsKey(value)) {
						score += joinScoreMap.get(value);
					}
				}

				document.setScore(score);
			}
		}

		documents.sort(new ScoreDocumentComparator());

		for (ScoredDocument document : documents) {

			if (document.getScore() < settings.getMinScore()) {
				continue;
			}

			ArrayList<Object> scoreList = new ArrayList<Object>();
			scoreList.add("" + document.getScore());

			HashMap<String, ArrayList<Object>> documentMap = parseResult(query, fields,
					document.getDocument(), settings.getFormat());

			documentMap.put("score", scoreList);
			documentArray.put(documentMap);
		}

		JSONObject returnResults = new JSONObject();
		returnResults.put(settings.getFormat().equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_XML) ? FIELD_RESULT
				: FIELD_DOCUMENTS, documentArray);

		return returnResults;
	}

	/**
	 * Creates a query for a search term token. The query is composed of a prefix
	 * query,
	 * a fuzzy query with the maximum supported edit distance of 2 and an exact
	 * match query.
	 * Exact matches are weighted much higher then prefix or fuzzy matches.
	 * The weights can be adjusted via the config file
	 * 
	 * @param field
	 * @param token
	 * @return
	 * @throws IOException
	 */
	private Query createQueryFromToken(String field, boolean isExact, String token, QuerySettings settings)
			throws IOException {

		if (isExact) {
			return new BoostQuery(new TermQuery(new Term(field, token)), settings.getExactMatchBoost());
		}

		Term term = new Term(field, token);

		Query prefixQuery = new BoostQuery(new PrefixQuery(term), settings.getPrefixMatchBoost());
		Query fuzzyQuery = new BoostQuery(
				new FuzzyQuery(term, settings.getFuzzyEditDistance(), settings.getFuzzyPrefixLength()),
				settings.getFuzzyMatchBoost());
		Query termQuery = new BoostQuery(new TermQuery(term), settings.getExactMatchBoost());

		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.setMinimumNumberShouldMatch(1);

		if (settings.getPrefixMatchBoost() > 0) {
			builder.add(prefixQuery, Occur.SHOULD);
		}

		if (settings.getFuzzyMatchBoost() > 0) {
			builder.add(fuzzyQuery, Occur.SHOULD);
		}

		if (settings.getExactMatchBoost() > 0) {
			builder.add(termQuery, Occur.SHOULD);
		}

		return builder.build();
	}

	private List<String> analyze(String text, Analyzer analyzer) throws IOException {
		List<String> result = new ArrayList<String>();
		TokenStream tokenStream = analyzer.tokenStream(null, text);
		CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();
		while (tokenStream.incrementToken()) {
			result.add(attr.toString());
		}
		tokenStream.close();
		return result;
	}

	/**
	 * Runs a query and returns the results as a list of documents
	 * 
	 * @param query
	 * @param maxResults
	 * @return
	 * @throws IOException
	 * @throws InvalidTokenOffsetsException
	 */
	private ArrayList<ScoredDocument> runQuery(Query query, int maxResults) throws IOException {

		ArrayList<ScoredDocument> resultList = new ArrayList<ScoredDocument>();

		if (maxResults == 0) {
			
			int hitCount = searcher.count(query);

			ScoredDocument scoredDocument = new ScoredDocument();

			Document document = new Document();
			document.add(new StoredField(FIELD_COUNT, "" + hitCount));

			scoredDocument.setDocument(document);
			scoredDocument.setScore(1);

			resultList.add(scoredDocument);
			return resultList;
		}

		TopDocs docs = searcher.search(query, maxResults);
		StoredFields storedFields = searcher.storedFields();
		ScoreDoc[] hits = docs.scoreDocs;

		for (int i = 0; i < hits.length; i++) {

			int docId = hits[i].doc;
			// System.out.println(searcher.explain(query, docId));

			Document document = storedFields.document(docId);

			ScoredDocument scoredDocument = new ScoredDocument();
			scoredDocument.setDocument(document);
			scoredDocument.setScore(hits[i].score);
			resultList.add(scoredDocument);
		}

		return resultList;
	}

	/**
	 * Creates a string-value map from a document and tries to highlight the query
	 * using the query and search fields used to retrieve the document
	 * 
	 * @param document The document
	 * @return The string-value map
	 * @throws IOException
	 */
	private HashMap<String, ArrayList<Object>> parseResult(Query query, LookupField[] fields, Document document,
			String fieldFormat) {

		HashMap<String, ArrayList<Object>> documentMap = new HashMap<String, ArrayList<Object>>();

		for (IndexableField f : document.getFields()) {

			if (!documentMap.containsKey(f.name())) {
				documentMap.put(f.name(), new ArrayList<Object>());
			}

			String value = f.stringValue();
			String name = f.name();

			JSONObject jsonValue = new JSONObject();
			jsonValue.put(FIELD_VALUE, value);

			// Highlight the document field, if the field is one of the search fields
			for (LookupField field : fields) {

				if (name.equals(field.getName()) && field.isHighlight()) {

					String highlightValue = highlightField(query, name, value);

					if (highlightValue != null && fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON)) {
						value = highlightValue;
					}

					if (fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON_FULL)) {
						jsonValue.put(FIELD_HIGHLIGHT, highlightValue != null ? highlightValue : value);
					}

					break;
				}
			}

			if (fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON_FULL)) {
				documentMap.get(f.name()).add(jsonValue);
			} else {
				documentMap.get(f.name()).add(value);
			}
		}

		return documentMap;
	}

	private String highlightField(Query query, String name, String value) {

		QueryScorer scorer = new QueryScorer(query, name);

		// used to markup highlighted terms found in the best sections of a text
		Highlighter highlighter = new Highlighter(formatter, scorer);

		// It breaks text up into same-size texts but does not split up spans
		Fragmenter fragmenter = new SimpleSpanFragmenter(scorer);

		highlighter.setTextFragmenter(fragmenter);

		try {
			return highlighter.getBestFragment(analyzer, name, value);
		} catch (IOException e) {
			System.out.println("hightlighting went wrong");
			return value;
		} catch (InvalidTokenOffsetsException e) {
			System.out.println("hightlighting went wrong");
			return value;
		}
	}

	public void close() {
		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int parseIntWithFallback(String val, int defaultValue) {
		int result;
		try {
			result = Integer.parseInt(val);
		} catch(NumberFormatException ex) {
			result = defaultValue;
		}

		return result;
	}

	public static class ScoredDocument {
		private Document document;

		private float score;

		public Document getDocument() {
			return document;
		}

		public void setDocument(Document document) {
			this.document = document;
		}

		public float getScore() {
			return score;
		}

		public void setScore(float score) {
			this.score = score;
		}
	}

	public class ScoreDocumentComparator implements Comparator<ScoredDocument> {
		@Override
		public int compare(ScoredDocument doc1, ScoredDocument doc2) {
			// Compare scores in descending order
			return Float.compare(doc2.getScore(), doc1.getScore());
		}
	}
}
