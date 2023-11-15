package org.dbpedia.lookup.indexer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.dbpedia.lookup.Constants;
import org.dbpedia.lookup.config.IndexConfig;
import org.dbpedia.lookup.config.IndexField;
import org.slf4j.Logger;

public class LuceneLookupIndexer {

	private static String lockFileName = "/write.lock";

	private IndexWriter indexWriter;

	private IndexSearcher searcher;

	private FSDirectory targetDirectory;

	private FSDirectory indexDirectory;

	private String indexPath;

	private String targetPath;

	private IndexConfig indexConfig;

	private Logger logger;

	private ConcurrentHashMap<String, Document> documentCache;

	private Analyzer analyzer;

	private String lastResource = null;

	private String lastValue = null;

	/**
	 * Creates a new lucene lookup indexer
	 * 
	 * @param filePath       File path to use for indexing
	 * @param updateInterval Max amount of updates before a commit
	 * @param cacheSize      Max cache size
	 */
	public LuceneLookupIndexer(IndexConfig indexConfig, Logger logger) {

		this.logger = logger;

		this.indexConfig = indexConfig;
		this.targetPath = indexConfig.getIndexPath();

		// Create a document cache to keep documents in between commits to the lucene
		// structure
		documentCache = new ConcurrentHashMap<String, Document>();

		// Create a new analyzer for the given index configuration
		analyzer = createAnalyzer(indexConfig);

		try {
			// Open the target directory
			targetDirectory = FSDirectory.open(Paths.get(targetPath));

			if (indexConfig.isCleanIndex()) {
				String stagingFolderName = "." + UUID.randomUUID().toString();
				indexPath = Paths.get(targetPath).getParent().toString() +
					File.separator + stagingFolderName;
				indexDirectory = FSDirectory.open(Paths.get(indexPath));

			} else {
				indexPath = targetPath;
				indexDirectory = targetDirectory;
			}

			File file = new File(indexPath);
			file.mkdirs();

			documentCache = new ConcurrentHashMap<String, Document>();
			indexWriter = new IndexWriter(indexDirectory, createWriterConfig());
			searcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

		} catch (IndexNotFoundException e) {
			return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	private IndexWriterConfig createWriterConfig() {
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
		indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		indexWriterConfig.setMaxBufferedDocs(indexConfig.getCacheSize());
		return indexWriterConfig;
	}

	private Analyzer createAnalyzer(IndexConfig indexConfig) {

		Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();

		for (IndexField field : indexConfig.getIndexFields()) {

			String fieldType = field.getType();

			if (fieldType == null) {
				continue;
			}

			if (fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_STRING)) {
				analyzerPerField.put(field.getFieldName(), new StringPhraseAnalyzer());
			}

			if(fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_NGRAM)) {
				analyzerPerField.put(field.getFieldName(), new NGramAnalyzer());
			}
		}

		Analyzer analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
		return analyzer;
	}

	public void indexResult(ResultSet result, IndexField path) {

		int k = 0;

		while (result.hasNext()) {

			QuerySolution entry = result.next();
			RDFNode documentNode = entry.get(path.getDocumentVariable());
			RDFNode fieldValueNode = entry.get(path.getFieldName());

			lastResource = documentNode.toString();

			if (fieldValueNode.isLiteral()) {
				lastValue = fieldValueNode.asLiteral().getString();
			} else {
				lastValue = fieldValueNode.toString();
			}

			indexField(lastResource, path, lastValue);

			k++;

			if (k % indexConfig.getCommitInterval() == 0) {
				commit();
			}
		}
	}

