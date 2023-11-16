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

	private String lastDocument = null;

	private String lastValue = null;

	private File stagingDirectory = null;

	public LuceneLookupIndexer(String targetPath, IndexConfig indexConfig, Logger logger) {

		this.logger = logger;
		this.indexConfig = indexConfig;
		this.targetPath = targetPath; 

		// Create a document cache to keep documents in between commits to the lucene structure
		documentCache = new ConcurrentHashMap<String, Document>();

		// Create a new analyzer for the given index configuration
		analyzer = createAnalyzer(indexConfig);

		try {
			// Open the target directory
			targetDirectory = FSDirectory.open(Paths.get(targetPath));

			if (indexConfig.isCleanIndex()) {
				String stagingFolderName = "." + UUID.randomUUID().toString();
				this.indexPath = Paths.get(targetPath).getParent().toString() +
					File.separator + stagingFolderName;
				indexDirectory = FSDirectory.open(Paths.get(this.indexPath));

				this.stagingDirectory = new File(this.indexPath);
			} else {
				this.indexPath = targetPath;
				indexDirectory = targetDirectory;
			}

			File file = new File(this.indexPath);
			file.mkdirs();

			documentCache = new ConcurrentHashMap<String, Document>();
			indexWriter = new IndexWriter(indexDirectory, createWriterConfig());
			searcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

		} catch (IndexNotFoundException e) {
			return;
		} catch (IOException e) {
			cleanUp();
			e.printStackTrace();
			return;
		}
	}

	public void cleanUp() {
		if(this.stagingDirectory != null) {
			for(File file : this.stagingDirectory.listFiles()) {
				file.delete();	
			}

			this.stagingDirectory.delete();
			this.stagingDirectory = null;
		}
	}

	private IndexWriterConfig createWriterConfig() {
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
		indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		indexWriterConfig.setMaxBufferedDocs(indexConfig.getMaxBufferedDocs());
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

			if(fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_URI)) {
				analyzerPerField.put(field.getFieldName(), new UriAnalyzer());
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

			lastDocument = documentNode.toString();

			if (fieldValueNode.isLiteral()) {
				lastValue = fieldValueNode.asLiteral().getString();
			} else {
				lastValue = fieldValueNode.toString();
			}

			indexField(lastDocument, path, lastValue);

			k++;

			if (k % indexConfig.getCommitInterval() == 0) {
				commit();
			}
		}
	}

	public void indexField(String documentId, IndexField indexField, String valueString) {

		String field = indexField.getFieldName();
		String fieldType = indexField.getType();

		if (fieldType != null) {
			fieldType = fieldType.toLowerCase();
		} else {
			fieldType = Constants.CONFIG_FIELD_TYPE_TEXT;
		}

		try {
			Document doc = findDocument(documentId);

			switch (fieldType) {
				case Constants.CONFIG_FIELD_TYPE_NUMERIC:
					long value = Long.parseLong(valueString);
					doc.removeFields(field);
					doc.add(new StoredField(field, value));
					doc.add(new NumericDocValuesField(field, value));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED:
					doc.add(new StoredField(field, valueString));
					break;
				case Constants.CONFIG_FIELD_TYPE_STRING:
					doc.add(new StringField(field, valueString, Field.Store.YES));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED_SORTED:
					doc.add(new StoredField(field, valueString));
					doc.add(new SortedDocValuesField(field, new BytesRef(valueString)));
					break;
				case Constants.CONFIG_FIELD_TYPE_URI:
					doc.add(new StringField(field, valueString, Field.Store.YES));
					break;
				default:
					doc.add(new TextField(field, valueString, Field.Store.YES));
					break;
			}

			indexWriter.updateDocument(new Term(Constants.FIELD_DOCUMENT_ID, documentId), doc);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Document findDocument(String documentId) throws IOException {

		if(documentId == null) {
			return null;
		}

		if (documentCache.containsKey(documentId)) {
			return documentCache.get(documentId);
		}

		// First try: search the index
		Document document = getDocumentFromIndex(documentId);

		if (document == null) {
			// Second try: create new document, add to cache
			document = new Document();

			document.add(new StringField(Constants.FIELD_DOCUMENT_ID, documentId, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_DOCUMENT_ID, new BytesRef(documentId)));
		}

		documentCache.put(documentId, document);
		return document;
	}

	private Document getDocumentFromIndex(String documentId) throws IOException {

		if (searcher == null) {
			return null;
		}

		Term documentIdTerm = new Term(Constants.FIELD_DOCUMENT_ID, documentId);
		TermQuery documentIdTermQuery = new TermQuery(documentIdTerm);

		TopDocs docs = searcher.search(documentIdTermQuery, 1);

		if (docs.totalHits > 0) {

			Document document = searcher.doc(docs.scoreDocs[0].doc);
			document.removeFields(Constants.FIELD_DOCUMENT_ID);
			document.add(new StringField(Constants.FIELD_DOCUMENT_ID, documentId, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_DOCUMENT_ID, new BytesRef(documentId)));
			
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

		logger.info("Latest: [" + lastValue + "] --> <" + lastDocument + "> ");
		logger.info("=== COMMITING ===");

		try {
			indexWriter.commit();

			documentCache = new ConcurrentHashMap<String, Document>();
			searcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

			System.gc();

		} catch (IOException e) {
			e.printStackTrace();
		}

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
