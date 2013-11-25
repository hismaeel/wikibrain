package org.wikapidia.sr.evaluation;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;
import org.wikapidia.core.lang.Language;
import org.wikapidia.sr.utils.KnownSim;
import org.wikapidia.utils.WpIOUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * A class that accumulates similarity() evaluation metrics.
 * The results can optionally be logged to a file.
 *
 * @author Shilad Sen
 */
public class SimilarityEvaluation {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private final Map<String, String> config;
    private final BufferedWriter log;

    private int missing;
    private int failed;
    private Date startDate;

    private final TDoubleList actual = new TDoubleArrayList();
    private final TDoubleList estimates = new TDoubleArrayList();

    public SimilarityEvaluation() throws IOException {
        this(new Date(), new HashMap<String, String>(), null);
    }

    public SimilarityEvaluation(File logPath) throws IOException {
        this(new Date(), new HashMap<String, String>(), logPath);
    }

    public SimilarityEvaluation(Map<String, String> config, File logPath) throws IOException {
        this(new Date(), config, logPath);
    }

    public SimilarityEvaluation(Date date, Map<String, String> config, File logPath) throws IOException {
        this.startDate = date;
        this.config = config;
        this.log = WpIOUtils.openWriter(logPath);
        for (String key : config.keySet()) {
            log.write("start" + formatDate(new Date()) + "\n");
            log.write("config" + key + "\t" + config.get(key) + "\n");
        }
        log.flush();
    }

    public synchronized void recordFailed(KnownSim ks) throws IOException {
        failed++;
        write(ks, "failed");
    }

    public synchronized void record(KnownSim ks, Double estimate) throws IOException {
        if (Double.isNaN(estimate) || Double.isInfinite(estimate)) {
            missing++;
        }
        write(ks, estimate.toString());
    }

    private synchronized void write(KnownSim ks, String result) throws IOException {
        if (log != null) {
            log.write("entry\t" + ks.language + "\t" + ks.phrase1 + "\t" + ks.phrase2 + "\t" + ks.similarity + "\t" + result +"\n");
            log.flush();
        }
    }

    public double getPearsonsCorrelation() {
        return new PearsonsCorrelation().correlation(actual.toArray(), estimates.toArray());
    }

    public double getSpearmansCorrelation() {
        return new SpearmansCorrelation().correlation(actual.toArray(), estimates.toArray());
    }

    public int getMissing() {
        return missing;
    }

    public int getFailed() {
        return failed;
    }

    public Map<String, String> getConfig() {
        return config;
    }

    private static String formatDate(Date d) {
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.format(d);
        }
    }

    private static Date parseDate(String s) throws ParseException {
        synchronized (DATE_FORMAT) {
            return DATE_FORMAT.parse(s);
        }
    }

    /**
     * Merges the accumulated values in eval into
     * @param eval
     */
    public void merge(SimilarityEvaluation eval) {
        if (eval.startDate.compareTo(startDate) > 0) {
            this.startDate = eval.startDate;
        }
        missing += eval.missing;
        failed += eval.failed;
        actual.addAll(eval.actual);
        estimates.addAll(eval.estimates);
    }

    /**
     * Reads in the similarity evaluation at a particular path.
     *
     * @param path
     * @return
     */
    public static SimilarityEvaluation read(File path) throws IOException, ParseException {
        Date start = null;
        Map<String, String> config = new HashMap<String, String>();
        SimilarityEvaluation eval = null;

        for (String line : FileUtils.readLines(path, "utf-8")) {
            if (line.endsWith("\n")) {
                line = line.substring(0, line.length() - 1);
            }
            String tokens[] = line.split("\t");
            if (tokens[0].equals("start")) {
                start = parseDate(tokens[1]);
            } else if (tokens[0].equals("config")) {
                config.put(tokens[1], tokens[2]);
            } else if (tokens[0].equals("entry")) {
                if (eval == null) {
                    eval = new SimilarityEvaluation(start, config, null);
                }
                KnownSim ks = new KnownSim(tokens[2], tokens[3], Double.valueOf(tokens[4]), Language.getByLangCode(tokens[1]));
                String val = tokens[5];
                if (val.equals("failed")) {
                    eval.recordFailed(ks);
                } else {
                    eval.record(ks, Double.valueOf(val));
                }
            } else {
                throw new IllegalStateException("invalid event in log " + path + ": " + line);
            }
        }

        return eval;
    }
}
