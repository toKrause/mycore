/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * MyCoRe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MyCoRe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MyCoRe.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mycore.datamodel.ifs2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.MCRUsageException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.config.MCRConfigurationException;
import org.mycore.common.content.MCRByteContent;
import org.mycore.common.content.MCRContent;
import org.mycore.common.content.streams.MCRByteArrayOutputStream;
import org.mycore.datamodel.ifs2.MCRMetadataVersion.MCRMetadataVersionState;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLogEntry;
import org.tmatesoft.svn.core.SVNLogEntryPath;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;
import org.tmatesoft.svn.core.auth.SVNUserNameAuthentication;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.admin.ISVNAdminEventHandler;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminEvent;

/**
 * TODO: expand
 * 
 * Stores metadata objects both in a local filesystem structure and in a
 * Subversion repository. Changes can be tracked and restored. To enable
 * versioning, configure the repository URL, for example
 *
 * MCR.IFS2.Store.DocPortal_document.SVNRepositoryURL=file:///foo/svnroot/
 *
 * @author Frank Lützenkirchen
 */
public class MCRSVNXMLMetadataStore extends MCRXMLMetadataStore {

    protected static final Logger LOGGER = LogManager.getLogger();

    protected SVNURL repURL;

    protected static final boolean SYNC_LAST_MODIFIED_ON_SVN_COMMIT = MCRConfiguration2
        .getBoolean("MCR.IFS2.SyncLastModifiedOnSVNCommit").orElse(true);

    static {
        FSRepositoryFactory.setup();
    }

    @Override
    protected void init(String type) {
        super.init(type);
        setupSVN(type);
    }

    @Override
    protected void init(MCRStoreConfig config) {
        super.init(config);
        setupSVN(config.getID());
    }

    private void setupSVN(String type) {
        LOGGER.info("Entering setupSVN(String {})", type);
        URI repositoryURI;
        String repositoryURIString = MCRConfiguration2.getStringOrThrow("MCR.IFS2.Store." + type + ".SVNRepositoryURL");
        try {
            repositoryURI = new URI(repositoryURIString);
            LOGGER.info("Repository URI: {}", repositoryURI);
        } catch (URISyntaxException e) {
            String msg = "Syntax error in MCR.IFS2.Store." + type + ".SVNRepositoryURL property: "
                + repositoryURIString;
            throw new MCRConfigurationException(msg, e);
        }
        try {
            LOGGER.info("Versioning metadata store {} repository URL: {}", type, repositoryURI);
            repURL = SVNURL.create(repositoryURI.getScheme(), repositoryURI.getUserInfo(), repositoryURI.getHost(),
                repositoryURI.getPort(), repositoryURI.getPath(), true);
            LOGGER.info("repURL: {}", repURL);
            File dir = new File(repURL.getPath());
            if (!dir.exists() || (dir.isDirectory() && dir.list().length == 0)) {
                LOGGER.info("Repository does not exist, creating new SVN repository at {}", repositoryURI);
                repURL = SVNRepositoryFactory.createLocalRepository(dir, true, false);
            }
            LOGGER.info("Exiting setupSVN(String {})\", type");
        } catch (SVNException ex) {
            String msg = "Error initializing SVN repository at URL " + repositoryURI;
            throw new MCRConfigurationException(msg, ex);
        }
    }

    /**
     * When metadata is saved, this results in SVN commit. If the property
     * MCR.IFS2.SyncLastModifiedOnSVNCommit=true (which is default), the
     * last modified date of the metadata file in the store will be set to the exactly
     * same timestamp as the SVN commit. Due to permission restrictions on Linux systems,
     * this may fail, so you can disable that behaviour.
     *
     * @return true, if last modified of file should be same as timestamp of SVN commit
     */
    public static boolean shouldSyncLastModifiedOnSVNCommit() {
        return SYNC_LAST_MODIFIED_ON_SVN_COMMIT;
    }

