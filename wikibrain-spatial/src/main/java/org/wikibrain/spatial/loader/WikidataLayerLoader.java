package org.wikibrain.spatial.loader;

import com.vividsolutions.jts.geom.Geometry;
import gnu.trove.TCollections;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.MetaInfoDao;
import org.wikibrain.core.lang.LanguageSet;
import org.wikibrain.core.model.UniversalPage;
import org.wikibrain.spatial.core.dao.SpatialDataDao;
import org.wikibrain.spatial.util.WikiBrainSpatialUtils;
import org.wikibrain.utils.ParallelForEach;
import org.wikibrain.utils.Procedure;
import org.wikibrain.utils.WpThreadUtils;
import org.wikibrain.wikidata.WikidataDao;
import org.wikibrain.wikidata.WikidataFilter;
import org.wikibrain.wikidata.WikidataSqlDao;
import org.wikibrain.wikidata.WikidataStatement;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads points from wikidata as a layer.
 *
 * @author bjhecht, Shilad
 */
public class WikidataLayerLoader {

    private static final Logger LOG = Logger.getLogger(WikidataLayerLoader.class.getName());

    public static final String EARTH_REF_SYS_NAME = "earth";
    public static final String LAYER_NAME = "wikidata";
    private static final int COORDINATE_LOCATION_PROPERTY_ID = 625;

    private final WikidataDao wdDao;
    private final SpatialDataDao spatialDao;
    private final MetaInfoDao miDao;

    public WikidataLayerLoader(MetaInfoDao metaDao, WikidataDao wdDao, SpatialDataDao spatialDao) {
        this.wdDao = wdDao;
        this.spatialDao = spatialDao;
        this.miDao = metaDao;
    }

    public final void loadData(final LanguageSet langs) throws DaoException {
        final TIntSet savedConcepts = TCollections.synchronizedSet(new TIntHashSet());

        final AtomicInteger matches = new AtomicInteger();
        final AtomicInteger count = new AtomicInteger();
        WikidataFilter filter = (new WikidataFilter.Builder()).withPropertyId(COORDINATE_LOCATION_PROPERTY_ID).build();
        Iterable<WikidataStatement> statements = wdDao.get(filter);
        ParallelForEach.iterate(statements.iterator(), WpThreadUtils.getMaxThreads(), 100, new Procedure<WikidataStatement>() {
            @Override
            public void call(WikidataStatement statement) throws Exception {
                UniversalPage uPage = wdDao.getUniversalPage(statement.getItem().getId());
                if (uPage != null && uPage.isInLanguageSet(langs, false)){
                    matches.incrementAndGet();
                    try {
                        storeStatement(savedConcepts, langs, statement);
                    } catch (Exception e) {
                        LOG.log(Level.SEVERE, "storage of statement failed: " + statement.toString(), e);
                    }
                }
                count.incrementAndGet();
                if (count.get() % 10000 == 0){
                    LOG.log(Level.INFO, "Matched " + matches + " out of " + count + " statements from " + this.getClass().getName());
                }

            }
        }, Integer.MAX_VALUE);
    }

    private boolean storeStatement(TIntSet savedConcepts, LanguageSet langs, WikidataStatement statement) throws DaoException {
        UniversalPage uPage = wdDao.getUniversalPage(statement.getItem().getId());
        if (uPage == null || !uPage.isInLanguageSet(langs, false)){
            return false;
        }

        int itemId = statement.getItem().getId();
        Geometry g = WikiBrainSpatialUtils.jsonToGeometry(statement.getValue().getJsonValue().getAsJsonObject());
        if (g == null) {
            return false;
        }

        if (savedConcepts.contains(itemId)) {
            return false;
        }
        savedConcepts.add(itemId);
        spatialDao.saveGeometry(itemId, LAYER_NAME, EARTH_REF_SYS_NAME,  g);
        miDao.incrementRecords(Geometry.class);
        return true;
    }
}
