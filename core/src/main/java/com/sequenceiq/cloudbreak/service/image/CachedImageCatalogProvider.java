package com.sequenceiq.cloudbreak.service.image;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.client.RestClientUtil;
import com.sequenceiq.cloudbreak.cloud.model.catalog.CloudbreakImageCatalogV2;
import com.sequenceiq.cloudbreak.core.CloudbreakImageCatalogException;
import com.sequenceiq.cloudbreak.util.FileReaderUtils;
import com.sequenceiq.cloudbreak.util.JsonUtil;

@Component
public class CachedImageCatalogProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(CachedImageCatalogProvider.class);

    @Value("${cb.etc.config.dir}")
    private String etcConfigDir;

    @Cacheable(cacheNames = "imageCatalogCache", key = "#catalogUrl")
    public CloudbreakImageCatalogV2 getImageCatalogV2(String catalogUrl) throws CloudbreakImageCatalogException {
        CloudbreakImageCatalogV2 catalog = null;
        if (catalogUrl == null) {
            LOGGER.warn("No image catalog was defined!");
            return catalog;
        }

        try {
            long started = System.currentTimeMillis();
            if (catalogUrl.startsWith("http")) {
                Client client = RestClientUtil.get();
                WebTarget target = client.target(catalogUrl);
                catalog = target.request().get().readEntity(CloudbreakImageCatalogV2.class);
            } else {
                String content = readCatalogFromFile(catalogUrl);
                catalog = JsonUtil.readValue(content, CloudbreakImageCatalogV2.class);
            }
            validateImageCatalogUuids(catalog);
            long timeOfParse = System.currentTimeMillis() - started;
            LOGGER.info("ImageCatalog has been get and parsed from '{}' and took '{}' ms.", catalogUrl, timeOfParse);
        } catch (RuntimeException e) {
            throw new CloudbreakImageCatalogException("Failed to get image catalog", e);
        } catch (IOException e) {
            throw new CloudbreakImageCatalogException(String.format("Failed to read image catalog from file: '%s'", catalogUrl), e);
        }
        return catalog;
    }

    @CacheEvict(value = "imageCatalogCache", key = "#catalogUrl")
    public void evictImageCatalogCache(String catalogUrl) {
    }

    private void validateImageCatalogUuids(CloudbreakImageCatalogV2 imageCatalog) throws CloudbreakImageCatalogException {
        Stream<String> uuidStream = Stream.concat(imageCatalog.getImages().getBaseImages().stream().map(images -> images.getUuid()),
                imageCatalog.getImages().getHdpImages().stream().map(images -> images.getUuid()));
        uuidStream = Stream.concat(uuidStream, imageCatalog.getImages().getHdfImages().stream().map(images -> images.getUuid()));
        List<String> uuidList = uuidStream.collect(Collectors.toList());
        List<String> orphanUuids = imageCatalog.getVersions().getCloudbreakVersions().stream().flatMap(cbv -> cbv.getImageIds().stream()).
                filter(imageId -> !uuidList.contains(imageId)).collect(Collectors.toList());
        if (!orphanUuids.isEmpty()) {
            throw new CloudbreakImageCatalogException(String.format("Images with ids: %s is not present in ambari-images block",
                    StringUtils.join(orphanUuids, ",")));
        }
    }

    void setEtcConfigDir(String etcConfigDir) {
        this.etcConfigDir = etcConfigDir;
    }

    private String readCatalogFromFile(String catalogUrl) throws IOException {
        File customCatalogFile = new File(etcConfigDir, catalogUrl);
        return FileReaderUtils.readFileFromPath(customCatalogFile.toPath());
    }
}
