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

package org.mycore.common.xml;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.MessageFormat;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.activation.MimetypesFileTypeMap;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.apache.log4j.Logger;
import org.jdom2.JDOMException;
import org.jdom2.output.DOMOutputter;
import org.mycore.backend.hibernate.MCRHIBConnection;
import org.mycore.common.MCRCache;
import org.mycore.common.MCRCache.ModifiedHandle;
import org.mycore.common.MCRCalendar;
import org.mycore.common.MCRSessionMgr;
import org.mycore.common.MCRSystemUserInformation;
import org.mycore.common.MCRUtils;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.common.config.MCRConfigurationDir;
import org.mycore.datamodel.classifications2.MCRCategLinkReference;
import org.mycore.datamodel.classifications2.MCRCategLinkService;
import org.mycore.datamodel.classifications2.MCRCategLinkServiceFactory;
import org.mycore.datamodel.classifications2.MCRCategory;
import org.mycore.datamodel.classifications2.MCRCategoryDAO;
import org.mycore.datamodel.classifications2.MCRCategoryDAOFactory;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.classifications2.MCRLabel;
import org.mycore.datamodel.classifications2.impl.MCRCategoryDAOImpl;
import org.mycore.datamodel.classifications2.impl.MCRCategoryImpl;
import org.mycore.datamodel.common.MCRISO8601Date;
import org.mycore.datamodel.common.MCRLinkTableManager;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
import org.mycore.datamodel.metadata.MCRDerivate;
import org.mycore.datamodel.metadata.MCRMetaLinkID;
import org.mycore.datamodel.metadata.MCRMetadataManager;
import org.mycore.datamodel.metadata.MCRObject;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.datamodel.niofs.MCRPath;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ibm.icu.util.Calendar;
import com.ibm.icu.util.GregorianCalendar;

/**
 * @author Thomas Scheffler (yagee)
 * @author Jens Kupferschmidt
 * @author shermann
 */
public class MCRXMLFunctions {

    //use holder to not initialize MCRXMLMetadataManager to early (simplifies junit testing)
    private static class MCRXMLMetaDataManagerHolder {
        public static final MCRXMLMetadataManager instance = MCRXMLMetadataManager.instance();
    }

    private static class MCRCategLinkServiceHolder {
        public static final MCRCategLinkService instance = MCRCategLinkServiceFactory.getInstance();
    }

    static MCRConfiguration CONFIG = MCRConfiguration.instance();

    private static final String HOST_PREFIX = "MCR.remoteaccess_";

    private static final String QUERY_SUFFIX = "_query_servlet";

    private static final String IFS_SUFFIX = "_ifs_servlet";

    private static final String HOST_SUFFIX = "_host";

    private static final String PORT_SUFFIX = "_port";

    private static final String PROTOCOLL_SUFFIX = "_protocol";

    private static final String DEFAULT_PORT = "80";

    private static final Logger LOGGER = Logger.getLogger(MCRXMLFunctions.class);

    private static MCRCache<String, Boolean> DISPLAY_DERIVATE_CACHE = new MCRCache<>(10000, "Derivate display value cache");

    /**
     * returns the given String trimmed
     *
     * @param arg0
     *            String to be trimmed
     * @return trimmed copy of arg0
     * @see java.lang.String#trim()
     */
    public static String trim(String arg0) {
        return arg0.trim();
    }

    /**
     * returns the given String in unicode NFC normal form.
     *
     * @param arg0 String to be normalized
     * @see Normalizer#normalize(CharSequence, java.text.Normalizer.Form)
     */
    public static String normalizeUnicode(String arg0) {
        return Normalizer.normalize(arg0, Normalizer.Form.NFC);
    }

    /**
     * returns the QueryServlet-Link of the given hostAlias
     *
     * @param hostAlias
     *            remote alias
     * @return QueryServlet-Link
     */
    public static String getQueryServlet(String hostAlias) {
        return getBaseLink(hostAlias).append(CONFIG.getString(HOST_PREFIX + hostAlias + QUERY_SUFFIX)).toString();
    }

