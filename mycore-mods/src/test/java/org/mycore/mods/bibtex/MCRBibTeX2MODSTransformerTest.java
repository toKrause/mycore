/*
 * $Revision$ $Date$
 *
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

package org.mycore.mods.bibtex;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.junit.Test;
import org.mycore.common.MCRConstants;
import org.mycore.common.MCRTestCase;
import org.mycore.common.content.MCRFileContent;
import org.mycore.common.content.MCRJDOMContent;
import org.xml.sax.SAXException;

public class MCRBibTeX2MODSTransformerTest extends MCRTestCase {

    @Test
    public void testTransformation() throws UnsupportedEncodingException, IOException, JDOMException, SAXException {
        ClassLoader classLoader = getClass().getClassLoader();
        File baseDir = new File(classLoader.getResource("BibTeX2MODSTransformerTest").getFile());
        int numTests = baseDir.list().length / 2;

        for (int i = 1; i <= numTests; i++) {
            // Read BibTeX file
            File bibTeXFile = new File(baseDir, String.format("test-%s-bibTeX.txt", i));
            MCRFileContent bibTeX = new MCRFileContent(bibTeXFile);

            // Transform BibTeX to MODS
            MCRJDOMContent resultingContent = new MCRBibTeX2MODSTransformer().transform(bibTeX);
            Element resultingMODS = resultingContent.asXML().getRootElement().getChildren().get(0).detach();
            removeSourceExtension(resultingMODS);

            // Read expected MODS
            File modsFile = new File(baseDir, String.format("test-%s-mods.xml", i));
            Element mods = new MCRFileContent(modsFile).asXML().detachRootElement();

            // Compare transformation output with expected MODS
            String expected = new MCRJDOMContent(mods).asString();
            String result = new MCRJDOMContent(resultingMODS).asString();

            String message = "transformation of " + bibTeXFile.getName();
            assertEquals(message, expected, result);
        }
    }

    private void removeSourceExtension(Element resultingMODS) {
        for (Element extension : resultingMODS.getChildren("extension", MCRConstants.MODS_NAMESPACE)) {
            for (Element source : extension.getChildren("source")) {
                source.detach();
            }
            if (extension.getChildren().isEmpty()) {
                extension.detach();
            }
        }
    }
}
