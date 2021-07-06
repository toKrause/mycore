/*
 * This file is part of ***  M y C o R e  ***
 * See http://www.mycore.de/ for details.
 *
 * This program is free software; you can use it, redistribute it
 * and / or modify it under the terms of the GNU General Public License
 * (GPL) as published by the Free Software Foundation; either version 2
 * of the License or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program, in a file called gpl.txt or license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 */

package org.mycore.mcr.acl.accesskey.backend;

import static org.junit.Assert.assertEquals;

import java.util.Map;

import org.junit.Test;

import org.mycore.access.MCRAccessManager;
import org.mycore.common.MCRTestCase;
import org.mycore.datamodel.metadata.MCRObjectID;

public class MCRAccessKeyTest extends MCRTestCase {

    private static final String MCR_OBJECT_ID = "mcr_test_00000001";

    private static final String READ_KEY = "blah";

    @Override
    protected Map<String, String> getTestProperties() {
        Map<String, String> testProperties = super.getTestProperties();
        testProperties.put("MCR.Metadata.Type.test", Boolean.TRUE.toString());
        return testProperties;
    }

    @Test
    public void testKey() {
        final MCRAccessKey accessKey = 
            new MCRAccessKey(MCRObjectID.getInstance(MCR_OBJECT_ID), READ_KEY, MCRAccessManager.PERMISSION_READ);
        assertEquals(MCR_OBJECT_ID, accessKey.getObjectId().toString());
        assertEquals(READ_KEY, accessKey.getValue());
        assertEquals(MCRAccessManager.PERMISSION_READ, accessKey.getType());
    }
}