    /**
     * returns the FileNodeServlet-Link of the given hostAlias
     *
     * @param hostAlias
     *            remote alias
     * @return FileNodeServlet-Link
     */
    public static String getIFSServlet(String hostAlias) {
        return getBaseLink(hostAlias).append(CONFIG.getString(HOST_PREFIX + hostAlias + IFS_SUFFIX)).toString();
    }

    public static StringBuffer getBaseLink(String hostAlias) {
        StringBuffer returns = new StringBuffer();
        returns.append(CONFIG.getString(HOST_PREFIX + hostAlias + PROTOCOLL_SUFFIX, "http")).append("://")
                .append(CONFIG.getString(HOST_PREFIX + hostAlias + HOST_SUFFIX));
        String port = CONFIG.getString(HOST_PREFIX + hostAlias + PORT_SUFFIX, DEFAULT_PORT);
        if (!port.equals(DEFAULT_PORT)) {
            returns.append(":").append(port);
        }
        return returns;
    }

    public static String formatISODate(String isoDate, String simpleFormat, String iso639Language) throws ParseException {
        return formatISODate(isoDate, null, simpleFormat, iso639Language);
    }

    public static String formatISODate(String isoDate, String isoFormat, String simpleFormat, String iso639Language) throws ParseException {
        return formatISODate(isoDate, isoFormat, simpleFormat, iso639Language, TimeZone.getDefault().getID());
    }

    public static String formatISODate(String isoDate, String isoFormat, String simpleFormat, String iso639Language, String timeZone)
            throws ParseException {
        if (LOGGER.isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("isoDate=");
            sb.append(isoDate).append(", simpleFormat=").append(simpleFormat).append(", isoFormat=").append(isoFormat)
                    .append(", iso649Language=").append(iso639Language).append(", timeZone=").append(timeZone);
            LOGGER.debug(sb.toString());
        }
        Locale locale = new Locale(iso639Language);
        MCRISO8601Date mcrdate = new MCRISO8601Date();
        mcrdate.setFormat(isoFormat);
        mcrdate.setDate(isoDate);
        try {
            String formatted = mcrdate.format(simpleFormat, locale, timeZone);
            return formatted == null ? "?" + isoDate + "?" : formatted;
        } catch (RuntimeException iae) {
            LOGGER.error("Unable to format date " + mcrdate.getISOString() + " to " + simpleFormat + " with locale " + locale
                    + " and timezone " + timeZone, iae);
            return "?";
        }
    }

    public static String getISODate(String simpleDate, String simpleFormat, String isoFormat) throws ParseException {
        Date date;
        if (simpleFormat.equals("long")) {
            date = new Date(Long.parseLong(simpleDate));
        } else {
            SimpleDateFormat df = new SimpleDateFormat(simpleFormat, Locale.ROOT);
            df.setTimeZone(TimeZone.getTimeZone("UTC"));
            // or else testcase
            // "1964-02-24" would
            // result "1964-02-23"
            date = df.parse(simpleDate);
        }
        return getISODate(date, isoFormat);
    }

    public static String getISODate(Date date, String isoFormat) {
        MCRISO8601Date mcrdate = new MCRISO8601Date();
        mcrdate.setDate(date);
        mcrdate.setFormat(isoFormat);
        return mcrdate.getISOString();
    }

    public static String getISODate(String simpleDate, String simpleFormat) throws ParseException {
        return getISODate(simpleDate, simpleFormat, null);
    }

