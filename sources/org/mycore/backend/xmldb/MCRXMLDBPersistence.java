/**
 * $RCSfile$
 * $Revision$ $Date$
 *
 * This file is part of ** M y C o R e **
 * Visit our homepage at http://www.mycore.de/ for details.
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
 * along with this program, normally in the file license.txt.
 * If not, write to the Free Software Foundation Inc.,
 * 59 Temple Place - Suite 330, Boston, MA  02111-1307 USA
 *
 **/

package org.mycore.backend.xmldb;

import java.util.GregorianCalendar;
import java.util.StringTokenizer;
import org.mycore.common.*;
import org.mycore.datamodel.metadata.*;
import org.jdom.Document;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;

/**
 * This class is the persistence layer for XML:DB databases.
 **/
public final class MCRXMLDBPersistence 
    implements MCRObjectPersistenceInterface {
    static Logger logger = Logger.getLogger( MCRXMLDBPersistence.class.getName() );
    private MCRConfiguration config = MCRConfiguration.instance();  
    private Collection rootCollection;

    /**
     * Creates a new MCRXMLDBPersistence.
     **/
    public MCRXMLDBPersistence() throws MCRPersistenceException {
	try {
	    rootCollection = MCRXMLDBTools.getRootCollection( MCRXMLDBTools.getDriverName(), 
							      MCRXMLDBTools.getConnectString() );
	}
	catch( Exception e ) {
	    throw new MCRPersistenceException( e.getMessage(), e );
	}
    }
    /**
     * This method creates and stores the data from MCRTypedContent and
     * XML data in the XMLDB datastore.
     *
     * @param mcr_tc the special typed content
     * @param doc the content as JDOM Document
     *
     * @throws MCRConfigurationExcpetion
     * @throws MCRPersistenceException
     **/
    public final void create( MCRTypedContent mcr_tc, Document doc, String mcr_ts ) 
	throws MCRPersistenceException {
	try {
            logger.debug("MCRXMLDBPersistence create: " + "MCRTypedContent: " + mcr_tc);
            logger.debug("MCRXMLDBPersistence create: " + "Document       : " + doc);
            logger.debug("MCRXMLDBPersistence create: " + "String         : " + mcr_ts);
	    MCRObjectID mcr_id = null;
	    String mcr_label = null;
	    for( int i = 0; i < mcr_tc.getSize(); i++ ) {
		if( mcr_tc.getNameElement( i ).equals( "ID" ) ) {
		    mcr_id = new MCRObjectID( (String)mcr_tc.getValueElement( i ) ); 
		    mcr_label = (String)mcr_tc.getValueElement( i+1 ); }
	    }
            logger.debug("MCRXMLDBPersistence create: " + "typeCollection: " + mcr_id.getTypeId().toLowerCase());
	    Collection typeCollection = rootCollection.getChildCollection( mcr_id.getTypeId().toLowerCase() );
	    MCRXMLDBItem item = new MCRXMLDBItem( typeCollection, mcr_id, doc );
	    item.create();
	}
	catch( Exception e ) {
	    throw new MCRPersistenceException( e.getMessage(), e );
	}
    }

    /**
     * Creates an empty database from the given configuration
     * file. Currently not used in this persistence layer.
     *
     * @param mcr_type
     * @param mcr_conf
     *
     * @throws MCRConfigurationException 
     * @throws MCRPersistenceException
     **/
    public void createDataBase( String mcr_type, org.jdom.Document mcr_conf )
	throws MCRConfigurationException, 
	       MCRPersistenceException {
	try {
	    CollectionManagementService cms = 
		(CollectionManagementService)
		rootCollection.getService( "CollectionManagementService",
					   "1.0" );
	    cms.createCollection( mcr_type.toLowerCase() );
	}
	catch( Exception e ) {
	    throw new MCRPersistenceException( e.getMessage(), e );
	}
    }

    /**
     * Updates the content in the database. Currently the same as
     * create. Should be made with XUpdate in the future.
     *
     * @param mcr_tc
     * @param doc
     **/    
    public void update( MCRTypedContent mcr_tc, Document doc, String mcr_ts ) {
        logger.debug("MCRXMLDBPersistence create: " + "update");
	create( mcr_tc, doc, mcr_ts );
    }

    /**
     * Deletes the object with the given object id in the datastore.
     *
     * @param mcr_id id of the object to delete
     *
     * @throws MCRPersistenceException something goes wrong during delete
     **/     
    public void delete( MCRObjectID mcr_id ) 
	throws MCRPersistenceException {
	Collection typeCollection = null;
	try {
	    typeCollection = 
		rootCollection.getChildCollection( mcr_id.getTypeId().toLowerCase() );
	    MCRXMLDBItem item = new MCRXMLDBItem( typeCollection, 
						  mcr_id, 
						  null );
	    item.delete();
	}
	catch( Exception e ) {
	    throw new MCRPersistenceException( e.getMessage(), e );
	}
    }

    /**
     * Retrieves the object with the given ID from the datastore.
     *
     * @param mcr_id object id whose data shall be received
     *
     * @return the content as a JDOM document
     *
     * @throws MCRConfigurationException
     * @throws MCRPersistenceException
     **/
    public final byte[] receive( MCRObjectID mcr_id )
	throws MCRConfigurationException, 
	MCRPersistenceException {
        logger.debug("MCRXMLDBPersistence create: " +  "receive");    
	return MCRUtils.getByteArray( getItem( mcr_id ).getContent() );
    }

    /**
     * Retrieves a MCRXMLDBItem with given MCRObjectID from datastore.
     *
     * @param mcr_id the object id of the returned MCRXMLDBtem
     * @return the MCRXMLDBItem
     * @throws MCRPersistenceException
     **/
    protected MCRXMLDBItem getItem( MCRObjectID mcr_id ) 
	throws MCRPersistenceException {
	Collection typeCollection = null;
	MCRXMLDBItem item = null;
        logger.debug("MCRXMLDBPersistence: " + "getItem with: " + mcr_id.getTypeId().toLowerCase() + " " + mcr_id.getId());    
	try {
	    typeCollection = 
		rootCollection.getChildCollection( mcr_id.getTypeId().toLowerCase() );

	    item = new MCRXMLDBItem( typeCollection, mcr_id, null );
	    item.retrieve();
	}
	catch( Exception e ) {
	    throw new MCRPersistenceException( e.getMessage(), e );
	}
	return item;
    }
    
    /**
     * This method returns the date of creation of the object with the
     * given ID. Currently not implemented.
     *
     * @param mcr_id the id of the object whose date of creation
     * should be returned
     * @return null
     * @throws MCRConfigurtionException
     * @throws MCRPersistenceException
     **/
    public final GregorianCalendar receiveCreateDate(MCRObjectID mcr_id)
	throws MCRConfigurationException, MCRPersistenceException {
	return null;
    }

    public final String receiveLabel(MCRObjectID mcr_id)
	throws MCRConfigurationException, MCRPersistenceException {
	return "";
    }

    public synchronized String getNextFreeId( String project_ID, String type_ID )
	throws MCRPersistenceException { 
        logger.debug("MCRXMLDBPersistence: " + "getNextFreeId");    
	return "";
	
    }

    /**
     * Checks whether an object with the given object id exists in the
     * datastore.
     *
     * @param mcr_id id of the object to delete
     *
     * @throws MCRPersistenceException something goes wrong during delete
     **/     
    public boolean exist( MCRObjectID mcr_id ) 
	throws MCRConfigurationException, MCRPersistenceException {
//  	Database database = null;
//  	Collection rootCollection = null;
//  	Collection typeCollection = null;
//  	try {
//  	    String connString = MCRXMLDBTools.buildConnectString( vendor, 
//  								  hostname, 
//  								  port, 
//  								  root );
//  	    rootCollection = MCRXMLDBTools.getRootCollection( driver, 
//  							    connString );
//  	    typeCollection = 
//  		rootCollection.getChildCollection( mcr_id.getProjectId().toLowerCase() ).getChildCollection( mcr_id.getTypeId().toLowerCase() );
	    
//  	    MCRXMLDBItem item = new MCRXMLDBItem( typeCollection, 
//  						  mcr_id, 
//  						  null );
//  	    item.delete();
//  	}
//  	catch( Exception e ) {
//  	    throw new MCRPersistenceException( e.getMessage(), e );
//  	}
//  	finally {
//  	    try {
//  		MCRXMLDBTools.safelyClose( rootCollection );
//  		if (database != null)
//  		    DatabaseManager.deregisterDatabase( database );
//  	    }
//  	    catch( Exception e ) {
//  		throw new MCRPersistenceException( e.getMessage(), e );
//  	    }
        logger.debug("MCRXMLDBPersistence exist: " + mcr_id.getTypeId().toLowerCase() + " " + mcr_id.getId() );    
	return false;
    }
}
