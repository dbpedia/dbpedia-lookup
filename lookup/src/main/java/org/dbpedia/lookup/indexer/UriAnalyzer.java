package org.dbpedia.lookup.indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.miscellaneous.TrimFilter;

/**
 * Analzyer setup for uris and other ids, only trims, no lowercase
 */
public class UriAnalyzer extends Analyzer {

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {

		KeywordTokenizer tok = new KeywordTokenizer();
        TokenFilter trimFilter = new TrimFilter(tok);
        
        return new TokenStreamComponents(tok, trimFilter);
	}
}