    /**
     * The method get a date String in format yyyy-MM-ddThh:mm:ssZ for ancient date values.
     *
     * @param date_value the date string
     * @param field_name the name of field of MCRMetaHistoryDate, it should be 'von' or 'bis'
     * @param calendar_name the name if the calendar defined in MCRCalendar
     * @return the date in format yyyy-MM-ddThh:mm:ssZ
     */
    public static String getISODateFromMCRHistoryDate(String date_value, String field_name, String calendar_name) throws ParseException {
        String formatted_date = "";
        if (field_name == null || field_name.trim().length() == 0) {
            return "";
        }
        boolean use_last_value = false;
        if (field_name.equals("bis"))
            use_last_value = true;
        try {
            Calendar calendar = MCRCalendar.getHistoryDateAsCalendar(date_value, use_last_value, calendar_name);
            GregorianCalendar g_calendar = MCRCalendar.getGregorianCalendarOfACalendar(calendar);
            formatted_date = MCRCalendar.getCalendarDateToFormattedString(g_calendar, "yyyy-MM-dd") + "T00:00:00.000Z";
            if (g_calendar.get(GregorianCalendar.ERA) == GregorianCalendar.BC)
                formatted_date = "-" + formatted_date;
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
        return formatted_date;
    }

    /**
     * Returns a string representing the current date. One can customize the
     * format of the returned string by providing a proper value for the format
     * parameter. If null or an invalid format is provided the default format
     * "yyyy-MM-dd" will be used.
     *
     * @param format
     *            the format in which the date should be formatted
     * @return the current date in the desired format
     * @see SimpleDateFormat format description
     */
    public static String getCurrentDate(String format) {
        SimpleDateFormat sdf = null;
        try {
            sdf = new SimpleDateFormat(format, Locale.ROOT);
        } catch (Exception i) {
            LOGGER.warn("Could not parse date format string, will use default \"yyyy-MM-dd\"", i);
            sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        }

        return sdf.format(new Date());
    }

    /**
     * A delegate for {@link String#compareTo(String)}.
     *
     * @return s1.compareTo(s2)
     */
    public static int compare(String s1, String s2) {
        return s1.compareTo(s2);
    }

    /**
     * @param source the source string to operate on
     * @param regex the regular expression to apply
     */
    public static String regexp(String source, String regex, String replace) {
        String regexApplied = null;
        try {
            regexApplied = source.replaceAll(regex, replace);
        } catch (Exception e) {
            LOGGER.warn("Could not apply regular expression. Returning source string (" + source + ").");
            return source;
        }

        return regexApplied;
    }

    public static boolean classAvailable(String className) {
        try {
            Class.forName(className);
            LOGGER.debug("found class: " + className);
            return true;
        } catch (ClassNotFoundException e) {
            LOGGER.debug("did not find class: " + className);
            return false;
        }
    }

    public static boolean resourceAvailable(String resourceName) {
        URL resource = MCRConfigurationDir.getConfigResource(resourceName);
        if (resource == null) {
            LOGGER.debug("did not find resource: " + resourceName);
            return false;
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(MessageFormat.format("resource: {0} found at {1}", resourceName, resource.toString()));
        }
        return true;
    }

    /**
     * Encodes the given url so that one can safely embed that string in a part
     * of an URI
     *
     * @return the encoded source
     */
    public static String encodeURL(String source, String encoding) {
        String result = null;
        try {
            result = URLEncoder.encode(source, encoding);
        } catch (UnsupportedEncodingException e) {
            LOGGER.error(e);
        }
        return result;
    }

    /**
     * Encodes the given URL so, that it is a valid RFC 2396 URL.
     */
    public static String normalizeAbsoluteURL(String url) throws MalformedURLException, URISyntaxException {
        try {
            return new URI(url).toASCIIString();
        } catch (Exception e) {
            URL testURL = new URL(url);
            URI uri = new URI(testURL.getProtocol(), testURL.getUserInfo(), testURL.getHost(), testURL.getPort(), testURL.getPath(),
                    testURL.getQuery(), testURL.getRef());
            return uri.toASCIIString();
        }
    }

    /**
     * Encodes the path so that it can be safely used in an URI.
     * Same as calling {@link #encodeURIPath(String, boolean)} with boolean parameter set to false.
     * @return encoded path as described in RFC 2396
     */
    public static String encodeURIPath(String path) throws URISyntaxException {
        return encodeURIPath(path, false);
    }

    /**
     * Encodes the path so that it can be safely used in an URI.
     * @param asciiOnly
     *          if true, return only ASCII characters (e.g. encode umlauts)
     * @return encoded path as described in RFC 2396
     */
    public static String encodeURIPath(String path, boolean asciiOnly) throws URISyntaxException {
        URI relativeURI = new URI(null, null, path, null, null);
        return asciiOnly ? relativeURI.toASCIIString() : relativeURI.getRawPath();
    }

    /**
     * Decodes the path so that it can be displayed without encoded octets.
     * @param path
     *            encoded path as described in RFC 2396
     * @return decoded path
     */
    public static String decodeURIPath(String path) throws URISyntaxException {
        URI relativeURI = new URI(path);
        return relativeURI.getPath();
    }

    public static boolean isDisplayedEnabledDerivate(String derivateId) {
        MCRObjectID derId = MCRObjectID.getInstance(derivateId);
        ModifiedHandle modifiedHandle = MCRXMLMetaDataManagerHolder.instance.getLastModifiedHandle(derId, 30, TimeUnit.SECONDS);
        Boolean result;
        try {
            result = DISPLAY_DERIVATE_CACHE.getIfUpToDate(derivateId, modifiedHandle);
        } catch (IOException e) {
            LOGGER.warn("Error while determining when " + derId + " was last modified.", e);
            return false;
        }
        if (result != null) {
            return result;
        }
        MCRDerivate der;
        try {
            org.jdom2.Document derDoc = MCRXMLMetaDataManagerHolder.instance.retrieveXML(derId);
            if (derDoc == null) {
                LOGGER.error("Derivate \"" + derId + "\" does not exist");
                return false;
            }
            der = new MCRDerivate(derDoc);
        } catch (SAXException | JDOMException | IOException | RuntimeException e) {
            LOGGER.warn("Error while loading derivate: " + derId, e);
            return false;
        }
        org.jdom2.Element derivateElem = der.getDerivate().createXML();
        String display = derivateElem.getAttributeValue("display", "true");
        Boolean returnValue = Boolean.valueOf(display);
        DISPLAY_DERIVATE_CACHE.put(derivateId, returnValue);
        return returnValue;
    }

    /**
     * @return true if the given object is allowed for urn assignment
     */
    public static boolean isAllowedObjectForURNAssignment(String objId) {
        if (objId == null) {
            return false;
        }
        try {
            MCRObjectID obj = MCRObjectID.getInstance(objId);
            String type = obj.getTypeId();
            return isAllowedObject(type);

        } catch (Exception ex) {
            LOGGER.error("Error while checking object " + objId + " is allowed for urn assignment");
            return false;
        }
    }

    /**
     * Reads the property "URN.Enabled.Objects".
     *
     * @param givenType
     *            the type of the mycore object to check
     * @return <code>true</code> if the given type is in the list of allowed
     *         objects, <code>false</code> otherwise
     */
    private static boolean isAllowedObject(String givenType) {
        if (givenType == null)
            return false;

        String propertyName = "MCR.URN.Enabled.Objects";
        String propertyValue = MCRConfiguration.instance().getString(propertyName, null);
        if (propertyValue == null || propertyValue.length() == 0) {
            LOGGER.info("URN assignment disabled as the property \"" + propertyName + "\" is not set");
            return false;
        }

        String[] allowedTypes = propertyValue.split(",");
        for (String current : allowedTypes) {
            if (current.trim().equals(givenType.trim())) {
                return true;
            }
        }
        LOGGER.info("URN assignment disabled as the object type " + givenType + " is not in the list of allowed objects. See property \""
                + propertyName + "\"");
        return false;
    }

    /**
     * @param objectId
     *            the id of the derivate owner
     * @return <code>true</code> if the derivate owner has a least one derivate
     *         with the display attribute set to true, <code>false</code>
     *         otherwise
     */
    public static boolean hasDisplayableDerivates(String objectId) throws Exception {
        MCRObjectID id = MCRObjectID.getInstance(objectId);
        if (MCRMetadataManager.exists(id)) {
            MCRObject obj = MCRMetadataManager.retrieveMCRObject(id);
            List<MCRMetaLinkID> links = obj.getStructure().getDerivates();

            for (MCRMetaLinkID aLink : links) {
                MCRDerivate derivate = MCRMetadataManager.retrieveMCRDerivate(aLink.getXLinkHrefID());
                if (derivate.getDerivate().isDisplayEnabled()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns a list of link targets of a given MCR object type. The structure
     * is <em>link</em>. If no links are found an empty NodeList is returned.
     * 
     * @param mcrid
     *            MCRObjectID as String as the link source
     * @param destinationType
     *            MCR object type
     * @return a NodeList with <em>link</em> elements
     */
    public static NodeList getLinkDestinations(String mcrid, String destinationType) {
        DocumentBuilder documentBuilder = MCRDOMUtils.getDocumentBuilderUnchecked();
        try {
            Document document = documentBuilder.newDocument();
            Element rootElement = document.createElement("linklist");
            document.appendChild(rootElement);
            MCRLinkTableManager ltm = MCRLinkTableManager.instance();
            for (String id : ltm.getDestinationOf(mcrid, destinationType)) {
                Element link = document.createElement("link");
                link.setTextContent(id);
                rootElement.appendChild(link);
            }
            return rootElement.getChildNodes();
        } finally {
            MCRDOMUtils.releaseDocumentBuilder(documentBuilder);
        }
    }

    /**
     * Returns a list of link sources of a given MCR object type. The structure
     * is <em>link</em>. If no links are found an empty NodeList is returned.
     *
     * @param mcrid
     *            MCRObjectID as String as the link target
     * @param sourceType
     *            MCR object type
     * @return a NodeList with <em>link</em> elements
     */
    public static NodeList getLinkSources(String mcrid, String sourceType) {
        DocumentBuilder documentBuilder = MCRDOMUtils.getDocumentBuilderUnchecked();
        try {
            Document document = documentBuilder.newDocument();
            Element rootElement = document.createElement("linklist");
            document.appendChild(rootElement);
            MCRLinkTableManager ltm = MCRLinkTableManager.instance();
            for (String id : ltm.getSourceOf(mcrid)) {
                if (sourceType == null || MCRObjectID.getIDParts(id)[1].equals(sourceType)) {
                    Element link = document.createElement("link");
                    link.setTextContent(id);
                    rootElement.appendChild(link);
                }
            }
            return rootElement.getChildNodes();
        } finally {
            MCRDOMUtils.releaseDocumentBuilder(documentBuilder);
        }
    }

    /**
     * same as {@link #getLinkSources(String, String)} with
     * <code>sourceType</code>=<em>null</em>
     *
     */
    public static NodeList getLinkSources(String mcrid) {
        return getLinkSources(mcrid, null);
    }

    /**
     * Determines the mime type for the file given by its name.
     *
     * @param f
     *            the name of the file
     * @return the mime type of the given file
     */
    public static String getMimeType(String f) {
        if (f == null) {
            return "application/octet-stream";
        }
        MimetypesFileTypeMap mTypes = new MimetypesFileTypeMap();
        return mTypes.getContentType(f.toLowerCase(Locale.ROOT));
    }

    /**
     * @return the name of the maindoc of the given derivate or null if maindoc is not set
     */
    public static String getMainDocName(String derivateId) {
        if (derivateId == null || derivateId.isEmpty()) {
            return null;
        }

        MCRObjectID objectID = MCRObjectID.getInstance(derivateId);
        if (!MCRMetadataManager.exists(objectID)) {
            return null;
        }

        return MCRMetadataManager.retrieveMCRDerivate(objectID).getDerivate().getInternals().getMainDoc();
    }

    /**
     * The method return a org.w3c.dom.NodeList as subpath of the doc input
     * NodeList selected by a path as String.
     *
     * @param doc
     *            the input org.w3c.dom.Nodelist
     * @param path
     *            the path of doc as String
     * @return a subpath of doc selected by path as org.w3c.dom.NodeList
     */
    public static NodeList getTreeByPath(NodeList doc, String path) {
        NodeList n = null;
        DocumentBuilder documentBuilder = MCRDOMUtils.getDocumentBuilderUnchecked();
        try {
            // build path selection
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            XPathExpression expr = xpath.compile(path);
            // select part
            Document document = documentBuilder.newDocument();
            if (doc.item(0).getNodeName().equals("#document")) {
                // LOGGER.debug("NodeList is a document.");
                Node child = doc.item(0).getFirstChild();
                if (child != null) {
                    Node node = (Node) doc.item(0).getFirstChild();
                    Node imp = document.importNode(node, true);
                    document.appendChild(imp);
                } else {
                    document.appendChild(doc.item(0));
                }
            }
            n = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            MCRDOMUtils.releaseDocumentBuilder(documentBuilder);
        }
        return n;
    }

    /**
     * checks if the current user is in a specific role
     *
     * @param role
     *            a role name
     * @return true if user has this role
     */
    public static boolean isCurrentUserInRole(String role) {
        return MCRSessionMgr.getCurrentSession().getUserInformation().isUserInRole(role);
    }

    public static boolean isCurrentUserSuperUser() {
        return MCRSessionMgr.getCurrentSession().getUserInformation().equals(MCRSystemUserInformation.getSuperUserInstance());
    }

    public static boolean isCurrentUserGuestUser() {
        return MCRSessionMgr.getCurrentSession().getUserInformation().equals(MCRSystemUserInformation.getGuestInstance());
    }

    public static String getCurrentUserAttribute(String attribute) {
        return MCRSessionMgr.getCurrentSession().getUserInformation().getUserAttribute(attribute);
    }

    public static boolean exists(String objectId) {
        return MCRMetadataManager.exists(MCRObjectID.getInstance(objectId));
    }

    /**
     * Verifies if object is in specified category.
     * @see MCRCategLinkService#isInCategory(MCRCategLinkReference, MCRCategoryID)
     * @param objectId valid MCRObjectID as String
     * @param categoryId valid MCRCategoryID as String
     * @return true if object is in category, else false
     */
    public static boolean isInCategory(String objectId, String categoryId) {
        try {
            MCRCategoryID categID = MCRCategoryID.fromString(categoryId);
            MCRObjectID mcrObjectID = MCRObjectID.getInstance(objectId);
            MCRCategLinkReference reference = new MCRCategLinkReference(mcrObjectID);
            return MCRCategLinkServiceHolder.instance.isInCategory(reference, categID);
        } catch (Throwable e) {
            LOGGER.error("Error while checking if object is in category", e);
            return false;
        }
    }

    /**
     * Checks if the User-Agent is sent from a mobile device
     * @return true if the User-Agent is sent from a mobile device
     */
    public static boolean isMobileDevice(String UserAgent) {
        return UserAgent.toLowerCase(Locale.ROOT).contains("mobile");
    }

    public static boolean hasParentCategory(String classificationId, String categoryId) {
        MCRCategoryID categID = new MCRCategoryID(classificationId, categoryId);
        MCRCategoryDAO dao = MCRCategoryDAOFactory.getInstance();
        //fast way
        if (dao instanceof MCRCategoryDAOImpl) {
            MCRCategoryImpl categoryImpl = MCRCategoryDAOImpl.getByNaturalID(MCRHIBConnection.instance().getSession(), categID);
            //root category has level 0
            return categoryImpl.getLevel() > 1;
        }
        //default way
        List<MCRCategory> parents = dao.getParents(categID);
        //parents at least holds root category:
        return parents.size() > 1;
    }

    public static String getDisplayName(String classificationId, String categoryId) {
        MCRCategoryID categID = new MCRCategoryID(classificationId, categoryId);
        MCRCategoryDAO dao = MCRCategoryDAOFactory.getInstance();
        MCRCategory category = dao.getCategory(categID, 0);
        return category.getCurrentLabel().map(MCRLabel::getText).orElse("");
    }

    public static boolean isCategoryID(String classificationId, String categoryId) {
        MCRCategoryID categID = MCRCategoryID.fromString(classificationId + ":" + categoryId);
        MCRCategoryDAO dao = MCRCategoryDAOFactory.getInstance();
        MCRCategory category = dao.getCategory(categID, 0);

        return category == null ? false : true;
    }

    /**
     * Method returns the amount of space consumed by the files contained in the
     * derivate container. The returned string is already formatted meaning it
     * has already the optimal measurement unit attached (e.g. 142 MB, ).
     *
     * @param derivateId
     *            the derivate id for which the size should be returned
     * @return the size as formatted string
     */
    public static String getSize(String derivateId) throws IOException {
        MCRPath rootPath = MCRPath.getPath(derivateId, "/");
        final AtomicLong size = new AtomicLong();
        Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                size.addAndGet(attrs.size());
                return super.visitFile(file, attrs);
            }

        });
        return MCRUtils.getSizeFormatted(size.get());
    }

    /**
     * @param derivateID
     *            the derivate id
     * @return the id of the mycore object that contains the derivate with the
     *         given id
     */
    public static String getMCRObjectID(String derivateID) {
        return getMCRObjectID(derivateID, 5000);
    }

    /**
     * Same as {@link MCRMetadataManager#getObjectId(MCRObjectID, long, TimeUnit)} with String representation.
     */
    public static String getMCRObjectID(final String derivateID, final long expire) {
        return MCRMetadataManager.getObjectId(MCRObjectID.getInstance(derivateID), expire, TimeUnit.MILLISECONDS).toString();
    }

    /**
     * @param uri the uri to resolve
     */
    public static NodeList resolve(String uri) throws JDOMException {
        org.jdom2.Element element = MCRURIResolver.instance().resolve(uri);
        element.detach();
        org.jdom2.Document document = new org.jdom2.Document(element);
        return new DOMOutputter().output(document).getDocumentElement().getChildNodes();
    }

    /**
     * Helper function for xslImport URI Resolver and {@link #hasNextImportStep(String)}
     * @param includePart substring after "xmlImport:"
     */
    public static String nextImportStep(String includePart) {
        int border = includePart.indexOf(':');
        String selfName = null;
        if (border > 0) {
            selfName = includePart.substring(border + 1);
            includePart = includePart.substring(0, border);
        }
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("get next import step for " + includePart);
        }
        // get the parameters from mycore.properties
        List<String> importList = Collections.emptyList();
        importList = MCRConfiguration.instance().getStrings("MCR.URIResolver.xslImports." + includePart, importList);
        if (importList.isEmpty()) {
            LOGGER.info("MCR.URIResolver.xslImports." + includePart + " has no Stylesheets defined");
        } else {
            ListIterator<String> listIterator = importList.listIterator(importList.size());

            if (selfName == null && listIterator.hasPrevious()) {
                return listIterator.previous();
            }

            while (listIterator.hasPrevious()) {
                String currentStylesheet = listIterator.previous();
                if (currentStylesheet.equals(selfName)) {
                    if (listIterator.hasPrevious()) {
                        return listIterator.previous();
                    } else {
                        LOGGER.debug("xslImport reached end of chain:" + importList);
                        return "";
                    }
                }
                //continue;
            }
            LOGGER.warn("xslImport could not find " + selfName + " in " + importList);
        }
        return "";
    }

    public static boolean hasNextImportStep(String uri) {
        boolean returns = !nextImportStep(uri).isEmpty();
        LOGGER.debug("hasNextImportStep('" + uri + "') -> " + returns);
        return returns;
    }

    public static String shortenText(String text, int lenght) {
        if (text.length() <= lenght) {
            return text;
        }
        int i = text.indexOf(' ', lenght);
        if (i < 0) {
            return text;
        }
        return text.substring(0, i) + "...";
    }

    public static String shortenPersonLabel(String text) {
        int pos = text.indexOf("(");
        if (pos == -1) {
            return text;
        }
        return text.substring(0, pos - 1);
    }

}
