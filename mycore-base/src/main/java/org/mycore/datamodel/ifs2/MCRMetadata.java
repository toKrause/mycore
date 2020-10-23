package org.mycore.datamodel.ifs2;

import java.util.Date;
import java.util.List;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.MCRUsageException;
import org.mycore.common.content.MCRContent;
import org.mycore.datamodel.ifs2.MCRMetadataVersion.MCRMetadataVersionState;

/**
 * An object-focused, generic interface to <code>{@link MCRMetadataStore}</code> extensions.</br>
 * 
 * An <code>MCRMetadata</code> instance represents an object that is (to be) stored in a
 * <code>MCRMetadataStore</code>. It has an immutable 
 * 
 * @author bt1cn
 *
 */
public class MCRMetadata {

    protected static final Logger LOGGER = LogManager.getLogger();

    private final MCRMetadataStore store;

    private final String base;

    private final String docType;

    private final int id;

    private final String fullID;

    private boolean updated;

    @Nullable
    protected Long revision;

    @Nullable
    protected MCRContent content;

    @Nullable
    protected Date date;

    public MCRMetadata(MCRMetadataStore store, int id) {
        this.store = store;
        base = store.getID();
        docType = store.getDocType();
        this.id = id;
        fullID = base + "_" + store.createIDWithLeadingZeros(id);

        this.updated = false;
    }

    public MCRMetadata(MCRMetadataStore store, int id, long revision) {
        this(store, id);
        this.revision = revision;
        this.date = store.getLastModified(this);
    }

    public void create(MCRContent content) throws MCRPersistenceException {
        try {
            store.createContent(this, content);
            this.content = content;
            revision = getVersionLast().getRevision();
            date = store.getLastModified(this);
            updated = true;
        } catch (MCRPersistenceException e) {
            updated = false;
            e.printStackTrace();
            throw new MCRPersistenceException(
                "Failed to create " + getFullID() + " in revision " + revision + " in store!", e);
        }
    }

    public MCRContent read() throws MCRPersistenceException {
        try {
            if (revision == null) {
                LOGGER.info("No revision to fetch specified, defaulting to the latest.");
                revision = getVersionLast().getRevision();
            }
            content = store.readContent(this);
            date = store.getLastModified(this);
            updated = true;
            return content;
        } catch (MCRPersistenceException e) {
            updated = false;
            e.printStackTrace();
            throw new MCRPersistenceException(
                "Failed to read " + getFullID() + " in revision " + revision + " from store!", e);
        }
    }

    public void update(MCRContent content) throws MCRPersistenceException {
        try {
            store.updateContent(this, content);
            revision = getVersionLast().getRevision();
            date = store.getLastModified(this);
            this.content = content;
            updated = true;
        } catch (MCRPersistenceException e) {
            updated = false;
            e.printStackTrace();
            throw new MCRPersistenceException(
                "Failed to update " + getFullID() + " in revision " + revision + " in store!", e);
        }
    }

    public void delete() throws MCRPersistenceException {
        try {
            store.deleteContent(this);
            MCRMetadataVersion newVersion = getVersionLast();
            if (newVersion != null) {
                revision = newVersion.getRevision();
                date = store.getLastModified(this);
                this.content = null;
                updated = true;
            }
        } catch (MCRPersistenceException e) {
            updated = false;
            e.printStackTrace();
            throw new MCRPersistenceException(
                "Failed to delete " + getFullID() + " in revision " + revision + " from store!", e);
        }
    }

    public boolean isUpdated() {
        return updated;
    }

    public String getBase() {
        return base;
    }

    public String getDocType() {
        return docType;
    }

    public int getID() {
        return id;
    }

    public Long getRevision() {
        return revision;
    }

    public Date getLastModified() {
        return date;
    }

    /* HELPERS / COMPATIBILITY */

    public String getFullID() {
        return fullID;
    }

    public boolean isDeleted() {
        return getVersion().getState() == MCRMetadataVersionState.DELETED;
    }

    public boolean isDeletedInRepository() {
        return getVersionLast().getState() == MCRMetadataVersionState.DELETED;
    }

    public List<MCRMetadataVersion> listVersions() throws MCRPersistenceException {
        return store.getMetadataVersions(id);
    }

    public void restore() throws MCRPersistenceException {
        getVersionLast().getMetadata().update(read());
    }

    public MCRMetadataVersion getVersion() throws MCRPersistenceException {
        return store.getMetadataVersion(id, revision);
    }

    public MCRMetadataVersion getVersion(long revision) throws MCRPersistenceException {
        return store.getMetadataVersion(id, revision);
    }

    public MCRMetadataVersion getVersionLast() throws MCRPersistenceException {
        return store.getMetadataVersionLast(id);
    }

    public void setLastModified(Date date) throws MCRUsageException {
        store.setLastModified(this, date);
    }

}