    /**
     * Returns the SVN repository used to manage metadata versions in this
     * store.
     *
     * @return the SVN repository used to manage metadata versions in this
     *         store.
     */
    private SVNRepository getRepository() throws MCRPersistenceException {
        LOGGER.info("Entering getRepository()");
        try {
            SVNRepository repository = SVNRepositoryFactory.create(repURL);
            String user = MCRSessionMgr.getCurrentSession().getUserInformation().getUserID();
            SVNAuthentication[] auth = {
                SVNUserNameAuthentication.newInstance(user, false, repURL, false) };
            BasicAuthenticationManager authManager = new BasicAuthenticationManager(auth);
            repository.setAuthenticationManager(authManager);
            repository.testConnection();
            LOGGER.info("Returning getRepository() {}", repository);
            return repository;
        } catch (SVNException e) {
            throw new MCRPersistenceException("Failed to get repository!", e);
        }
    }

    /**
     * Returns the URL of the SVN repository used to manage metadata versions in
     * this store.
     *
     * @return the URL of the SVN repository used to manage metadata versions in
     *         this store.
     */
    SVNURL getRepositoryURL() {
        return repURL;
    }

    /**
     * Checks to local SVN repository for errors
     * @throws MCRPersistenceException if 'svn verify' fails
     */
    @Override
    public void verify() throws MCRPersistenceException, MCRUsageException {
        String replURLStr = repURL.toString();
        if (!repURL.getProtocol().equals("file")) {
            LOGGER.warn("Cannot verify non local SVN repository '{}'.", replURLStr);
            return;
        }
        try {
            SVNRepository repository = getRepository();
            long latestRevision = repository.getLatestRevision();
            if (latestRevision == 0) {
                LOGGER.warn("Cannot verify SVN repository '{}' with no revisions.", replURLStr);
            }
            ISVNAuthenticationManager authenticationManager = repository.getAuthenticationManager();
            SVNAdminClient adminClient = new SVNAdminClient(authenticationManager, null);
            File repositoryRoot = new File(URI.create(replURLStr));
            adminClient.setEventHandler(new ISVNAdminEventHandler() {
                //if more than batchSize revisions print progress
                int batchSize = 100;

                @Override
                public void checkCancelled() throws SVNCancelException {
                }

                @Override
                public void handleEvent(SVNEvent event, double progress) throws SVNException {
                }

                @Override
                public void handleAdminEvent(SVNAdminEvent event, double progress) throws SVNException {
                    if (event.getMessage() != null) {
                        if (event.getRevision() % batchSize != 0 || event.getRevision() == 0) {
                            LOGGER.debug(event::getMessage);
                        } else {
                            LOGGER.info("{} ({}% done)", event.getMessage(),
                                (int) (event.getRevision() * 100.0 / latestRevision));
                        }
                    }
                }
            });
            adminClient.doVerify(repositoryRoot);
            LOGGER.info("Verified SVN repository '{}'.", replURLStr);
        } catch (Exception e) {
            throw new MCRPersistenceException("SVN repository contains errors and could not be verified: " + replURLStr,
                e);
        }
    }

