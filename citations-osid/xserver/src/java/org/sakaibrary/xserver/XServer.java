/**********************************************************************************
 * $URL$
 * $Id$
 ***********************************************************************************
 *
 * Copyright (c) 2006, 2007 The Sakai Foundation.
 * 
 * Licensed under the Educational Community License, Version 1.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 *      http://www.opensource.org/licenses/ecl1.php
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 **********************************************************************************/

package org.sakaibrary.xserver;

//Util imports
import java.util.ArrayList;

//I/O imports
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

//URL/Network Connectivity imports
import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;

//SAX XML parsing imports
import org.sakaibrary.osid.repository.xserver.SearchStatusProperties;
import org.sakaibrary.xserver.session.MetasearchSession;
import org.sakaibrary.xserver.session.MetasearchSessionManager;
import org.xml.sax.*;
import org.xml.sax.helpers.DefaultHandler;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.ParserConfigurationException;

public class XServer extends DefaultHandler {

	// debugging
	boolean printXML = false;

	/* constants */
	private static final org.apache.commons.logging.Log LOG =
		org.apache.commons.logging.LogFactory.getLog(
		"org.sakaibrary.xserver.XServer" );
	private static final String XSLT_FILE = "/xsl/xserver2sakaibrary.xsl";

	/* fields coming from searchProperties */
	private String guid;                // required
	private String username;            // required
	private String password;            // required
	private String xserverBaseUrl;      // required
	private ArrayList searchSourceIds;  // required
	private String sortBy;
	private Integer pageSize;
	private Integer startRecord;

	/* session variables */
	private MetasearchSessionManager msm;
	private String sessionId;
	private String foundGroupNumber;
	private String mergedGroupNumber;
	private String setNumber;

	/* other member variables */
	// findResultSets keeps track of all result sets found
	private ArrayList findResultSets;

	// check authorization from X-server
	private String auth;

	// SAXParser variables
	private SAXParser saxParser;

	// text buffer to hold SAXParser character data
	private StringBuilder textBuffer;

	// create parser flags
	private boolean parsingMergeSort = false;

	// merge control flag
	private boolean singleSearchSource;

	//--------------
	// Constructor -
	//--------------
	/**
	 * Creates a new XServer object ready to communicate with the
	 * MetaLib X-server.  Reads searchProperties, sets up SAX Parser, and
	 * sets up session management for this object.
	 */
	public XServer( String guid )
	throws XServerException {
		this.guid = guid;

		// setup the SAX parser
		SAXParserFactory factory;
		factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware( true );
		try {
			saxParser = factory.newSAXParser();
		} catch (SAXException sxe) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;

			if (sxe.getException() != null) {
				x = sxe.getException();
			}

			LOG.warn( "XServer() SAX exception in trying to get a new SAXParser " +
					"from SAXParserFactory: " + sxe.getMessage(), x );
			throw new RuntimeException( "XServer() SAX exception: " + sxe.getMessage(), x );
		} catch (ParserConfigurationException pce) {
			// Parser with specified options can't be built
			LOG.warn( "XServer() SAX parser cannot be built with specified options" );
			throw new RuntimeException( "XServer() SAX parser cannot be built with " +
					"specified options: " + pce.getMessage(), pce );
		}

