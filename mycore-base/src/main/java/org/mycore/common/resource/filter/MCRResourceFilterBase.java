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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.hint.MCRHints;
import org.mycore.common.log.MCRTreeMessage;

/**
 * {@link MCRResourceFilterBase} is a base implementation of {@link MCRResourceFilter} that
 * facilitates consistent logging. Implementors must provide a class-specific {@link Logger} and the
 * actual filtering strategy ({@link MCRResourceFilterBase#doFilter(Stream, MCRHints)}).
 */
public abstract class MCRResourceFilterBase implements MCRResourceFilter {

    private final Logger logger = LogManager.getLogger();

    @Override
    public Stream<URL> filter(Stream<URL> resourceUrls, MCRHints hints) {
        logger.debug("Filtering resource URLs");
        Stream<URL> filteredResourceUrls = doFilter(resourceUrls, hints);
        if (logger.isDebugEnabled()) {
            return logResourceUrls(filteredResourceUrls.collect(Collectors.toList())).stream();
        } else {
            return filteredResourceUrls;
        }
    }

    private List<URL> logResourceUrls(List<URL> resourceUrls) {
        for (URL resourceUrl : resourceUrls) {
            logger.debug("Keeping resource URL {}", resourceUrl);
        }
        return resourceUrls;
    }

    protected final Logger getLogger() {
        return logger;
    }

    protected abstract Stream<URL> doFilter(Stream<URL> resourceUrls, MCRHints hints);

    @Override
    public MCRTreeMessage compileDescription(Level level) {
        MCRTreeMessage description = new MCRTreeMessage();
        description.add("Class", getClass().getName());
        return description;
    }

}
