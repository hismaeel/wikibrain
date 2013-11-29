package org.wikapidia.sr.dataset;

import org.wikapidia.core.lang.Language;
import org.wikapidia.sr.utils.KnownSim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A gold standard dataset in some language.
 *
 * @author Ben Hillmann
 * @author Matt Lesicko
 */
public class Dataset {
    private String name;
    private Language language;
    private List<KnownSim> data;

    public Dataset(String name, Language language) {
        this.name = name;
        this.language = language;
        this.data = new ArrayList<KnownSim>();
    }

    public Dataset(String name, Language language, List<KnownSim> data) {
        this.name = name;
        this.language = language;
        this.data = data;
    }

    /**
     * Concatenates a list of datasets into a new merged dataset.
     * @param datasets
     */
    public Dataset(List<Dataset> datasets) {
        if (datasets==null||datasets.isEmpty()) {
            throw new IllegalArgumentException("Attempted to create dataset from an empty list");
        }
        this.language = datasets.get(0).getLanguage();
        this.data = new ArrayList<KnownSim>();
        for (Dataset dataset:datasets) {
            if (dataset.getLanguage()!=language) {
                throw new IllegalArgumentException("Dataset language was " + language + " but attempted to add " + dataset.getLanguage());
            }
            this.data.addAll(dataset.getData());
        }
    }

    public Language getLanguage() {
        return language;
    }

    public List<KnownSim> getData() {
        return data;
    }

    public Dataset prune(double minSim, double maxSim) {
        List<KnownSim> pruned = new ArrayList<KnownSim>();
        for (KnownSim ks : data) {
            if (minSim <= ks.similarity && ks.similarity <= maxSim) {
                pruned.add(ks);
            }
        }
        return new Dataset(name + "-pruned", language, pruned);
    }

    /**
     * Shuffles a dataset and splits it into k equally sized subsets, and returns them all
     * @param k the number of desired subsets
     * @return a list of k equally sized subsets of the original dataset
     */
    public List<Dataset> split(int k) {

        if (k>data.size()){
            k=data.size();
        }
        List<KnownSim> clone = new ArrayList<KnownSim>();
        for (KnownSim ks : data){
            clone.add(ks);
        }
        Collections.shuffle(clone);
        List<Dataset> splitSets = new ArrayList<Dataset>();
        for (int i=0; i<k; i++) {
            splitSets.add(new Dataset(name + "-" + i, language));
        }
        for (int i=0; i< clone.size(); i++) {
            splitSets.get(i%k).getData().add(clone.get(i));
        }
        return splitSets;
    }

    public String getName() {
        return name;
    }
}
