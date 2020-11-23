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

import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.MCRUsageException;
import org.mycore.common.content.MCRContent;
import org.mycore.datamodel.ifs2.MCRMetadataVersion.MCRMetadataVersionState;

/**
 * An object-focused, generic interface to {@link MCRMetadataStore} extensions.
 * 
 * An MCRMetadata instance represents an object that is (to be) stored in a
 * MCRMetadataStore. It has an immutable set of information about its associated store, name and ID.
 * 
 * @author Christoph Neidahl (OPNA2608)
 *
 */
public class MCRMetadata {

    protected static final Logger LOGGER = LogManager.getLogger();

    /** The {@link MCRMetadataStore} object this MCRMetadata object belongs to. */
    private final MCRMetadataStore store;

    /** The linked MCRMetadataStore's base. For convenience. */
    private final String base;

    /** The linked MCRMetadataStore's enforced document type. For convenience. */
    private final String docType;

    /** This object's ID. */
    private final int id;

    /** The object's full ID: {base}_{id}. For convenience. */
    private final String fullID;

    /** Sync indicator. Set to <code>false</code> if this object's information and the linked store's data
     * differ or haven't been checked yet. This can happen for a multitude of reasons:
     * <ul>
     *   <li>
     *     The object has just been initialized and no CRUD operations
     *     have been done against the store yet.
     *   </li>
     *   <li>
     *     An {@link MCRPersistenceException} has occurred while communicating with the store.
     *   </li>
     *   <li>
     *     <p>
     *       The {@link #delete()} method has been run and {@link #getVersionLast()} succeeded
     *       without an exception, but the store was unable to hand us back the last version of this object.
     *     </p>
     *     <p>
     *       This is normal behaviour for a store that does not use a versioned storage system,
     *       for example {@link MCRXMLMetadataStore}.
     *   </li>
     * </ul>
     */
    private boolean updated;

    /** The current revision of the MCRObject that this instance depicts.
     * May be <code>null</code> if none has been specified at object instantiation, or if the
     * {@link #delete()} method has been called and the linked store's implementation of
     * {@link MCRMetadataStore#deleteContent(MCRMetadata)} purges all versioning-related information.
     * 
     *  @see #store
     */
    protected Long revision;

    /** The current revision's file contents.
     * May be <code>null</code> if no CRU method has been called yet, or if
     * {@link #delete()} has been called successfully.
     */
    protected MCRContent content;

    /** The current revision's commit date.
     * May be <code>null</code> if no CRU method has been called yet, or if the
     * {@link #delete()} method has been called and the linked store's implementation of
     * {@link MCRMetadataStore#deleteContent(MCRMetadata)} purges all versioning-related information.
     */
    protected Date date;

    /**
     * Instantiates a metadata object.
     * 
     * @param store
     *     the {@link MCRMetadataStore} instance this object should talk to for CRUD operations at the likes
     * @param id
     *     the ID this object should refer to
     * 
     * @see #MCRMetadata(MCRMetadataStore, int, long)
     */
    public MCRMetadata(MCRMetadataStore store, int id) {
        this.store = store;
        base = store.getID();
        docType = store.getDocType();
        this.id = id;
        fullID = base + "_" + store.createIDWithLeadingZeros(id);

        this.updated = false;
    }

    /**
     * Instantiates a metadata object of a specific revision.
     * 
     * @param store
     *     the {@link MCRMetadataStore} instance this object should talk to for CRUD operations at the likes
     * @param id
     *     the ID this object should refer to
     * @param revision
     *     the revision this object will identify itself as
     * 
     * @see #MCRMetadata(MCRMetadataStore, int)
     */
    public MCRMetadata(MCRMetadataStore store, int id, long revision) {
        this(store, id);
        this.revision = revision;
        this.date = store.getLastModified(this);
    }

    /**
     * Communicates with the specified store instance to create this object.
     * 
     * @param content
     *     the content of this object
     * @throws MCRPersistenceException
     *     if the store encounters a problem while storing an object with this ID (and revision, if supported)
     *     with the specified content
     */
    public void create(MCRContent content) throws MCRPersistenceException {
        try {
            store.createContent(this, content);
            this.content = content;
            revision = getVersionLast().getRevision();
            date = store.getLastModified(this);
            updated = true;
        } catch (MCRPersistenceException e) {
            updated = false;
            throw new MCRPersistenceException(
                "Failed to create " + getFullID() + " in revision " + revision + " in store!", e);
        }
    }

    /**
     * Communicates with the specified store instance to read this object's content.
     * 
     * @return the content stored under this object ID &amp; revision
     * @throws MCRPersistenceException
     *     if the store encounters a problem while reading the stored object with this ID and revision
     */
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
            throw new MCRPersistenceException(
                "Failed to read " + getFullID() + " in revision " + revision + " from store!", e);
        }
    }

    /**
     * Communicates with the specified store instance to update this object.
     * 
     * @param content
     *     the new content of this object
     * @throws MCRPersistenceException
     *     if the store encounters a problem while updating the stored object of the specified ID
     *     (and revision, if supported) with the specified content
     */
    public void update(MCRContent content) throws MCRPersistenceException {
        try {
            store.updateContent(this, content);
            revision = getVersionLast().getRevision();
            date = store.getLastModified(this);
            this.content = content;
            updated = true;
        } catch (MCRPersistenceException e) {
            updated = false;
            throw new MCRPersistenceException(
                "Failed to update " + getFullID() + " in revision " + revision + " in store!", e);
        }
    }

    /**
     * Communicates with the specified store instance to delete this object.
     * 
     * @throws MCRPersistenceException
     *     if the store encounters a problem while deleting the stored object of the specified ID
     *     (and revision, if supported)
     */
    public void delete() throws MCRPersistenceException {
        try {
            store.deleteContent(this);
            MCRMetadataVersion newVersion = getVersionLast();
            if (newVersion != null) {
                revision = newVersion.getRevision();
                date = store.getLastModified(this);
            } else {
                revision = null;
                date = null;
            }
            this.content = null;
            updated = true;
        } catch (MCRPersistenceException e) {
            updated = false;
            throw new MCRPersistenceException(
                "Failed to delete " + getFullID() + " in revision " + revision + " from store!", e);
        }
    }

    /**
     * Whether or not this object's state has been synchronized with the store.
     * 
     * @return whether it's updated or not
     * 
     * @see #updated
     * 
     */
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

    public String getFullID() {
        return fullID;
    }

    /**
     * Whether the revision this object refers to is currently considered deleted by the store.
     * 
     * @return whether this object revision's state == {@link MCRMetadataVersionState#DELETED}
     */
    public boolean isDeleted() {
        return getVersion().getState() == MCRMetadataVersionState.DELETED;
    }

    /**
     * Whether the latest available revision of this object is currently considered deleted by the store.
     * 
     * @return whether the latest object revision's state == {@link MCRMetadataVersionState#DELETED}
     */
    public boolean isDeletedInRepository() {
        return getVersionLast().getState() == MCRMetadataVersionState.DELETED;
    }

    public List<MCRMetadataVersion> listVersions() throws MCRPersistenceException {
        return store.getMetadataVersions(id);
    }

    /**
     * Shortcut for reading this revision's content and running {@link #update(MCRContent)} on the latest
     * revision of this object
     * 
     * @throws MCRPersistenceException
     *     if any error occurs while restoring this revision's content
     */
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
