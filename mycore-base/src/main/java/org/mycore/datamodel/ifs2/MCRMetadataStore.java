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
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.MCRPersistenceException;
import org.mycore.common.MCRUsageException;
import org.mycore.common.config.MCRConfiguration2;
import org.mycore.common.content.MCRContent;

/**
 * TODO rewrite
 * 
 * Stores XML metadata documents (or optionally any other BLOB data) in a
 * persistent filesystem structure
 * 
 * For each object type, a store must be defined as follows:
 * 
 * MCR.IFS2.Store.DocPortal_document.Class=org.mycore.datamodel.ifs2.MCRMetadataStore 
 * MCR.IFS2.Store.DocPortal_document.BaseDir=/foo/bar
 * MCR.IFS2.Store.DocPortal_document.SlotLayout=4-2-2 
 * MCR.IFS2.Store.DocPortal_document.ForceXML=true (which is default)
 * 
 * @author Frank Lützenkirchen
 */
public abstract class MCRMetadataStore<T> extends MCRStore {

    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * If true (which is default), store will enforce it gets
     * XML to store, otherwise any binary content can be stored here.
     * 
     * Override with {@code MCR.IFS2.Store.<ObjectType>.ForceXML=true|false}
     */
    protected boolean forceXML = true;

    protected String forceDocType;

    /**
     * Initializes a new metadata store instance.
     * 
     * @param type
     *            the document type that is stored in this store
     */
    @Override
    protected void init(String type) {
        super.init(type);
        prefix = MCRConfiguration2.getString("MCR.IFS2.Store." + type + ".Prefix").orElse(type + "_");
        suffix = ".xml";
        forceXML = MCRConfiguration2.getBoolean("MCR.IFS2.Store." + type + ".ForceXML").orElse(true);
        if (forceXML) {
            forceDocType = MCRConfiguration2.getString("MCR.IFS2.Store." + type + ".ForceDocType").orElse(null);
            LOGGER.debug("Set doctype for {} to {}", type, forceDocType);
        }
    }

    /**
     * Initializes a new metadata store instance.
     * 
     * @param config
     *            the configuration for the store
     */
    @Override
    protected void init(MCRStoreConfig config) {
        super.init(config);
        prefix = Optional.ofNullable(config.getPrefix()).orElseGet(() -> config.getID() + "_");
        suffix = ".xml";
        forceXML = MCRConfiguration2.getBoolean("MCR.IFS2.Store." + config.getID() + ".ForceXML").orElse(true);
        if (forceXML) {
            forceDocType = MCRConfiguration2.getString("MCR.IFS2.Store." + config.getID() + ".ForceDocType")
                .orElse(null);
            LOGGER.debug("Set doctype for {} to {}", config.getID(), forceDocType);
        }
    }

    /**
     * Stores a newly created document, using the next free ID.
     * 
     * @param content
     *            the content to be stored
     * @return the stored metadata object
     */
    public MCRMetadata create(MCRContent content) throws MCRPersistenceException {
        return create(getNextFreeID(), content);
    }

    /**
     * Stores a newly created document under the given ID.
     * 
     * @param content
     *            the content to be stored
     * @param id
     *            the ID under which the document should be stored
     * @return the stored metadata object
     */
    public MCRMetadata create(int id, MCRContent content) throws MCRPersistenceException {
        MCRMetadata metadata = new MCRMetadata(this, id);
        metadata.create(content);
        return metadata;
    }

    /**
     * Returns the metadata stored under the given ID, or null
     * 
     * @param id
     *            the ID of the XML document
     * @return the metadata stored under that ID, or null when there is no such
     *         metadata object
     */
    public MCRMetadata retrieve(int id) throws MCRPersistenceException {
        if (exists(id)) {
            MCRMetadata metadata = new MCRMetadata(this, id);
            metadata.read();
            return metadata;
        } else {
            return null;
        }
    }

    public MCRMetadata update(int id, MCRContent content) throws MCRPersistenceException {
        MCRMetadata metadata = new MCRMetadata(this, id);
        metadata.update(content);
        return metadata;
    }

    public void delete(int id) throws MCRPersistenceException {
        new MCRMetadata(this, id).delete();
    }

    @Override
    public boolean exists(final int id) throws MCRPersistenceException {
        return exists(new MCRMetadata(this, id));
    }

    public abstract boolean exists(MCRMetadata metadata) throws MCRPersistenceException;

    /**
     * Creates an object in the underlying repository system.
     * 
     * This implementation is a stub for input verification, please {@code @Override} this method with your store-specific implementation and call {@code super.createContent(metadata, content)} to use these checks. 
     * @param metadata
     * @param content
     * @throws MCRPersistenceException
     * @throws MCRUsageException
     */
    protected abstract void createContent(MCRMetadata metadata, MCRContent content)
        throws MCRPersistenceException, MCRUsageException;

    protected abstract MCRContent readContent(MCRMetadata metadata) throws MCRPersistenceException, MCRUsageException;

    protected abstract void updateContent(MCRMetadata metadata, MCRContent content) throws MCRPersistenceException;

    protected abstract void deleteContent(MCRMetadata metadata) throws MCRPersistenceException;

    public abstract List<MCRMetadataVersion> getMetadataVersions(int id) throws MCRPersistenceException;

    protected abstract T getRepository() throws MCRPersistenceException;

    public abstract void verify() throws MCRPersistenceException, MCRUsageException;

    public Date getLastModified(int id) throws MCRPersistenceException {
        return getLastModified(new MCRMetadata(this, id));
    }

    public abstract Date getLastModified(MCRMetadata metadata) throws MCRPersistenceException;

    protected abstract void setLastModified(MCRMetadata mcrMetadata, Date date) throws MCRUsageException;

    public boolean shouldForceXML() {
        return forceXML;
    }

    public String getDocType() {
        return forceDocType;
    }

    public MCRMetadataVersion getMetadataVersion(int id, long revision) throws MCRPersistenceException {
        return getMetadataVersions(id).get((int) (revision - 1));
    }

    public MCRMetadataVersion getMetadataVersionFirst(int id) throws MCRPersistenceException {
        List<MCRMetadataVersion> list = getMetadataVersions(id);
        if (list.size() < 1) {
            return null;
        } else {
            return list.get(0);
        }
    }

    public MCRMetadataVersion getMetadataVersionLast(int id) throws MCRPersistenceException {
        List<MCRMetadataVersion> list = getMetadataVersions(id);
        if (list.size() < 1) {
            return null;
        } else {
            return list.get(list.size() - 1);
        }
    }

    protected final void checkIDPermitted(int id) throws MCRUsageException {
        if (id < 1)
            throw new MCRUsageException("ID " + id + " must be >= 1!");
    }
}
