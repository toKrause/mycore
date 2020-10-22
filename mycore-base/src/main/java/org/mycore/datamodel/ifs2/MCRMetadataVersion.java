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

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Provides information about the revision of a metadata object at the time of the request.
 * 
 * This includes the revision number, date, state ({@link MCRMetadataVersionState}),
 * the committer and a reference to the metadata object itself. ({@link MCRMetadata}) 
 * 
 * @author Frank Lützenkirchen
 * @author Christoph Neidahl (OPNA2608)
 */
@XmlRootElement(name = "revision")
@XmlAccessorType(XmlAccessType.FIELD)
public class MCRMetadataVersion {

    protected static final Logger LOGGER = LogManager.getLogger();

    /**
     * The state of the metadata object as described by this version.
     * 
     * <ul>
     *   <li><p>CREATED - Initial version</p>
     *       <p>(default if the parent {@link MCRMetadataStore} does not implement a version control system)</p></li>
     *   <li>MODIFIED - Metadata has been revised</li>
     *   <li>DELETED - Metadata was deleted</li>
     * </ul>
     * - 
     * @author Christoph Neidahl (OPNA2608)
     *
     */
    public enum MCRMetadataVersionState {
        CREATED,
        UPDATED,
        DELETED
    }

    /**
     * The metadata document this version belongs to.
     */
    @XmlTransient
    private final MCRMetadata metadata;

    /**
     * The revision number of this version
     */
    @XmlAttribute(name = "r")
    private final long revision;

    /**
     * The user that created this version
     */
    @XmlAttribute
    private final String user;

    /**
     * The date this version was created
     */
    @XmlAttribute
    private final Date date;

    /**
     * Was this version result of a create, update or delete?
     */
    @XmlAttribute()
    private final MCRMetadataVersionState state;

    /**
     * Creates a new metadata version info object
     * 
     * @param vm
     *            the metadata document this version belongs to
     * @param logEntry
     *            the log entry from SVN holding data on this version
     * @param type
     *            the type of commit
     */
    public MCRMetadataVersion(MCRMetadata metadata, long revision, String user, Date date,
        MCRMetadataVersionState state) {
        LOGGER.debug("Instantiating version info for {}_{}, revision {}.", metadata.getBase(), metadata.getID(),
            revision);
        this.metadata = metadata;
        this.revision = revision;
        this.user = user;
        this.date = date;
        this.state = state;
    }

    /**
     * Returns the metadata object this version belongs to.
     * 
     * @return the metadata object this version belongs to
     */
    public final MCRMetadata getMetadata() {
        return metadata;
    }

    /**
     * Returns the revision number of this version.
     * 
     * @return the revision number of this version
     */
    public long getRevision() {
        return revision;
    }

    /**
     * Returns the user that created this version.
     * 
     * @return the user that created this version
     */
    public String getUser() {
        return user;
    }

    /**
     * Returns the date and time this version was created.
     * 
     * @return the date and time this version was created
     */
    public Date getDate() {
        return date;
    }

    /**
     * Returns the state this version represents.
     * 
     * @return the state this version represents
     */
    public MCRMetadataVersionState getState() {
        return state;
    }

    /* HELPERS / COMPATIBILITY */

    public char getType() {
        return state.toString().charAt(0);
    }

    public void restore() {
        getMetadata().restore();
    }
}