    private void commit(MCRMetadataVersionState type, MCRMetadata metadata, MCRContent content)
        throws MCRPersistenceException {
        LOGGER.info("Entering commit(MCRMetadataVersionState {}, MCRMetadata {}, MCRContent {})", type, metadata,
            content);
        String commitMsg = type.toString().toLowerCase() + " metadata object " + metadata.getFullID() + " in store";
        SVNCommitInfo info;
        try {
            SVNRepository repository = getRepository();

            if (type != MCRMetadataVersionState.DELETED) {
                String[] paths = getSlotPaths(metadata.getID());
                int existing = paths.length - 1;
                for (; existing >= 0; existing--) {
                    if (!repository.checkPath(paths[existing], -1).equals(SVNNodeKind.NONE)) {
                        break;
                    }
                }

                existing += 1;

                ISVNEditor editor = repository.getCommitEditor(commitMsg, null);
                editor.openRoot(-1);

                // Create directories in SVN that do not exist yet
                for (int i = existing; i < paths.length - 1; i++) {
                    LOGGER.debug("SVN create directory {}", paths[i]);
                    editor.addDir(paths[i], null, -1);
                    editor.closeDir();
                }

                // Commit file changes
                String filePath = paths[paths.length - 1];
                if (existing < paths.length) {
                    editor.addFile(filePath, null, -1);
                } else {
                    editor.openFile(filePath, -1);
                }

                editor.applyTextDelta(filePath, null);
                SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();

                String checksum;
                try (InputStream in = content.getContentInputStream()) {
                    checksum = deltaGenerator.sendDelta(filePath, in, editor, true);
                }

                if (shouldForceXML()) {
                    editor.changeFileProperty(filePath, SVNProperty.MIME_TYPE, SVNPropertyValue.create("text/xml"));
                }

                editor.closeFile(filePath, checksum);

                editor.closeDir(); // root
                info = editor.closeEdit();
            } else {
                ISVNEditor editor = repository.getCommitEditor(commitMsg, null);
                editor.openRoot(-1);

                editor.deleteEntry(getSlotPath(metadata.getID()), -1);

                editor.closeDir();
                info = editor.closeEdit();
            }
            LOGGER.info("SVN commit of {} finished, new revision {}", type.toString(), info.getNewRevision());
        } catch (SVNException | IOException | MCRPersistenceException e) {
            throw new MCRPersistenceException("Failed to commit changes!", e);
        }
    }

    public boolean existsSVN(MCRMetadata metadata) throws MCRPersistenceException {
        Long requestedRevision = metadata.getRevision();
        try {
            LOGGER.warn("ID {}, Revision {}, Path {}", metadata.getFullID(),
                requestedRevision != null ? requestedRevision : "any",
                "/" + getSlotPath(metadata.getID()));
            List<MCRMetadataVersion> versions;
            try {
                versions = getMetadataVersions(metadata.getID());
            } catch (MCRPersistenceException e) {
                return false;
            }
            LOGGER.info("requestedRevision {}", requestedRevision);
            LOGGER.info("versions.size() {}", versions.size());
            if (requestedRevision == null && versions.size() > 0) {
                return true;
            } else if (requestedRevision != null && versions.size() >= requestedRevision) {
                return true;
            } else {
                return false;
            }
        } catch (MCRPersistenceException e) {
            e.printStackTrace();
            throw new MCRPersistenceException("Failed to check if ID " + metadata.getFullID() + " exists!", e);
        }
    }

    /**
     * Updates the version stored in the local filesystem to the latest version
     * from Subversion repository HEAD.
     */
    public void updateAll() throws Exception {
        SVNRepository repository = getRepository();
        for (Iterator<Integer> ids = listIDs(true); ids.hasNext();) {
            MCRByteArrayOutputStream baos = new MCRByteArrayOutputStream();
            int id = ids.next();
            repository.getFile("/" + getSlotPath(id), -1, null, baos);
            baos.close();
            MCRMetadata metadata = new MCRMetadata(this, id);
            super.updateContent(metadata, new MCRByteContent(baos.getBuffer(), 0, baos.size(),
                metadata.getLastModified().getTime()));
        }
    }

    /**
     * Checks if the version of an object in the local store is up to date with the latest
     * version in SVN repository
     *
     * @return true, if the local version in store is the latest version
     */
    public boolean isUpToDate(int id) throws MCRPersistenceException, IOException {
        MCRContent contentSVN, contentXML;
        contentSVN = readContent(retrieve(id));
        contentXML = super.readContent(super.retrieve(id));
        return (contentSVN != null) && (contentXML != null)
            && (contentSVN.asByteArray() == contentXML.asByteArray());
    }

    @Override
    public void createContent(MCRMetadata metadata, MCRContent content)
        throws MCRPersistenceException, MCRUsageException {
        if (content == null) {
            throw new MCRUsageException("Cannot update " + metadata.getFullID() + " in repository with null content!");
        }
        super.createContent(metadata, content);
        commit(MCRMetadataVersionState.CREATED, metadata, content);
    }

