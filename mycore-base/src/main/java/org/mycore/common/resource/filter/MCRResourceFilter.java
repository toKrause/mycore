/*
 *
 * $Revision$ $Date$
 *
 * This file is part of *** M y C o R e *** See http://www.mycore.de/ for
 * details.
 *
 * This program is free software; you can use it, redistribute it and / or
 * modify it under the terms of the GNU General Public License (GPL) as
 * published by the Free Software Foundation; either version 2 of the License or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program, in a file called gpl.txt or license.txt. If not, write to the
 * Free Software Foundation Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307 USA
 */

package org.mycore.common.resource.filter;

import java.net.URL;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.mycore.common.hint.MCRHints;
import org.mycore.common.log.MCRTreeMessage;
import org.mycore.common.resource.provider.MCRLFSResourceProvider;

/**
 * A {@link MCRResourceFilter} implements the <em>filter</em>-phase for a {@link MCRLFSResourceProvider}.
 */
public interface MCRResourceFilter {

    /**
     * Reduces the set of possible candidates to the set of allowed candidates.
     */
    Stream<URL> filter(Stream<URL> resourceUrls, MCRHints hints);

    /**
     * Returns a description of this {@link MCRCombinedResourceFilter}.
     */
    MCRTreeMessage compileDescription(Level level);

}