		// load session state
		msm = MetasearchSessionManager.getInstance();
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );

		if( metasearchSession == null ) {
			// bad state management
			throw new RuntimeException( "XServer() - ehcache MetasearchSession is " +
					"NULL :: guid is " + guid );
		}

		// get X-Server base URL
		xserverBaseUrl = metasearchSession.getBaseUrl();

		if( !metasearchSession.isLoggedIn() ) {
			// need to login
			username = metasearchSession.getUsername();
			password = metasearchSession.getPassword();

			if( !loginURL( username, password ) ) {
				// authorization failed
				throw new XServerException( "XServer.loginURL()",
				"authorization failed." );
			}

			// login success
			metasearchSession.setLoggedIn( true );
			metasearchSession.setSessionId( sessionId );
		}

		// get search properties
		org.osid.shared.Properties searchProperties = metasearchSession.
		getSearchProperties();

		try {
			searchSourceIds = ( ArrayList ) searchProperties.getProperty(
			"searchSourceIds" );  // empty TODO
			sortBy = ( String ) searchProperties.getProperty( "sortBy" );
			pageSize = ( Integer ) searchProperties.getProperty( "pageSize" );
			startRecord = ( Integer ) searchProperties.getProperty( "startRecord" );
		} catch( org.osid.shared.SharedException se ) {
			LOG.warn( "XServer() failed to get search properties - will assign " +
					"defaults", se );
		}

		// assign defaults if necessary
		// TODO assign the updated values to the session... searchProperties is read-only, need to add additional fields to MetasearchSession.
		if( sortBy == null ) {
			sortBy = "rank";
		}

		if( pageSize == null ) {
			pageSize = new Integer( 10 );
		}

		if( startRecord == null ) {
			startRecord = new Integer( 1 );
		}

		// check args
		if( startRecord.intValue() <= 0 ) {
			LOG.warn( "XServer() - startRecord must be set to 1 or higher." );
			startRecord = null;
			startRecord = new Integer( 1 );
		}

		// add/update this MetasearchSession in the cache
		msm.putMetasearchSession( guid, metasearchSession );
	}


	//------------------------------------
	// METALIB X-SERVICE IMPLEMENTATIONS -
	//------------------------------------

	/**
	 * Logs a user into the X-server using URL Syntax for communications.
	 * Uses the login X-service.
	 * 
	 * @param username String representing user username
	 * @param password String representing user password
	 * 
	 * @return boolean true if authorization succeeds, false otherwise.
	 * 
	 * @throws XServerException if login fails due to X-server error
	 */
	private boolean loginURL( String username, String password )
	throws XServerException {
		// build URL query string
		StringBuilder query = new StringBuilder( xserverBaseUrl );
		query.append( "?op=login_request&user_name=" + username +
				"&user_password=" + password );

		// connect to URL and get response
		java.io.ByteArrayOutputStream xml = doURLConnection( query.toString() );

		if( printXML ) {
			// print xml
			LOG.debug( xml.toString() );
		}

		// run SAX Parser
		try {
			saxParseXML( new java.io.ByteArrayInputStream( xml.toByteArray() ) );
		} catch( SAXException sxe ) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;

			if (sxe.getException() != null) {
				x = sxe.getException();
			}

			LOG.warn( "loginURL() SAX exception: " + sxe.getMessage(),
					x );
		} catch (IOException ioe) {
			// I/O error
			LOG.warn( "loginURL() IO exception", ioe );
		}

		// return whether or not the login was successful
		return( loginSuccessful() );
	}


	/**
	 * Finds records within the given sources using the given find command
	 * query.  Uses the find X-service.
	 * 
	 * @param findCommand String representing find_request_command.  See
	 *   <a href="http://searchtools.lib.umich.edu/X/?op=explain&func=find">
	 *   find</a> explanation from MetaLib X-Server to see how
	 *   find_request_command should be built.
	 *   
	 * @param waitFlag String representing the wait_flag.  A "Y" indicates
	 *   the X-server will not produce a response until the find command has
	 *   completed.  Full information about the group and each search set will
	 *   be returned.
	 *   <br></br>
	 *   A "N" indicates the X-server will immediately respond with the group
	 *   number while the find continues to run in the background.  The user
	 *   can then use the findGroupInfo method to poll for results.
	 * 
	 * @throws XServerException if find fails due to X-server error
	 */
	private void findURL( String findCommand, String waitFlag )
	throws XServerException {
		// build a query string containing all sources that need to be searched
		StringBuilder findBaseString = new StringBuilder();
		for( int i = 0; i < searchSourceIds.size(); i++ ) {
			findBaseString.append( "&find_base_001=" + ( String )
					searchSourceIds.get( i ) );
		}

		// build URL query string
		StringBuilder query = new StringBuilder( xserverBaseUrl );
		query.append( "?op=find_request" +
				"&wait_flag=" + waitFlag +
				"&find_request_command=" + findCommand +
				findBaseString.toString() +
				"&session_id=" + sessionId );

		// connect to URL and get response
		java.io.ByteArrayOutputStream xml = doURLConnection( query.toString() );

		if( printXML ) {
			// print xml
			LOG.debug( xml.toString() );
		}

		// run SAX Parser
		try {
			saxParseXML( new java.io.ByteArrayInputStream( xml.toByteArray() ) );
		} catch (SAXException sxe) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;

			if (sxe.getException() != null) {
				x = sxe.getException();
			}

			LOG.warn( "findURL() SAX exception: " + sxe.getMessage(), x );
		} catch (IOException ioe) {
			// I/O error
			LOG.warn( "findURL() IO exception", ioe );
		}
	}

	/**
	 * Gets information on a result set group which has already been created
	 * using the find command in asynchronous mode (waitFlag set to "N")
	 *
	 * @throws XServerException if find_group_info fails due to X-Server error
	 */
	private void findGroupInfoURL() throws XServerException {
		findResultSets = new java.util.ArrayList();

		StringBuilder query = new StringBuilder( xserverBaseUrl );
		query.append( "?op=find_group_info_request" +
				"&group_number=" + foundGroupNumber +
				"&session_id=" + sessionId );

		// connect to URL and get response
		java.io.ByteArrayOutputStream xml = doURLConnection( query.toString() );

		if( printXML ) {
			// print xml
			LOG.debug( xml.toString() );
		}

		// run SAX Parser
		try {
			saxParseXML( new java.io.ByteArrayInputStream( xml.toByteArray() ) );
		} catch (SAXException sxe) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;

			if (sxe.getException() != null) {
				x = sxe.getException();
			}

			LOG.warn( "findGroupInfoURL() SAX exception: " + sxe.getMessage(), x );
		} catch (IOException ioe) {
			// I/O error
			LOG.warn( "findGroupInfoURL() IO exception", ioe );
		}
	}

	/**
	 * Finds records within the given sources using the given find command
	 * query.  Uses the find X-service.
	 * 
	 * @param action valid values: merge, merge_more, remerge, sort_only
	 * @param primarySortKey valid values: rank, title, author, year, database
	 * 
	 * @throws XServerException if mergeSort fails due to X-server error
	 */
	private void mergeSortURL( String action, String primarySortKey )
	throws XServerException {

		if( primarySortKey == null ) {
			// default to rank
			primarySortKey = "rank";
		}

		// build URL query string
		StringBuilder query = new StringBuilder( xserverBaseUrl );
		query.append( "?op=merge_sort_request" +
				"&group_number=" + foundGroupNumber +
				"&action=" + action +
				"&primary_sort_key=" + primarySortKey +
				"&session_id=" + sessionId );

		// connect to URL and get response
		java.io.ByteArrayOutputStream xml = doURLConnection( query.toString() );

		if( printXML ) {
			// print xml
			LOG.debug( xml.toString() );
		}

		// run SAX Parser
		try {
			saxParseXML( new java.io.ByteArrayInputStream( xml.toByteArray() ) );
		} catch (SAXException sxe) {
			// Error generated by this application
			// (or a parser-initialization error)
			Exception x = sxe;

			if (sxe.getException() != null) {
				x = sxe.getException();
			}

			LOG.warn( "mergeSortURL() SAX exception: " + sxe.getMessage(), x );
		} catch (IOException ioe) {
			// I/O error
			LOG.warn( "mergeSortURL() IO exception", ioe );
		}
	}

	/**
	 * Presents records found in the given set.  Displays records in full MARC
	 * format.
	 * 
	 * @param setNumber identifier for a set to obtain records from
	 * @param setEntry  how many/which records to present
	 * @throws XServerException
	 */
	private ByteArrayOutputStream presentURL( String setNumber, String setEntry ) 
	throws XServerException {

		// build URL query string
		StringBuilder query = new StringBuilder( xserverBaseUrl );
		query.append( "?op=present_request" +
				"&set_number=" + setNumber +
				"&set_entry=" + setEntry +
				"&format=marc" +
				"&view=full" +

//				"&view=customize" +
//				"&field=VOL%23%23" +
//				"&field=YR%23%23%23" +
//				"&field=ISSUE" +
//				"&field=PAGES" +
//				"&field=ISSU%23" +
//				"&field=PAGE%23" +
//				"&field=DATE%23" +
//				"&field=JT%23%23%23" +
//				"&field=DOI%23%23" +
//				"&field=245%23%23" +  // title
//				"&field=520%23%23" +  // abstract
//				"&field=100%23%23" +  // author
//				"&field=700%23%23" +  // secondary authors
//				"&field=022%23%23" +  // issn

				"&session_id=" + sessionId );

		// connect to URL and get response
		ByteArrayOutputStream xml = doURLConnection( query.toString() );

		if( printXML ) {
			// print xml
			LOG.debug( xml.toString() );
		}

		return xml;
	}

	/**
	 * Returns a metasearchStatus Type Properties object describing this search's
	 * status.
	 * 
	 * @return metasearchStatus org.osid.shared.Properties
	 */
	public org.osid.shared.Properties getSearchStatusProperties() {
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		return new SearchStatusProperties( metasearchSession.getSearchStatusProperties() );
	}

	/**
	 * Runs a blocking search of the X-Server and returns the response xml.
	 * 
	 * @param numAssets number of records presented from the X-Server. Must be 0
	 * or greater.
	 * @return ByteArrayInputStream encapsulating response xml from the X-Server
	 * @throws XServerException in case of X-Server error
	 */
	public ByteArrayInputStream getRecordsXML( int numAssets )
	throws XServerException, org.osid.repository.RepositoryException {
		// check args
		if( numAssets < 0 ) {
			LOG.warn( "getRecordsXML() - numAssets below zero." );
			numAssets = 0;
		}

		// check session state
		if( !checkSessionState() ) {
			// throw invalid session exception (TODO use of RepositoryException = bad)
			throw new org.osid.repository.RepositoryException( 
					org.sakaibrary.osid.repository.xserver.
					MetasearchException.SESSION_TIMED_OUT );
		}

		/* figure out whether to merge or not */
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		setNumber = metasearchSession.getRecordsSetNumber();

		if( setNumber == null ) {
			// null setNumber indicates multiple search sources, do a merge
			LOG.debug( "getRecordsXML() - doing merge, set number is null" );
			mergeSortURL( "merge", sortBy );

			// we'll be getting a new setNumber for the merged set, store it
			metasearchSession.setRecordsSetNumber( setNumber );
			metasearchSession.setMergedGroupNumber( mergedGroupNumber );

			// add/update this MetasearchSession in the cache
			msm.putMetasearchSession( guid, metasearchSession );
		} else {
			if( !singleSearchSource ) {
				// do a merge_more if we're working with multiple search sources
				LOG.debug( "getRecordsXML() - doing merge_more, set number " +
						"is " + setNumber );
				mergeSortURL( "merge_more", sortBy );
			}
		}

		// determine which records to pull from the X-Server
		java.text.DecimalFormat df = new java.text.DecimalFormat( "000000000" );
		String setEntryStart;
		String setEntryEnd;
		int setEntryStartValue;

		// starting record
		if( numAssets == 0 ) {
			// just beginning a search
			setEntryStart = df.format( startRecord.intValue() );
			setEntryStartValue = startRecord.intValue();
		} else {
			// already conducted a search, continue from where we left off
			setEntryStart = df.format( numAssets + 1 );
			setEntryStartValue = numAssets + 1;
		}

		// ending record
		Integer numRecords = ( singleSearchSource ) ?
				metasearchSession.getNumRecordsFetched() : metasearchSession.getNumRecordsMerged();

		if( numAssets == numRecords.intValue() ) {
			// we've already returned all the records that the X-Server has.
			// need to wait longer
			// TODO - dangerous to throw a RepositoryException here...
			throw new org.osid.repository.RepositoryException( 
					org.sakaibrary.osid.repository.xserver.
					MetasearchException.ASSET_NOT_FETCHED );
		}

		int setEntryEndValue = numRecords.intValue();
		if( numRecords.intValue() >= pageSize.intValue() + setEntryStartValue - 1 ) {
			setEntryEndValue = pageSize.intValue() + setEntryStartValue - 1;
			if( numRecords.intValue() >= pageSize.intValue() * 2 + setEntryStartValue - 1 ) {
				// watch out if the user sets pageSize very large...
				setEntryEndValue = pageSize.intValue() * 2 + setEntryStartValue - 1;
			}
		}

		setEntryEnd = df.format( setEntryEndValue );
		LOG.debug( "getRecordsXML() - presenting records: " +
				setEntryStart + "-" + setEntryEnd );

		// run the present X-Service
		ByteArrayOutputStream cleanXml = presentURL( setNumber,
				setEntryStart + "-" + setEntryEnd );

		// transform the cleaned up xml
		XMLTransform xmlTransform = new XMLTransform( XSLT_FILE, cleanXml );
		ByteArrayOutputStream transformedXml = xmlTransform.transform();

		// return transformed xml bytes
		return new ByteArrayInputStream( transformedXml.toByteArray() );
	}

	public void initAsynchSearch( String criteria,
			java.util.ArrayList sourceIds )
	throws XServerException {
		this.searchSourceIds = sourceIds;

		LOG.debug( "initAsynchSearch() - searchSourceIds: " + searchSourceIds.size() );
		if( searchSourceIds.size() == 1 ) {
			// only one search source - do not need to merge
			singleSearchSource = true;
		} else {
			singleSearchSource = false;
		}

		LOG.debug( "initAsynchSearch() - find_command: " + criteria );
		// run the find X-Service in non-blocking mode
		findURL( criteria, "N" );

		// add/update this MetasearchSession in the cache
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		metasearchSession.setFoundGroupNumber( foundGroupNumber );
		metasearchSession.setSingleSearchSource( singleSearchSource );
		msm.putMetasearchSession( guid, metasearchSession );
	}

	public void updateSearchStatusProperties()
	throws XServerException, org.osid.repository.RepositoryException {
		// check session state
		if( !checkSessionState() ) {
			// throw invalid session exception (TODO use of RepositoryException = bad)
			throw new org.osid.repository.RepositoryException( 
					org.sakaibrary.osid.repository.xserver.
					MetasearchException.SESSION_TIMED_OUT );
		}

		// run the find_group_info X-Service
		findGroupInfoURL();

		// setup search status properties
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		java.util.Properties searchStatusProperties =
			metasearchSession.getSearchStatusProperties();

		// set up other variables to determine search status properties
		java.util.ArrayList databaseNames = new java.util.ArrayList();
		java.util.HashMap databaseMap;
		String status = null;
		String statusMessage = null;
		int numRecordsFound = 0;
		int numRecordsFetched = 0;
		int numRecordsMerged = 0;
		int delayHint = 2500;  // 2.5 seconds
		boolean ready = false;
		boolean fetching = false;
		boolean searching = false;
		boolean timeout = false;
		boolean error = false;

		// collect findGroupInfoURL results
		for( int i = 0; i < findResultSets.size(); i++ ) {
			FindResultSetBean frsb = ( FindResultSetBean ) findResultSets.get( i );

			// separate MERGESET info
			if( frsb.getBaseName().equals( "MERGESET" ) ) {
				setNumber = frsb.getSetNumber();
				if( frsb.getStatus().equals( "DONE" ) ) {
					status = "ready";
					statusMessage = "X-Server is ready to return records.";
					numRecordsMerged = Integer.parseInt( frsb.getNumDocs() );
				} else if( frsb.getStatus().equals( "FORK" ) ||
						frsb.getStatus().equals( "FIND" ) ) {
					status = "searching";
					statusMessage = "X-Server is currently searching. Please wait.";
				} else if( frsb.getStatus().equals( "FETCH" ) ) {
					status = "fetching";
					statusMessage = "X-Server is currently fetching records. Please wait.";
				} else if( frsb.getStatus().equals( "STOP" ) ) {
					status = "timeout";
					statusMessage = "X-Server session has timed out. Please start a new session.";
				} else if( frsb.getStatus().equals( "ERROR" ) ) {
					status = "error";
					statusMessage = "An X-Server error has occurred (" +
					frsb.getFindErrorText() + "). Please verify your search criteria is correct and try again.";
				}
			} else {
				setNumber = ( singleSearchSource ) ? frsb.getSetNumber() : null;

				// create a new Map entry for this database
				databaseMap = new java.util.HashMap();
				databaseMap.put( "databaseName", frsb.getFullName() );

				if( frsb.getStatus().equals( "FORK" ) ||
						frsb.getStatus().equals( "FIND" ) ) {
					searching = true;
					databaseMap.put( "status", "searching" );
					databaseMap.put( "statusMessage", "Currently searching. Please wait." );
				} else if( frsb.getStatus().equals( "FETCH" ) ) {
					fetching = true;
					databaseMap.put( "status", "fetching" );
					databaseMap.put( "statusMessage", "Currently fetching records. Please wait." );
					databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
				} else if( frsb.getStatus().equals( "DONE1" ) ) {
					ready = true;
					databaseMap.put( "status", "ready" );
					databaseMap.put( "statusMessage", "Fetched 10 records." );
					databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
					databaseMap.put( "numRecordsFetched", new Integer( 10 ) );
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
					numRecordsFetched += 10;
				} else if( frsb.getStatus().equals( "DONE2" ) ) {
					ready = true;
					databaseMap.put( "status", "ready" );
					databaseMap.put( "statusMessage", "Fetched 20 records." );
					databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
					databaseMap.put( "numRecordsFetched", new Integer( 20 ) );
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
					numRecordsFetched += 20;
				} else if( frsb.getStatus().equals( "DONE3" ) ) {
					ready = true;
					databaseMap.put( "status", "ready" );
					databaseMap.put( "statusMessage", "Fetched 30 records." );
					databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
					databaseMap.put( "numRecordsFetched", new Integer( 30 ) );
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
					numRecordsFetched += 30;
				} else if( frsb.getStatus().equals( "DONE" ) ) {
					if( Integer.parseInt( frsb.getNumDocs() ) > 0 ) {
						// have results
						ready = true;
						databaseMap.put( "status", "ready" );
						databaseMap.put( "statusMessage", "Fetched ALL records." );
						databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
						databaseMap.put( "numRecordsFetched", new Integer( frsb.getNumDocs() ) );
						numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
						numRecordsFetched += Integer.parseInt( frsb.getNumDocs() );
					} else {
						// no results
						databaseMap.put( "status", "empty" );
						databaseMap.put( "statusMessage", "No records found." );
						databaseMap.put( "numRecordsFound", new Integer( frsb.getNumDocs() ) );
						databaseMap.put( "numRecordsFetched", new Integer( 0 ) );
					}
				} else if( frsb.getStatus().equals( "STOP" ) ) {
					timeout = true;
					databaseMap.put( "status", "timeout" );
					databaseMap.put( "statusMessage", "X-Server session has timed out. Please start a new session." );
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
				} else if( frsb.getStatus().equals( "ERROR" ) ) {
					error = true;
					databaseMap.put( "status", "error" );
					databaseMap.put( "statusMessage", "An X-Server error has occurred (" +
							frsb.getFindErrorText() + "). Please verify your search criteria is correct and try again." );
					statusMessage = "An X-Server error has occurred (" +
					frsb.getFindErrorText() + "). Please verify your search criteria is correct and try again.";
					numRecordsFound += Integer.parseInt( frsb.getNumDocs() );
				}

				// add this Map to the Properties object
				searchStatusProperties.put( frsb.getFullName(), databaseMap );

				// add the database name to databaseNames array
				databaseNames.add( frsb.getFullName() );
			}
		}

		// determine status of search set
		if( status == null ) {
			// a merge has not been done
			if( ready ) {
				searchStatusProperties.put( "status", "ready" );
				searchStatusProperties.put( "statusMessage", "X-Server is ready to return records." );
			} else if( fetching ) {
				searchStatusProperties.put( "status", "fetching" );
				searchStatusProperties.put( "statusMessage", "Currently searching. Please wait." );
			} else if( searching ) {
				searchStatusProperties.put( "status", "searching" );
				searchStatusProperties.put( "statusMessage", "Currently fetching records. Please wait." );
			} else if( timeout ) {
				searchStatusProperties.put( "status", "timeout" );
				searchStatusProperties.put( "statusMessage", "X-Server session has timed out. Please start a new session." );
			} else if( error ) {
				searchStatusProperties.put( "status", "error" );
				searchStatusProperties.put( "statusMessage", statusMessage );
			} else if( !ready ) {
				// absolutely no records found
				searchStatusProperties.put( "status", "empty" );
				searchStatusProperties.put( "statusMessage", "No records found for your query." );
			}
		} else {
			// a merge has been done
			searchStatusProperties.put( "status", status );
			searchStatusProperties.put( "statusMessage", statusMessage );
		}

		// update properties
		searchStatusProperties.put( "delayHint", new Integer( delayHint ) );
		searchStatusProperties.put( "databaseNames", databaseNames );
		searchStatusProperties.put( "numRecordsFound", new Integer( numRecordsFound ) );
		searchStatusProperties.put( "numRecordsFetched", new Integer( numRecordsFetched ) );
		searchStatusProperties.put( "numRecordsMerged", new Integer( numRecordsMerged ) );

		// add/update this MetasearchSession in the cache
		metasearchSession.setSearchStatusProperties( searchStatusProperties );
		metasearchSession.setRecordsSetNumber( setNumber );
		metasearchSession.setNumRecordsFound( new Integer( numRecordsFound ) );
		metasearchSession.setNumRecordsFetched( new Integer( numRecordsFetched ) );
		metasearchSession.setNumRecordsMerged( new Integer( numRecordsMerged ) );
		msm.putMetasearchSession( guid, metasearchSession );
	}

	//-----------------------------
	// PUBLIC DATA ACCESS METHODS |
	//-----------------------------

	/**
	 * Returns the list of find result sets found during this session.  This
	 * method should be called only after calling the findURL method.
	 * 
	 * @return array of FindResultSetBeans encapsulating a list of result sets
	 * provided by the find X-service data
	 */
	public ArrayList getFindResultSets() {
		return findResultSets;
	}


	//----------------------------------
	// DEFAULT HANDLER IMPLEMENTATIONS -
	//----------------------------------

	/**
	 * Receive notification of the beginning of an element.
	 *   
	 * @see DefaultHandler
	 */
	public void startElement( String namespaceURI, String sName,
			String qName, Attributes attrs ) throws SAXException {
		// set flags to avoid overwriting duplicate tag data
		if( qName.equals( "merge_sort_response" ) ) {
			parsingMergeSort = true;
		}
	}

	/**
	 * Receive notification of the end of an element.
	 *   
	 * @see DefaultHandler
	 */
	public void endElement( String namespaceURI, String sName, String qName ) 
	throws SAXException {
		// extract data
		extractDataFromText( qName );

		// clear flags
		if( qName.equals( "merge_sort_response" ) ) {
			parsingMergeSort = false;
		}
	}

	/**
	 * Receive notification of character data inside an element.
	 *   
	 * @see DefaultHandler
	 */
	public void characters( char[] buf, int offset, int len )
	throws SAXException {
		// store character data
		String text = new String( buf, offset, len );

		if( textBuffer == null ) {
			textBuffer = new StringBuilder( text );
		} else {
			textBuffer.append( text );
		}
	}


	//-------------------------
	// PRIVATE HELPER METHODS -
	//-------------------------

	private void extractDataFromText( String element ) {
		if( textBuffer == null ) {
			return;
		}

		String text = textBuffer.toString().trim();
		if( text.equals( "" ) ) {
			return;
		}

		/* login */
		else if( element.equals( "session_id" ) ) {
			sessionId = text;
		} else if( element.equals( "auth" ) ) {
			auth = text;
		}

		/* find */
		else if( element.equals( "group_number" ) ) {
			// merge_sort will also return a group_number
			if( parsingMergeSort ) {
				mergedGroupNumber = text;
			} else {
				foundGroupNumber = text;
			}
		}

		/* find_group_info */
		else if( element.equals( "base" ) ) {
			// add FindResultSetBean to FindResultSet array, findResultSets
			findResultSets.add( new FindResultSetBean( text ) );
		}

		else if( element.equals( "full_name" ) ) {
			// result set's resource full name
			( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
			setFullName( text );
		}

		else if( element.equals( "base_001" ) ) {
			// result set resource id
			( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
			setSourceId( text );
		}

		else if( element.equals( "set_number" ) ) {
			// result set's set number
			( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
			setSetNumber( text );
		}

		else if( element.equals( "find_status" ) ) {
			// result set's status
			( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
			setStatus( text );
		}

		else if( element.equals( "find_error_text" ) ) {
			// if status is ERROR, extract error text
			( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
			setFindErrorText( text );
		}

		else if( element.equals( "no_of_documents" ) ) {
			if( !parsingMergeSort ) {
				// number of documents in result set
				( (FindResultSetBean)findResultSets.get( findResultSets.size() - 1 ) ).
				setNumDocs( text );
			} else {
				MetasearchSession ms = msm.getMetasearchSession(guid);
				ms.setNumRecordsMerged( new Integer( text ) );
				msm.putMetasearchSession(guid, ms);
			}
		}

		/* merge_sort */
		else if( element.equals( "new_set_number" ) ) {
			setNumber = text;
		}

		textBuffer = null;
	}

	/**
	 * Check for invalid session state
	 */
	private boolean checkSessionState() {
		// a search (find X-Service) should have been conducted
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		if( metasearchSession == null ||
				!metasearchSession.isLoggedIn() ||
				metasearchSession.getSessionId() == null ||
				metasearchSession.getFoundGroupNumber() == null ||
				metasearchSession.getSearchProperties() == null ) {
			if( metasearchSession == null ) {
				LOG.error( "checkSessionState() - session state out of sync:" +
						"\n  guid: " + guid +
				"\n  MetasearchSession: null" );
			} else {
				LOG.error( "checkSessionState() - session state out of sync:" +
						"\n  guid: " + guid +
						"\n  MetasearchSession: " + metasearchSession +
						"\n  logged in: " + metasearchSession.isLoggedIn() +
						"\n  sessionId: " + metasearchSession.getSessionId() +
						"\n  foundGroupNumber: " + metasearchSession.getFoundGroupNumber() +
						"\n  searchProperties: " + metasearchSession.getSearchProperties() );
			}
			return false;
		} else {
			this.sessionId = metasearchSession.getSessionId();
			this.foundGroupNumber = metasearchSession.getFoundGroupNumber();
			this.singleSearchSource = metasearchSession.isSingleSearchSource();
			return true;
		}
	}

	/**
	 * Setup a URL Connection, get an InputStream for parsing
	 * 
	 * @param urlQuery String with URL Query for X-server
	 */
	private ByteArrayOutputStream doURLConnection( String urlQuery )
	throws XServerException {
		ByteArrayOutputStream xml = null;

		URL url = null;
		HttpURLConnection urlConn = null;
		try {
			// define URL
			url = new URL( urlQuery );

			// open a connection to X-server
			urlConn = ( HttpURLConnection )url.openConnection();

			XMLCleanup xmlCleanup = new XMLCleanup();
			xml = xmlCleanup.cleanup( urlConn.getInputStream() );

			// disconnect
			urlConn.disconnect();
		} catch( MalformedURLException mue ) {
			LOG.warn( "doURLConnection() malformed URL" );
			wrapXServerException( null, "Error in connecting to X-Server. Please contact Citations Helper Administrator." );
		} catch( IOException ioe ) {
			LOG.warn( "doURLConnection() IOException, connection failed" );
			wrapXServerException( null, "Error in connecting to X-Server. Please contact Citations Helper Administrator." );
		} catch( XServerException xse ) {
			LOG.warn( "doURLConnection() - XServerException: " +
					xse.getErrorCode() + " - " + xse.getErrorText() );
			wrapXServerException( xse.getErrorCode(), xse.getErrorText() + "Please contact Citations Helper Administrator." );
		}

		return xml;
	}
	
	private void wrapXServerException( String errorCode, String errorMsg ) throws XServerException
	{
		// update searchStatusProperties
		MetasearchSession metasearchSession = msm.getMetasearchSession( guid );
		java.util.Properties searchStatusProperties = metasearchSession.
		getSearchStatusProperties();
		searchStatusProperties.put( "status", "error" );
		searchStatusProperties.put( "statusMessage", errorMsg );
		metasearchSession.setSearchStatusProperties(searchStatusProperties);
		msm.putMetasearchSession( guid, metasearchSession );

		// throw the XServerException now that status has been updated
		throw new XServerException( errorCode, errorMsg );
	}

	/**
	 * Initiate the SAX Parser with the given InputStream.
	 * 
	 * @param is InputStream to parse
	 * 
	 * @throws IOException
	 * @throws SAXException
	 */
	private void saxParseXML( InputStream is )
	throws IOException, SAXException {
		// run the SAX Parser
		saxParser.parse( is, this );
		is.close();
	}

	/**
	 * Validate login X-service
	 * 
	 * @return true if succesful, false otherwise
	 */
	private boolean loginSuccessful() {
		if( auth != null && auth.equals( "N" ) ) {
			return false;
		}
		return true;
	}
}