    // TODO DRY for repo-iteration code
    @Override
    @Nullable
    public MCRContent readContent(MCRMetadata metadata) throws MCRPersistenceException {
        if (metadata.getRevision() == null) {
            MCRContent content = super.readContent(metadata);
            if (content != null) {
                return content;
            }
        }
        LOGGER.warn("Object {} not found in XML store, trying SVN repository.", metadata.getFullID());
        if (!existsSVN(metadata)) {
            throw new MCRUsageException(
                "Object " + metadata.getFullID() + " does not exist in store!");
        }

        int requestedID = metadata.getID();
        Long requestedRevision = metadata.getRevision();
        long visitedRevisions = 0L;
        String filepath = "/" + getSlotPath(requestedID);
        String dirpath = filepath.substring(0, filepath.lastIndexOf('/'));
        if (requestedRevision == null) {
            throw new MCRUsageException("No revision specified!");
        }
        MCRMetadataVersion requestedVersion = getMetadataVersion(requestedID, requestedRevision);
        if (requestedVersion.getState() == MCRMetadataVersionState.DELETED) {
            LOGGER.info("Requested revision {} is deleted, has no content!");
            return null;
        }
        try {
            SVNRepository repository = getRepository();
            @SuppressWarnings("unchecked")
            List<SVNLogEntry> listRevisions = new ArrayList<SVNLogEntry>(
                repository.log(new String[] { dirpath }, null, 0, -1L, true, true));

            LOGGER.info("listRevisions size: {}", listRevisions.size());
            for (SVNLogEntry svnEntry : listRevisions) {
                SVNLogEntryPath svnLogEntryPath = svnEntry.getChangedPaths().get(filepath);
                if (svnLogEntryPath != null) {
                    ++visitedRevisions;
                    if (visitedRevisions == requestedRevision) {
                        MCRByteArrayOutputStream baos = new MCRByteArrayOutputStream();
                        repository.getFile("/" + getSlotPath(requestedID), svnEntry.getRevision(), null, baos);
                        baos.close();
                        return new MCRByteContent(baos.getBuffer(), 0, baos.size(),
                            requestedVersion.getDate().getTime());
                    }
                }
            }
            throw new MCRPersistenceException(
                "Requested revision " + requestedRevision + " not found, last revision is " + visitedRevisions + "!");
        } catch (SVNException | IOException | MCRPersistenceException e) {
            throw new MCRPersistenceException("Failed to read content!", e);
        }
    }

    @Override
    public void updateContent(MCRMetadata metadata, MCRContent content)
        throws MCRPersistenceException, MCRUsageException {
        if (content == null) {
            throw new MCRUsageException("Cannot update " + metadata.getFullID() + " in repository with null content!");
        }
        if (metadata.isDeletedInRepository()) {
            super.createContent(metadata, content);
        } else {
            super.updateContent(metadata, content);
        }
        commit(MCRMetadataVersionState.UPDATED, metadata, content);
    }

    @Override
    public void deleteContent(MCRMetadata metadata) throws MCRPersistenceException {
        SVNCommitInfo info;
        try {
            SVNRepository repository = getRepository();
            ISVNEditor editor = repository.getCommitEditor("Deleting object " + metadata.getFullID() + " in store.",
                null);
            editor.openRoot(-1);
            editor.deleteEntry("/" + getSlotPath(metadata.getID()), -1);
            editor.closeDir();

            info = editor.closeEdit();
            LOGGER.info("SVN commit of delete finished, new revision {}", info.getNewRevision());
        } catch (SVNException e) {
            LOGGER.error("Error while deleting {} in SVN ", metadata.getFullID(), e);
        } finally {
            super.deleteContent(metadata);
        }
    }

