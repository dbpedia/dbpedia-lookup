package org.dbpedia.lookup.indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.miscellaneous.TrimFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.standard.StandardFilter;
import org.apache.lucene.analysis.standard.StandardTokenizer;

/**
 * Analzyer setup for NGrams using the NGramTokenFilter
 */
public class NGramAnalyzer extends Analyzer {

	@SuppressWarnings("resource")
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {

		Tokenizer tokenizer = new StandardTokenizer();
		TokenStream stream = new StandardFilter(tokenizer);
		stream = new LowerCaseFilter(stream);
		stream = new StandardFilter(stream);
		stream = new NGramTokenFilter(stream, 3, 5, true);
		stream = new PorterStemFilter(stream);
		stream = new TrimFilter(stream);

		return new TokenStreamComponents(tokenizer, stream);
	}
}