	public void indexField(String resource, IndexField path, String literal) {

		String field = path.getFieldName();
		String fieldType = path.getType();

		if (fieldType != null) {
			fieldType = fieldType.toLowerCase();
		} else {
			fieldType = Constants.CONFIG_FIELD_TYPE_TEXT;
		}

		try {
			Document doc = findDocument(resource);

			switch (fieldType) {
				case Constants.CONFIG_FIELD_TYPE_NUMERIC:
					long value = Long.parseLong(literal);
					doc.removeFields(field);
					doc.add(new StoredField(field, value));
					doc.add(new NumericDocValuesField(field, value));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED:
					doc.add(new StoredField(field, literal));
					break;
				case Constants.CONFIG_FIELD_TYPE_STRING:
					doc.add(new StringField(field, literal, Field.Store.YES));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED_SORTED:
					doc.add(new StoredField(field, literal));
					doc.add(new SortedDocValuesField(Constants.FIELD_RESOURCE, new BytesRef(resource)));
					break;
				default:
					doc.add(new TextField(field, literal, Field.Store.YES));
					break;
			}

			indexWriter.updateDocument(new Term(Constants.FIELD_RESOURCE, resource), doc);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Document findDocument(String resource) throws IOException {

		if(resource == null) {
			return null;
		}

		if (documentCache.containsKey(resource)) {
			return documentCache.get(resource);
		}

		// First try: search the index
		Document document = getDocumentFromIndex(resource);

		if (document == null) {
			// Second try: create new document, add to cache
			document = new Document();

			document.add(new StringField(Constants.FIELD_RESOURCE, resource, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_RESOURCE, new BytesRef(resource)));
		}

		documentCache.put(resource, document);
		return document;
	}

	private Document getDocumentFromIndex(String resource) throws IOException {

		if (searcher == null) {
			return null;
		}

		Term resourceTerm = new Term(Constants.FIELD_RESOURCE, resource);
		TermQuery resourceTermQuery = new TermQuery(resourceTerm);

		TopDocs docs = searcher.search(resourceTermQuery, 1);

		if (docs.totalHits > 0) {

			Document document = searcher.doc(docs.scoreDocs[0].doc);
			document.removeFields(Constants.FIELD_RESOURCE);
			document.add(new StringField(Constants.FIELD_RESOURCE, resource, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_RESOURCE, new BytesRef(resource)));
			// Fetched document fields are all reset to default stored/text fields
			// Special fields such as the numeric fields have to be reset to their
			// respective type
			for (IndexField field : indexConfig.getIndexFields()) {

				String fieldType = field.getType();

				if (fieldType == null) {
					continue;
				}

				// Remove all numeric index fields and re-add them to the document as
				// NumericDocValuesField (plus StoredField for retrieval)
				if (fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_NUMERIC)) {

					// Fetch the field with the given index field name (does not support
					// multi-values)
					IndexableField numericField = document.getField(field.getFieldName());

					// Only do this if a field with the given field name exists
					if (numericField != null) {

						// Fetch the field long value
						long value = numericField.numericValue().longValue();

						// Remove all fields with the respective name
						document.removeFields(field.getFieldName());

						// Re-add the field
						document.add(new NumericDocValuesField(field.getFieldName(), value));
						document.add(new StoredField(field.getFieldName(), value));
					}
				}
			}

			return document;
		}

		return null;
	}

	public void commit() {

		logger.info("Latest: [" + lastValue + "] --> <" + lastResource + "> ");
		logger.info("=== COMMITING ===");

		try {
			indexWriter.commit();

			documentCache = new ConcurrentHashMap<String, Document>();
			searcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

			// index = FSDirectory.open(new File(indexPath).toPath());
			// indexWriter = new IndexWriter(indexDirectory, createWriterConfig());

			// IndexReader reader = DirectoryReader.open(indexDirectory);
			// searcher = new IndexSearcher(reader);
			// documentCache = new ConcurrentHashMap<String, Document>();

			System.gc();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public boolean clearIndex() {

		try {
			indexWriter.deleteAll();
			commit();
			return true;
		} catch (IndexNotFoundException e) {
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}

	public void finish() {
		try {
			indexWriter.close();

			// Remove lock file
			File lockFile = new File(indexPath + lockFileName);
			lockFile.delete();

			// If we are in a staging area, copy the results over
			if (indexConfig.isCleanIndex()) {
				File targetFile = new File(targetPath);

				FileUtils.cleanDirectory(targetFile);
				File indexFile = new File(indexPath);

				for(File file : indexFile.listFiles()) {
					FileUtils.moveToDirectory(file, targetFile, false);
				}

				indexFile.delete();

			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void test() {

	}

}
