package org.mycore.services.z3950;

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Logger;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.mycore.common.MCRUtils;
import org.mycore.common.config.MCRConfiguration;
import org.mycore.datamodel.classifications2.MCRCategory;
import org.mycore.datamodel.classifications2.MCRCategoryDAOFactory;
import org.mycore.datamodel.classifications2.MCRCategoryID;
import org.mycore.datamodel.common.MCRXMLMetadataManager;
import org.mycore.datamodel.metadata.MCRObjectID;
import org.mycore.parsers.bool.MCRCondition;
import org.mycore.services.fieldquery.MCRHit;
import org.mycore.services.fieldquery.MCRQuery;
import org.mycore.services.fieldquery.MCRQueryManager;
import org.mycore.services.fieldquery.MCRResults;
import org.xml.sax.SAXException;
/**
 * Diese Klasse ist eine Implementierung eines Suchservice fï¿½r die Z39.50-
 * Schnittstelle. Dabei werden nur Z39.50-Anfragen im Prefixformat
 * entgegengenommen.
 * @author Andreas de Azevedo
 * @version 1.0
 * 
 */
public class MCRZ3950QueryService implements MCRZ3950Query {
    
    protected static MCRConfiguration CONFIG = MCRConfiguration.instance();
    
    private static Logger logger = Logger.getLogger(MCRZ3950QueryService.class);
    
    private static MCRXMLMetadataManager TM = MCRXMLMetadataManager.instance();
    
    // Die Z39.50-Anfrage als String
    private String query;
    
    // Das Ergebnis der Suche
    private MCRResults mycoreResults;
    
    // Wir geben immer nur ein Ergebnis zurück, normalerweise das erste
    private int index;
    

    public MCRZ3950QueryService() {
        this(null);
    }
    
    public MCRZ3950QueryService(String query) {
        this.query = query;
        index = 0;
    }
    
    public void cutDownTo(int maxresults) {
        if (mycoreResults.getNumHits() > 0 && maxresults > 0) 
            mycoreResults.cutResults(maxresults);
    }
    
    public void sort() {}
    
    /**
     * Gibt alle Ergebnisse als Bytestrom zurï¿½ck.
     * @return Das Ergebnisdokument als Byte-Array, null falls es keine Ergebnisse gab.
     */
    public byte[] getDocumentAsByteArray() throws IOException, JDOMException, SAXException {
        byte[] result = null;
        if (mycoreResults.getNumHits() > 0)
            {
                MCRHit hit = mycoreResults.getHit(index);
                String id = hit.getID();
                // check the ID and retrieve the data
                org.jdom2.Document d = TM.retrieveXML(MCRObjectID.getInstance(id));
                fillClassificationsWithLabels(d.getRootElement());
                
                // build old MCRXMLContainer so that stylesheets in jzkit package must not be changed
                // cleaning action in Hamburg 2006/11/21 has caused this 
                org.jdom2.Element root = new org.jdom2.Element( "mcr_results");
                org.jdom2.Element ele = new org.jdom2.Element( "mcr_result");
                root.addContent(ele);
                ele.setAttribute("id", id);
                ele.addContent(d.cloneContent());

                org.jdom2.output.XMLOutputter outputter = new org.jdom2.output.XMLOutputter();
                logger.debug(outputter.outputString(root));
                result = MCRUtils.getByteArray(new org.jdom2.Document( root));
            }
        return result;  
    }
    
    /**
     * Fï¿½hrt eine Suchanfrage in MyCoRe aus.
     * @return True falls es Ergebnisse gab, sonst False.
     */
    public boolean search() {
        MCRZ3950PrefixQueryParser pqs = new MCRZ3950PrefixQueryParser( new StringReader( query ) );
        MCRCondition condition = pqs.parse();
        
        if (logger.isDebugEnabled())
            logger.debug("Transformed query: " + condition.toString());

        mycoreResults = MCRQueryManager.search(new MCRQuery( condition ));
        return mycoreResults.getNumHits() > 0;
    }
    
    /**
     * Die Methode <code>fillClassificationsWithLabels</code> durchsucht alle
     * Metadaten und untersucht deren benutzte Klassifikationen. Da in den
     * Metadaten nur ein Verweis auf Klasse und Kategorie ist, wird dieser
     * ergï¿½nzt durch sein Label.
     */
    private void fillClassificationsWithLabels(org.jdom2.Element result) {
        Element metadata = result.getChild("metadata");
        // Alle Kinder des Knotens, also alle Metadaten
        List metadataChildren = metadata.getChildren();
        // Iteriere ï¿½ber alle Knoten
        for (Object aMetadataChildren : metadataChildren) {
            // Prï¿½fe, ob der Knoten eine Klassifikation benutzt
            Element parent = (Element) aMetadataChildren;
            String cl = parent.getAttributeValue("class");
            if (cl.equals("MCRMetaClassification")) {
                // Iteriere ï¿½ber alle Kinder des Knotens (z.B. Subject)             
                List children = parent.getChildren();
                for (Object aChildren : children) {
                    Element e = (Element) aChildren;
                    String classificationId = e.getAttributeValue("classid");
                    String categoryId = e.getAttributeValue("categid");
                    MCRCategory category = MCRCategoryDAOFactory.getInstance().getCategory(new MCRCategoryID(classificationId, categoryId), -1);
//                  Fülle den Knoten mit dem Klassifiaktions-Label
//                  TODO: please change: language is random
                    e.setText(category.getLabels().iterator().next().getText());
                }
            }
        }
    }
    
    public int getSize() {
        return mycoreResults.getNumHits();
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
        // mycoreResults = mycoreResults.exportElementToContainer(index);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

}