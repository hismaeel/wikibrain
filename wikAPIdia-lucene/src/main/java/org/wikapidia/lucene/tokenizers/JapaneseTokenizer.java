package org.wikapidia.lucene.tokenizers;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.cjk.CJKWidthFilter;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.ja.JapaneseAnalyzer;
import org.apache.lucene.analysis.ja.JapaneseBaseFormFilter;
import org.apache.lucene.analysis.ja.JapaneseKatakanaStemFilter;
import org.apache.lucene.analysis.ja.JapanesePartOfSpeechStopFilter;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.util.Version;
import org.wikapidia.core.lang.Language;
import org.wikapidia.lucene.TokenizerOptions;

import java.io.Reader;

/**
 * @author Ari Weiland
 */
public class JapaneseTokenizer extends LanguageTokenizer {

    protected JapaneseTokenizer(Version version, TokenizerOptions options, Language language) {
        super(version, options, language);
    }

    @Override
    public Tokenizer setTokenizer(Reader r) {
        tokenizer = new org.apache.lucene.analysis.ja.JapaneseTokenizer(r, null, false, org.apache.lucene.analysis.ja.JapaneseTokenizer.DEFAULT_MODE);
        return tokenizer;
    }

    @Override
    public TokenStream getTokenStream(Reader reader, CharArraySet stemExclusionSet) {
        TokenStream stream = setTokenizer(reader);
        stream = new JapaneseBaseFormFilter(stream);
        stream = new CJKWidthFilter(stream);
        if (caseInsensitive)
            stream = new LowerCaseFilter(matchVersion, stream);
        if (useStopWords) {
            stream = new JapanesePartOfSpeechStopFilter(true, stream, JapaneseAnalyzer.getDefaultStopTags());
            stream = new StopFilter(matchVersion, stream, JapaneseAnalyzer.getDefaultStopSet());
        }
        if (useStem)
            stream = new JapaneseKatakanaStemFilter(stream);
        return stream;
    }
}