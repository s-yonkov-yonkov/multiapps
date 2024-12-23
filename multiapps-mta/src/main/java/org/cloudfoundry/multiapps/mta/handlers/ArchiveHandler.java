package org.cloudfoundry.multiapps.mta.handlers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;
import org.cloudfoundry.multiapps.common.ContentException;
import org.cloudfoundry.multiapps.common.SLException;
import org.cloudfoundry.multiapps.mta.Messages;
import org.cloudfoundry.multiapps.mta.util.LimitedInputStream;

public class ArchiveHandler {

    public static final String MTA_DEPLOYMENT_DESCRIPTOR_NAME = "META-INF/mtad.yaml";
    public static final String MTA_MANIFEST_NAME = "META-INF/MANIFEST.MF";

    private ArchiveHandler() {

    }

    public static Manifest getManifest(InputStream archiveStream, long maxManifestSize) throws SLException {
        try (InputStream manifestStream = getInputStream(archiveStream, JarFile.MANIFEST_NAME, maxManifestSize)) {
            return new Manifest(manifestStream);
        } catch (IOException e) {
            throw new SLException(e, Messages.ERROR_RETRIEVING_MTA_ARCHIVE_MANIFEST);
        }
    }

    public static String getDescriptor(InputStream archiveStream, long maxMtaDescriptorSize) throws SLException {
        try (InputStream descriptorStream = getInputStream(archiveStream, MTA_DEPLOYMENT_DESCRIPTOR_NAME, maxMtaDescriptorSize)) {
            return IOUtils.toString(descriptorStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SLException(e, Messages.ERROR_RETRIEVING_MTA_MODULE_CONTENT);
        }
    }

    public static byte[] getFileContent(InputStream archiveStream, String fileName, long maxMtaFileSize) throws SLException {
        try (InputStream moduleStream = getInputStream(archiveStream, fileName, maxMtaFileSize)) {
            return IOUtils.toByteArray(moduleStream);
        } catch (IOException e) {
            throw new SLException(e, Messages.ERROR_RETRIEVING_MTA_MODULE_CONTENT, fileName);
        }
    }

    public static InputStream getInputStream(InputStream is, String entryName, long maxEntrySize) {
        try {
            ZipArchiveInputStream zis = new ZipArchiveInputStream(is);
            for (ZipArchiveEntry e; (e = zis.getNextEntry()) != null;) {
                if (e.getName()
                     .equals(entryName)) {
                    // quick and unreliable check for file size without file processing
                    validateZipEntrySize(e, maxEntrySize);
                    // ensure processed file size indeed is lower than the configured limit
                    return new LimitedInputStream(zis, maxEntrySize) {
                        @Override
                        protected void raiseError(long maxSize, long currentSize) {
                            throw new ContentException(Messages.ERROR_PROCESSED_SIZE_OF_FILE_EXCEEDS_CONFIGURED_MAX_SIZE_LIMIT,
                                                       currentSize,
                                                       entryName,
                                                       maxSize);
                        }
                    };
                }
            }
            throw new ContentException(Messages.CANNOT_FIND_ARCHIVE_ENTRY, entryName);
        } catch (IOException e) {
            throw new SLException(e, Messages.ERROR_RETRIEVING_ARCHIVE_ENTRY, entryName);
        }
    }

    private static void validateZipEntrySize(ZipArchiveEntry zipEntry, long maxEntrySize) {
        if (zipEntry.getSize() > maxEntrySize) {
            throw new ContentException(Messages.ERROR_SIZE_OF_FILE_EXCEEDS_CONFIGURED_MAX_SIZE_LIMIT,
                                       zipEntry.getSize(),
                                       zipEntry.getName(),
                                       maxEntrySize);
        }
    }
}