    private static final Map<String, MCRMetadataVersionState> TYPE_STATE_MAPPING = Stream.of(
        new AbstractMap.SimpleImmutableEntry<>("A", MCRMetadataVersionState.CREATED),
        new AbstractMap.SimpleImmutableEntry<>("M", MCRMetadataVersionState.UPDATED),
        new AbstractMap.SimpleImmutableEntry<>("R", MCRMetadataVersionState.UPDATED),
        new AbstractMap.SimpleImmutableEntry<>("D", MCRMetadataVersionState.DELETED))
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    @Override
    public List<MCRMetadataVersion> getMetadataVersions(int id) throws MCRPersistenceException {
        long visitedRevisions = 0L;
        String filepath = "/" + getSlotPath(id);
        String dirpath = filepath.substring(0, filepath.lastIndexOf('/'));
        try {
            SVNRepository repository = getRepository();
            @SuppressWarnings("unchecked")
            List<SVNLogEntry> listRevisions = new ArrayList<SVNLogEntry>(
                repository.log(new String[] { dirpath }, null, 0, -1L, true, true));
            List<MCRMetadataVersion> listMetadataVersions = new ArrayList<MCRMetadataVersion>();

            for (SVNLogEntry svnEntry : listRevisions) {
                SVNLogEntryPath svnLogEntryPath = svnEntry.getChangedPaths().get(filepath);
                if (svnLogEntryPath != null) {
                    ++visitedRevisions;
                    MCRMetadataVersionState state = TYPE_STATE_MAPPING.get(String.valueOf(svnLogEntryPath.getType()));
                    LOGGER.info("Base: {}", this.getID());
                    LOGGER.info("ID: {}", id);
                    LOGGER.info("Revision: {}", visitedRevisions);
                    LOGGER.info("User: {}", svnEntry.getAuthor());
                    LOGGER.info("Date: {}", svnEntry.getDate());
                    LOGGER.info("State: {}", state.toString());
                    listMetadataVersions.add(new MCRMetadataVersion(new MCRMetadata(this, id, visitedRevisions),
                        visitedRevisions, svnEntry.getAuthor(), svnEntry.getDate(), state));
                }
            }
            return listMetadataVersions;
        } catch (SVNException e) {
            throw new MCRPersistenceException(
                "Failed to enumerate revisions of " + getID() + "_" + createIDWithLeadingZeros(id) + "!", e);
        }
    }

    // TODO DRY for repo-iteration code
    @Override
    public Date getLastModified(MCRMetadata metadata) throws MCRPersistenceException {
        Long requestedRevision = metadata.getRevision();
        long revision = ((requestedRevision != null) ? requestedRevision : -1L);

        long visitedRevisions = 0L;
        String filepath = "/" + getSlotPath(metadata.getID());
        String dirpath = filepath.substring(0, filepath.lastIndexOf('/'));
        if (requestedRevision == null) {
            LOGGER.warn("No revision specified, defaulting to last revision.");
        }
        try {
            SVNRepository repository = getRepository();
            @SuppressWarnings("unchecked")
            List<SVNLogEntry> listRevisions = new ArrayList<SVNLogEntry>(
                repository.log(new String[] { dirpath }, null, 0, -1L, true, true));

            LOGGER.info("listRevisions size: {}", listRevisions.size());
            for (SVNLogEntry svnEntry : listRevisions) {
                SVNLogEntryPath svnLogEntryPath = svnEntry.getChangedPaths().get(filepath);
                if (svnLogEntryPath != null) {
                    ++visitedRevisions;
                    if (visitedRevisions == requestedRevision) {
                        LOGGER.error(svnEntry.getDate());
                        return svnEntry.getDate();
                    }
                }
            }
            throw new MCRPersistenceException(
                "Requested revision " + revision + " not found, last revision is " + visitedRevisions + "!");
        } catch (SVNException | MCRPersistenceException e) {
            throw new MCRPersistenceException("Failed to read date!", e);
        }
    }

    @Override
    protected void setLastModified(MCRMetadata mcrMetadata, Date date) throws MCRUsageException {
        LOGGER.info("Overriding commit date is not supported by SVN, ignoring setLastModified call.");
    }

}
