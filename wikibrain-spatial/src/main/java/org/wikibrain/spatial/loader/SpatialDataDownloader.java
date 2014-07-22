package org.wikibrain.spatial.loader;

import com.typesafe.config.Config;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.io.FileUtils;
import org.wikibrain.conf.Configuration;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.download.FileDownloader;
import org.wikibrain.spatial.util.WbShapeFile;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Shilad Sen
 */
public class SpatialDataDownloader {
    private static final Logger LOG = Logger.getLogger(SpatialDataDownloader.class.getName());

    private final SpatialDataFolder dir;
    private final Configuration config;

    public SpatialDataDownloader(SpatialDataFolder dir) {
        this.dir = dir;
        this.config = null;
    }

    public SpatialDataDownloader(Configuration config) {
        this.config = config;
        this.dir = new SpatialDataFolder(new File(config.get().getString("spatial.dir")));
    }

    public SpatialDataDownloader(Env env) {
        this.config = env.getConfiguration();
        this.dir = new SpatialDataFolder(new File(config.get().getString("spatial.dir")));
    }

    public WbShapeFile download(URL url, String zipShp, String refSystem, String layerGroup, String name, String encoding) throws InterruptedException, IOException {
        // Download the file if necessary
        String tokens[] = url.toString().split("/");
        String filename = refSystem + "_" + layerGroup + "_" + name + "_" + tokens[tokens.length - 1];
        File zipDest = FileUtils.getFile(dir.getRawFolder(), filename);
        if (!zipDest.isFile()) {
            FileDownloader downloader = new FileDownloader();
            downloader.download(url, zipDest);
        }

        // Unzip the file
        File tmpDir;
        try {
            ZipFile zipFile = new ZipFile(zipDest.getCanonicalPath());
            tmpDir = File.createTempFile("wikibrain", ".exploded");
            FileUtils.deleteQuietly(tmpDir);
            FileUtils.forceMkdir(tmpDir);
            LOG.log(Level.INFO, "Extracting to " + tmpDir);
            zipFile.extractAll(tmpDir.getAbsolutePath());
            FileUtils.forceDeleteOnExit(tmpDir);
        } catch (ZipException e) {
            throw new IOException(e);
        }

        // Move the requested shape file into place
        WbShapeFile src = new WbShapeFile(new File(tmpDir, zipShp), encoding);
        for (File file : src.getComponentFiles()) {
            if (!file.isFile()) {
                throw new IllegalArgumentException("expected file " + file.getAbsolutePath() + " not found");
            }
        }
        WbShapeFile dest = dir.getShapeFile(refSystem, layerGroup, name, encoding);
        src.move(dest.getFile());
        if (!dest.hasComponentFiles()) {
            throw new IllegalArgumentException();
        }

        return dest;
    }

    public WbShapeFile download(String refSysName, String layerGroup, String datasetName) throws InterruptedException, IOException {
        if (config == null) {
            throw new IllegalArgumentException("To download by name, SpatialDataDownloader must have a configuration");
        }
        Config c = config.getConfig("spatial.datasets", refSysName, layerGroup, datasetName);
        WbShapeFile shapeFile = download(
                new URL(c.getString("url")),
                c.getString("shp"),
                refSysName,
                layerGroup,
                datasetName,
                c.getString("encoding")
        );
        downloadMapping(new URL(c.getString("mappingUrl")), shapeFile);
        return shapeFile;
    }

    public void downloadMapping(URL mappingUrl, WbShapeFile shapeFile) throws IOException, InterruptedException {
        File dest = shapeFile.getMappingFile();

        File tmp = File.createTempFile("wikibrain-mapping", "zip");
        FileUtils.deleteQuietly(tmp);

        if (!tmp.isFile()) {
            FileDownloader downloader = new FileDownloader();
            downloader.download(mappingUrl, tmp);
        }
        FileUtils.forceDeleteOnExit(tmp);

        // Unzip the file
        File tmpDir;
        try {
            ZipFile zipFile = new ZipFile(tmp.getCanonicalPath());
            tmpDir = File.createTempFile("wikibrain", ".exploded");
            FileUtils.deleteQuietly(tmpDir);
            FileUtils.forceMkdir(tmpDir);
            LOG.log(Level.INFO, "Extracting " + mappingUrl + " to " + tmpDir);
            zipFile.extractAll(tmpDir.getAbsolutePath());
            FileUtils.forceDeleteOnExit(tmpDir);
        } catch (ZipException e) {
            throw new IOException(e);
        }

        File src = FileUtils.getFile(tmpDir, dest.getName());
        if (!src.isFile()) {
            throw new IOException("Missing file " + dest.getName() + " in " + mappingUrl);
        }
        FileUtils.forceDelete(dest);
        FileUtils.moveFile(src, dest);
    }
}
