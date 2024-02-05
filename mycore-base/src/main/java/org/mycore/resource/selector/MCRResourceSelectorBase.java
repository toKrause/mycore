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

package org.mycore.resource.selector;

import java.net.URL;
import java.util.List;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mycore.common.hint.MCRHints;
import org.mycore.common.log.MCRTreeMessage;

/**
 * {@link MCRResourceSelectorBase} is a base implementation of {@link MCRResourceSelector} that
 * facilitates consistent logging. Implementors must provide a class-specific {@link Logger} and the
 * actual selection strategy ({@link MCRResourceSelectorBase#doSelect(List, MCRHints)}).
 */
public abstract class MCRResourceSelectorBase implements MCRResourceSelector {

    private final Logger logger = LogManager.getLogger(getClass());

    @Override
    public List<URL> select(List<URL> resourceUrls, MCRHints hints) {
        logger.debug("Selecting resource URLs");
        List<URL> selectedResourceUrls = doSelect(resourceUrls, hints);
        if (selectedResourceUrls.isEmpty()) {
            selectedResourceUrls = resourceUrls;
        }
        if (logger.isDebugEnabled()) {
            return logResourceUrls(selectedResourceUrls);
        } else {
            return selectedResourceUrls;
        }
    }

    private List<URL> logResourceUrls(List<URL> resourceUrls) {
        for (URL resourceUrl : resourceUrls) {
            logger.debug("Selected resource URL {}", resourceUrl);
        }
        return resourceUrls;
    }

    protected final Logger getLogger() {
        return logger;
    }

    protected abstract List<URL> doSelect(List<URL> resourceUrls, MCRHints hints);

    @Override
    public MCRTreeMessage compileDescription(Level level) {
        MCRTreeMessage description = new MCRTreeMessage();
        description.add("Class", getClass().getName());
        return description;
    }

}
