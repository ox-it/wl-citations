/**********************************************************************************
 * $URL:  $
 * $Id:   $
 ***********************************************************************************
 *
 * Copyright (c) 2006 The Sakai Foundation.
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

package org.sakaiproject.citation.impl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.Vector;

import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityAdvisor.SecurityAdvice;
import org.sakaiproject.authz.cover.SecurityService;
import org.sakaiproject.citation.util.api.OsidConfigurationException;
import org.sakaiproject.citation.api.ConfigurationService;
import org.sakaiproject.citation.api.SiteOsidConfiguration;

import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.content.api.ContentHostingService;
import org.sakaiproject.content.api.ContentResource;
import org.sakaiproject.entity.api.ContextObserver;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entity.cover.EntityManager;
import org.sakaiproject.event.api.Event;
import org.sakaiproject.event.cover.EventTrackingService;
import org.sakaiproject.exception.IdInvalidException;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.exception.IdUsedException;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.exception.ServerOverloadException;
import org.sakaiproject.exception.TypeException;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SitePage;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.site.api.ToolConfiguration;
import org.sakaiproject.tool.api.SessionManager;

/**
 *
 */
public class BaseConfigurationService implements ConfigurationService, Observer
{
	private static Log m_log = LogFactory.getLog(BaseConfigurationService.class);

	/*
	 * All the following properties will be set by Spring using components.xml
	 */
	// enable/disable entire helper
    protected boolean m_citationsEnabledByDefault = false;
    protected boolean m_allowSiteBySiteOverride = false;

	// enable/disable helper features -->
    protected String m_googleSearchEnabled = "false";
    protected String m_librarySearchEnabled = "false";

    protected String m_adminSiteName = "citationsAdmin";
    protected String m_configFolder = "config";
    protected String m_configXml = "sakai/citationsConfig.xml";
    protected String m_categoriesXml = "sakai/databaseHierarchy.xml";

    protected SortedSet<String> m_updatableResources = new TreeSet<String>();

	// configuration XML file location
    protected String m_databaseXml;
    protected String m_siteConfigXml;

  	// metasearch engine parameters
    protected String m_metasearchUsername;
    protected String m_metasearchPassword;
    protected String m_metasearchBaseUrl;

	// which osid impl to use
    protected String m_osidImpl;

	// openURL parameters -->
    protected String m_openUrlLabel;
    protected String m_openUrlResolverAddress;

	// google scholar parameters -->
    protected String m_googleBaseUrl;
    protected String m_sakaiServerKey;

	// site-specific config/authentication/authorization implementation -->
    protected String m_osidConfig;

	// other config services -->
	protected SessionManager m_sessionManager;
	protected ServerConfigurationService m_serverConfigurationService;

	private TreeSet<String> m_categories;

	private TreeSet<String> m_configs;
	/*
	 * End of components.xml properties
	 */

	/*
	 * Site specific OSID configuration instance
	 */
	private static SiteOsidConfiguration m_siteConfigInstance = null;

  /*
   * Dynamic configuration parameters
   */
  protected static Map<String, Map<String,String>> m_configMaps = new HashMap<String, Map<String,String>>();

  protected static String m_configListRef = null;

  /*
   * Failed configurations (didn't parse properly)
   */
  protected static List<String> m_failedConfigs = new ArrayList<String>();

	/*
	 * Interface methods
	 */

	/**
	 * Fetch the appropriate XML configuration document for this user
	 * @return Configuration XML resource name
	 */
	public String getConfigurationXml() throws OsidConfigurationException
	{
		SiteOsidConfiguration siteConfig  = getSiteOsidConfiguration();
		String                configXml   = null;

		if (siteConfig != null)
		{
			configXml = siteConfig.getConfigurationXml();
		}

		if (isNull(configXml))
		{
			configXml = m_siteConfigXml;
		}
		return configXml;
	}

  /**
   * Is the configuration XML file provided and readable
   * @return true If the XML file is provided and readable, false otherwise
   */
  public boolean isConfigurationXmlAvailable()
  {
    try
    {
    	// get the xml filename
    	String configXml = getConfigurationXml();
    	if( configXml == null )
    	{
    		return false;
    	}

    	// not null, try to open it for reading
    	//java.io.FileInputStream fis = new java.io.FileInputStream( configXml );
    }
/*
    catch( java.io.FileNotFoundException fnfe )
    {
    	// file not found
    	return false;
    }
*/
    catch (OsidConfigurationException ignore) { }

    // filename is not null and the file is readable
    return true;
  }

	public String getConfigFolderReference()
    {
	    String configFolderRef = null;
		if(! isNull(this.m_adminSiteName) && ! isNull(this.m_configFolder))
		{
			configFolderRef = "/content/group/" + this.m_adminSiteName + "/" + this.m_configFolder + "/";
		}
		return configFolderRef;

    }

	public String getConfigFolderId()
	{
		String configFolderId = null;
		if(! isNull(this.m_adminSiteName) && ! isNull(this.m_configFolder))
		{
			configFolderId = "/group/" + this.m_adminSiteName + "/" + this.m_configFolder + "/";
		}
		return configFolderId;
	}

  /**
   * Fetch the appropriate XML database hierarchy document for this user
   * @return Hierarchy XML resource name
   */
  public String getDatabaseHierarchyXml() throws OsidConfigurationException
  {
    SiteOsidConfiguration   siteConfig  = getSiteOsidConfiguration();
    String                  databaseXml = null;

    if (siteConfig != null)
    {
      databaseXml = siteConfig.getDatabaseHierarchyXml();
    }

    if (isNull(databaseXml))
    {
      databaseXml = m_databaseXml;
    }
    return databaseXml;
  }

  /**
   * Is the database hierarchy XML file provided and readable
   * @return true If the XML file is provided and readable, false otherwise
   */
  public boolean isDatabaseHierarchyXmlAvailable()
  {
    try
    {
    	// get the xml filename
    	String dbXml = getDatabaseHierarchyXml();
    	if( dbXml == null )
    	{
    		return false;
    	}

    	// not null, try to open it for reading
    	// java.io.FileInputStream fis = new java.io.FileInputStream( dbXml );
    }
/*
    catch( java.io.FileNotFoundException fnfe )
    {
    	// file not found
    	return false;
    }
*/
    catch (OsidConfigurationException ignore) { }

    // filename is not null and the file is readable
    return true;
  }

  /**
   * Fetch this user's group affiliations
   * @return A list of group IDs (empty if no IDs exist)
   */
  public List<String> getGroupIds() throws OsidConfigurationException
  {
    SiteOsidConfiguration   siteConfig  = getSiteOsidConfiguration();

    if (siteConfig == null)
    {
      ArrayList<String> emptyList = new ArrayList();

      return emptyList;
    }
    return siteConfig.getGroupIds();
  }

  /**
   * Fetch the site specific Repository OSID package name
   * @return Repository Package (eg org.sakaibrary.osid.repository.xserver)
   */
  public synchronized String getSiteConfigOsidPackageName()
  {
    String value = getConfigurationParameter("osid-impl");

    return (value != null) ? value : getOsidImpl();
  }

  /**
   * Fetch the meta-search username
   * @return the username
   */
  public synchronized String getSiteConfigMetasearchUsername()
  {
    String value = getConfigurationParameter("metasearch-username");

    return (value != null) ? value : getMetasearchUsername();
  }

  /**
   * Fetch the meta-search password
   * @return the password
   */
  public synchronized String getSiteConfigMetasearchPassword()
  {
    String value = getConfigurationParameter("metasearch-password");

    return (value != null) ? value : getMetasearchPassword();
  }

  /**
   * Fetch the meta-search base-URL
   * @return the username
   */
  public synchronized String getSiteConfigMetasearchBaseUrl()
  {
    String value = getConfigurationParameter("metasearch-baseurl");

    return (value != null) ? value : getMetasearchBaseUrl();
  }

  /**
   * Fetch the OpenURL label
   * @return the label text
   */
  public synchronized String getSiteConfigOpenUrlLabel()
  {
    String value = getConfigurationParameter("openurl-label");

    return (value != null) ? value : getOpenUrlLabel();
  }

  /**
   * Fetch the OpenURL resolver address
   * @return the resolver address (domain name or IP)
   */
  public synchronized String getSiteConfigOpenUrlResolverAddress()
  {
    String value = getConfigurationParameter("openurl-resolveraddress");

    return (value != null) ? value : getOpenUrlResolverAddress();
  }

  /**
   * Fetch the Google base-URL
   * @return the URL
   */
  public synchronized String getSiteConfigGoogleBaseUrl()
  {
    String value = getConfigurationParameter("google-baseurl");

    return (value != null) ? value : getGoogleBaseUrl();
  }

  /**
   * Fetch the Sakai server key
   * @return the key text
   */
  public synchronized String getSiteConfigSakaiServerKey()
  {
    String value = getConfigurationParameter("sakai-serverkey");

    return (value != null) ? value : getSakaiServerKey();
  }

  /**
   * Enable/disable Citations Helper by default
   * @param state true to set default 'On'
   */
  public void setCitationsEnabledByDefault(boolean citationsEnabledByDefault)
  {
    m_citationsEnabledByDefault = citationsEnabledByDefault;
  }

  /**
   * Is Citations Helper by default enabled?
   * @return true if so
   */
  public boolean isCitationsEnabledByDefault()
  {
    return m_citationsEnabledByDefault;
  }

  /**
   * Enable/disable site by site Citations Helper override
   * @param state true to enable site by site Citations Helper
   */
  public void setAllowSiteBySiteOverride(boolean allowSiteBySiteOverride)
  {
	  m_allowSiteBySiteOverride = allowSiteBySiteOverride;
  }

  /**
   * Is site by site Citations Helper enabled?
   * @return true if so
   */
  public boolean isAllowSiteBySiteOverride()
  {
    return m_allowSiteBySiteOverride;
  }

  /**
   * Enable/disable Google support (no support for site specific XML configuration)
   * @param state true to enable Google support
   */
  public void setGoogleScholarEnabled(boolean state)
  {
    String enabled = state ? "true" : "false";

    setGoogleSearchEnabled(enabled);
  }

  /**
   * Is Google search enabled? (no support for site specific XML configuration)
   * @return true if so
   */
  public boolean isGoogleScholarEnabled()
  {
    String state = getGoogleSearchEnabled();

    return state.equals("true");
  }

  /**
   * Enable/disable library search support (no support for site specific XML configuration)
   * @param state true to enable support
   */
  public void setLibrarySearchEnabled(boolean state)
  {
    String enabled = state ? "true" : "false";

    setLibrarySearchEnabled(enabled);
  }

  /**
   * Is library search enabled? (no support for site specific XML configuration)
   * @return true if so
   */
  public boolean isLibrarySearchEnabled()
  {
    String state = getLibrarySearchEnabled();

    return state.equals("true");
  }

  /*
   * Helpers
   */

  /**
   * Get a named value from the site-specific XML configuration file
   * @param parameter Configuration parameter to lookup
   * @return Parameter value (null if none [or error])
   */
  protected String getConfigurationParameter(String parameter)
  {
    Map<String, String> parameterMap  = null;

    try
    {
      SiteOsidConfiguration siteConfig = getSiteOsidConfiguration();
      String configXml;
      /*
       * Fetch the configuration XML resource name
       */
      if (siteConfig == null)
      {
        return null;
      }

      if ((configXml = siteConfig.getConfigurationXml()) == null)
      {
        return null;
      }
      String configXmlRef = this.getConfigFolderReference() + configXml;
      /*
       * Look up the requested configuration Map - if it doesn't already exist,
       * try to load it "on the fly"
       */
      synchronized (this)
      {
        parameterMap = m_configMaps.get(configXmlRef);
        if (parameterMap == null)
        {
          m_log.debug("New configuration requested from: " + configXml);

          updateConfig(configXmlRef);

          parameterMap = m_configMaps.get(configXmlRef);
          if (parameterMap != null)
          {
   	    	  if (!this.m_updatableResources.contains(configXmlRef))
   	    	  {
   	    	    this.m_updatableResources.add(configXmlRef);
            }
            m_log.debug("Now observing " + configXml);
          }
        }
      }
    }
    catch (OsidConfigurationException exception)
    {
      m_log.warn("Failed to get dynamic XML value for "
              +  parameter
              +  ": "
              +  exception);
    }
    /*
     * Finally, return the requested configuration parameter
     */
    m_log.debug("getParameter() returns: " + ((parameterMap == null) ? null : parameterMap.get(parameter)));
    return (parameterMap == null) ? null : parameterMap.get(parameter);
  }


  /**
   * Load and initialize the site-specific OSID configuration code
   * @return The initialized, site-specific OSID configuration
   *         object (null on error)
   */
  protected SiteOsidConfiguration getSiteOsidConfiguration()
  {
	  SiteOsidConfiguration siteConfig;

    try
    {
	    siteConfig = getConfigurationHandler(m_osidConfig);
	    siteConfig.init();
	  }
	  catch (Exception exception)
	  {
	    m_log.warn("Failed to get " + m_osidConfig + ": " + exception);
	    siteConfig = null;
	  }
    return siteConfig;
  }

  /**
   * Return a SiteOsidConfiguration instance
   * @return A SiteOsidConfiguration
   */
  public synchronized
         SiteOsidConfiguration getConfigurationHandler(String osidConfigHandler)
                                      throws  java.lang.ClassNotFoundException,
      							  					              java.lang.InstantiationException,
  	    												              java.lang.IllegalAccessException
  {
    if (m_siteConfigInstance == null)
    {
      Class configClass = Class.forName(osidConfigHandler);

      m_siteConfigInstance = (SiteOsidConfiguration) configClass.newInstance();
    }
    return m_siteConfigInstance;
  }

  /**
   * Populate cached values from configuration XML.  Don't even try
   * if the requested XML resource has already failed to parse.
   *
   * Invoked by <code>getConfigurationParameter()</code>.
   *
   * @param configXmlRef Configuration resource name
   */
  protected void populateConfigOnReference(String configXmlRef)
  {
    if (m_failedConfigs.contains(configXmlRef))
    {
      return;
    }
    populateConfig(configXmlRef);
  }

  /**
   * Populate cached values from a configuration XML resource.  We always try
   * to parse the resource, regardless of any prior success or failure.
   *
   * @param configurationXml Configuration resource name (this doubles as a
   *                         unique key into the configuration cache)
   */
  public void populateConfig(String configurationXml)
  {
    org.w3c.dom.Document  document;
    String                value;

    if (configurationXml == null)
    {
      return;
    }

    document = parseXmlFromUri(configurationXml);

    synchronized (this)
    {
      Map<String, String> parameterMap;

      /*
       * If the parse fails, flag it and give up now
       */
      if (document == null)
      {
        if (!m_failedConfigs.contains(configurationXml))
        {
          m_failedConfigs.add(configurationXml);
        }
        return;
      }
      /*
       * Successful parse - save the values (and clear the "failed" flag)
       */
      m_failedConfigs.remove(configurationXml);

      if ((parameterMap = m_configMaps.get(configurationXml)) == null)
      {
        parameterMap = new HashMap<String, String>();
      }
      parameterMap.clear();

      saveParameter(document, parameterMap, "osid-impl");
      saveParameter(document, parameterMap, "metasearch-username");
      saveParameter(document, parameterMap, "metasearch-password");
      saveParameter(document, parameterMap, "metasearch-baseurl");
      saveParameter(document, parameterMap, "openurl-label");           // obsolete?
      saveParameter(document, parameterMap, "openurl-resolveraddress");
      saveParameter(document, parameterMap, "google-baseurl");
      saveParameter(document, parameterMap, "sakai-serverkey");
      saveParameter(document, parameterMap, "config-id");
      saveParameter(document, parameterMap, "hierarchy-xml");

      m_configMaps.put(configurationXml, parameterMap);
    }
  }

  /**
   * Populate cached values from a configuration XML resource.  We always try
   * to parse the resource, regardless of any prior success or failure.
   *
   * @param configurationXml Configuration resource name (this doubles as a
   *                         unique key into the configuration cache)
   */
  public void populateConfig(String configurationXml, InputStream stream)
  {
    org.w3c.dom.Document  document;
    String                value;

    document = parseXmlFromStream(stream);

    synchronized (this)
    {
      Map<String, String> parameterMap;

      /*
       * If the parse fails, flag it and give up now
       */
      if (document == null)
      {
        if (!m_failedConfigs.contains(configurationXml))
        {
          m_failedConfigs.add(configurationXml);
        }
        return;
      }
      /*
       * Successful parse - save the values (and clear the "failed" flag)
       */
      m_failedConfigs.remove(configurationXml);

      if ((parameterMap = m_configMaps.get(configurationXml)) == null)
      {
        parameterMap = new HashMap<String, String>();
      }
      parameterMap.clear();

      saveParameter(document, parameterMap, "osid-impl");
      saveParameter(document, parameterMap, "metasearch-username");
      saveParameter(document, parameterMap, "metasearch-password");
      saveParameter(document, parameterMap, "metasearch-baseurl");
      saveParameter(document, parameterMap, "openurl-label");           // obsolete?
      saveParameter(document, parameterMap, "openurl-resolveraddress");
      saveParameter(document, parameterMap, "google-baseurl");
      saveParameter(document, parameterMap, "sakai-serverkey");
      saveParameter(document, parameterMap, "config-id");
      saveParameter(document, parameterMap, "database-xml");

      m_configMaps.put(configurationXml, parameterMap);
    }
  }

  /**
   * Lookup and save one dynamic configuration parameter
   * @param Configuration XML
   * @param parameterMap Parameter name=value pairs
   * @param name Parameter name
   */
  protected void saveParameter(org.w3c.dom.Document document,
                             Map parameterMap, String name)
  {
    String value;

    if ((value = getText(document, name)) != null)
    {
      parameterMap.put(name, value);
      m_log.debug("New configuration value: " + name + " = " + value);
    }
  }

  /**
   * Parse an XML file into a Document.
   * @param filename The filename (or URI) to parse
   * @return DOM Document (null if parse fails)
   */
  protected Document parseXmlFromUri(String filename)
  {
    try
    {
      DocumentBuilder documentBuilder = getXmlDocumentBuilder();

      if (documentBuilder != null)
      {
        return documentBuilder.parse(filename);
      }
    }
    catch (Exception exception)
    {
      m_log.warn("XML parse on \"" + filename + "\" failed: " + exception);
    }
    return null;
  }

  /**
   * Parse an XML file into a Document.
   * @param filename The filename (or URI) to parse
   * @return DOM Document (null if parse fails)
   */
  protected Document parseXmlFromStream(InputStream stream)
  {
    try
    {
      DocumentBuilder documentBuilder = getXmlDocumentBuilder();

      if (documentBuilder != null)
      {
        return documentBuilder.parse(stream);
      }
    }
    catch (Exception exception)
    {
      m_log.warn("XML parse on \"" + stream + "\" failed: " + exception);
    }
    return null;
  }

  /**
   * Get a DOM Document builder.
   * @return The DocumentBuilder
   * @throws DomException
   */
  protected DocumentBuilder getXmlDocumentBuilder()
  {
    try
    {
      DocumentBuilderFactory factory;

      factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);

      return factory.newDocumentBuilder();
    }
    catch (Exception exception)
    {
      m_log.warn("Failed to get XML DocumentBuilder: " + exception);
    }
    return null;
  }

  /**
   * "Normalize" XML text node content to create a simple string
   * @param original Original text
   * @param update Text to add to the original string (a space separates the two)
   * @return Concatenated contents (trimmed)
   */
  protected String normalizeText(String original, String update)
  {
  	StringBuffer	result;

    if (original == null)
    {
      return (update == null) ? "" : update.trim();
    }

    result = new StringBuffer(original.trim());
    result.append(' ');
    result.append(update.trim());

    return result.toString();
  }

  /**
   * Get the text associated with this element
   * @param root The document containing the text element
   * @return Text (trimmed of leading/trailing whitespace, null if none)
   */
  protected String getText(Document root, String elementName)
  {
    return getText(root.getDocumentElement(), elementName);
  }

  /**
   * Get the text associated with this element
   * @param root The root node of the text element
   * @return Text (trimmed of leading/trailing whitespace, null if none)
   */
  protected String getText(Element root, String elementName)
  {
    NodeList  nodeList;
    Node      parent;
    String    text;

    nodeList = root.getElementsByTagName(elementName);
    if (nodeList.getLength() == 0)
    {
      return null;
    }

    text = null;
    parent = (Element) nodeList.item(0);

    for (Node child = parent.getFirstChild();
              child != null;
              child = child.getNextSibling())
    {
      switch (child.getNodeType())
      {
        case Node.TEXT_NODE:
          text = normalizeText(text, child.getNodeValue());
          break;

        default:
          break;
      }
    }
    return text == null ? text : text.trim();
  }

  /**
   * Null (or empty) String?
   * @param string String to check
   * @return true if so
   */
	private boolean isNull(String string)
	{
		return (string == null) || (string.trim().equals(""));
	}

	/**
	 * Establish a security advisor to allow the "embedded" azg work to occur
	 * with no need for additional security permissions.
	 */
	protected void enableSecurityAdvisor()
	{
		// put in a security advisor so we can create citationAdmin site without need
		// of further permissions
		SecurityService.pushAdvisor(new SecurityAdvisor() {
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});
	}

  /*
   * Inititialize and destroy
   */
 	public void init()
	{
		m_log.info("init()");

		EventTrackingService.addObserver(this);

		SiteService siteService = (SiteService) ComponentManager.get(SiteService.class);
		ContentHostingService contentService = (ContentHostingService) ComponentManager.get(ContentHostingService.class);

		if(isNull(this.m_adminSiteName))
		{
			// can't create
		}
		else if(siteService.siteExists(this.m_adminSiteName))
		{
			// no need to create
		}
		else
		{
			// need to create
			try
            {
				enableSecurityAdvisor();
				Site adminSite = siteService.addSite(this.m_adminSiteName, "project");

				// add Resources tool
				SitePage resPage = adminSite.addPage();
				resPage.addTool("sakai.resources");

				enableSecurityAdvisor();
				siteService.save(adminSite);
            }
            catch (IdInvalidException e)
            {
	            // TODO Auto-generated catch block
	            m_log.warn("IdInvalidException ", e);
            }
            catch (IdUsedException e)
            {
	            // we've already verified that the site doesn't exist but
            	// this can occur if site was created by another server
            	// in a cluster that is starting up at the same time.
            	m_log.warn("IdUsedException ", e);
            }
            catch (PermissionException e)
            {
	            // TODO Auto-generated catch block
            	m_log.warn("PermissionException ", e);
            }
            catch (IdUnusedException e)
            {
	            // TODO Auto-generated catch block
	            m_log.warn("IdUnusedException ", e);
            }
		}

		for(String config : this.m_configs)
		{
			String configFileRef = this.getConfigFolderReference() + config;
	        updateConfig(configFileRef);
		}
	}

	public void destroy()
	{
		m_log.info("destroy()");
	}

  /*
   * Getters/setters for components.xml parameters
   */

	/**
   * @return the OSID package name
   */
  public String getOsidImpl()
  {
  	return m_osidImpl;
  }

	/**
   * @param osidImpl the OSID package name
   */
	public void setOsidImpl(String osidImpl)
	{
		m_osidImpl = osidImpl;
	}

	/**
   * @return the m_metasearchUsername
   */
  public String getMetasearchUsername()
  {
  	return m_metasearchUsername;
  }

	/**
   * @param username the m_metasearchUsername to set
   */
  public void setMetasearchUsername(String username)
  {
  	m_metasearchUsername = username;
  }

	/**
   * @return the m_metasearchBaseUrl
   */
  public String getMetasearchBaseUrl()
  {
  	return m_metasearchBaseUrl;
  }

	/**
   * @param baseUrl the m_metasearchBaseUrl to set
   */
  public void setMetasearchBaseUrl(String baseUrl)
  {
  	m_metasearchBaseUrl = baseUrl;
  }

	/**
   * @return the m_metasearchPassword
   */
  public String getMetasearchPassword()
  {
  	return m_metasearchPassword;
  }

	/**
   * @param password the m_metasearchPassword to set
   */
  public void setMetasearchPassword(String password)
  {
  	m_metasearchPassword = password;
  }

  /**
   * @return the Google base URL
   */
  public String getGoogleBaseUrl()
  {
  	return m_googleBaseUrl;
  }

	/**
   * @param googleBaseUrl the base URL to set
   */
  public void setGoogleBaseUrl(String googleBaseUrl)
  {
  	m_googleBaseUrl = googleBaseUrl;
  }

	/**
   * @return the sakaiServerKey
   */
  public String getSakaiServerKey()
  {
  	return m_sakaiServerKey;
  }

	/**
   * @param sakaiServerKey the sakaiServerKey to set
   */
  public void setSakaiServerKey(String sakaiId)
  {
  	m_sakaiServerKey = sakaiId;
  }

	/**
   * @return the OpenURL label
   */
  public String getOpenUrlLabel()
  {
  	return m_openUrlLabel;
  }

	/**
   * @param set the OpenURL label
   */
  public void setOpenUrlLabel(String openUrlLabel)
  {
  	m_openUrlLabel = openUrlLabel;
  }

	/**
   * @return the OpenURL resolver address
   */
  public String getOpenUrlResolverAddress()
  {
  	return m_openUrlResolverAddress;
  }

	/**
   * @param set the OpenURL resolver address
   */
  public void setOpenUrlResolverAddress(String openUrlResolverAddress)
  {
  	m_openUrlResolverAddress = openUrlResolverAddress;
  }

	/**
   * @return the database hierarchy XML filename/URI
   */
  public String getDatabaseXml()
  {
  	return m_databaseXml;
  }

	/**
   * @param set the database hierarchy XML filename/URI
   */
  public void setDatabaseXml(String databaseXml)
  {
  	m_databaseXml = databaseXml;
  }

	/**
   * @return the configuration XML filename/URI
   */
  public String getSiteConfigXml()
  {
  	return m_siteConfigXml;
  }

	/**
   * @param set the configuration XML filename/URI
   */
  public void setSiteConfigXml(String siteConfigXml)
  {
  	m_siteConfigXml = siteConfigXml;
  }

	/**
   * @return the serverConfigurationService
   */
  public ServerConfigurationService getServerConfigurationService()
  {
  	return m_serverConfigurationService;
  }

	/**
   * @param serverConfigurationService the serverConfigurationService to set
   */
  public void setServerConfigurationService(ServerConfigurationService serverConfigurationService)
  {
  	m_serverConfigurationService = serverConfigurationService;
  }

	/**
   * @param sessionManager the SessionManager to save
   */
	public void setSessionManager(SessionManager sessionManager)
	{
		m_sessionManager = sessionManager;
	}

	/**
   * @return the SessionManager
   */
	public SessionManager getSessionManager()
	{
		return m_sessionManager;
	}

	/**
   * @return the site specific "OSID configuration" package name
   */
  public String getOsidConfig()
  {
  	return m_osidConfig;
  }

	/**
   * @param osidConfig the site specific "OSID configuration" package name
   */
	public void setOsidConfig(String osidConfig)
	{
		m_osidConfig = osidConfig;
	}

	/**
   * @return Google search support status
   */
  public String getGoogleSearchEnabled()
  {
  	return m_googleSearchEnabled;
  }

	/**
   * @param googleSearchEnabled ("true" or "false")
   */
	public void setGoogleSearchEnabled(String googleSearchEnabled)
	{
	  if (googleSearchEnabled.equalsIgnoreCase("true") ||
			  googleSearchEnabled.equalsIgnoreCase("false"))
	  {
	    m_googleSearchEnabled = googleSearchEnabled;
	    return;
	  }

	  m_log.warn("Invalid Google support setting \""
	        +    googleSearchEnabled
	        +    "\", disabling Google search");

	  m_googleSearchEnabled = "false";
	}

	/**
   * @return library search support status
   */
  public String getLibrarySearchEnabled()
  {
  	return m_librarySearchEnabled;
  }

	/**
   * @param librarySearchEnabled ("true" or "false")
   */
	public void setLibrarySearchEnabled(String librarySearchEnabled)
	{
	  if (librarySearchEnabled.equalsIgnoreCase("true") ||
			  librarySearchEnabled.equalsIgnoreCase("false"))
	  {
	    m_librarySearchEnabled = librarySearchEnabled;
	    return;
	  }

	  m_log.warn("Invalid library support setting \""
	        +    librarySearchEnabled
	        +    "\", disabling library search");

	  m_librarySearchEnabled = "false";
	}

	/**
     * @return the adminSiteName
     */
    public String getAdminSiteName()
    {
    	return m_adminSiteName;
    }

	/**
     * @param adminSiteName the adminSiteName to set
     */
    public void setAdminSiteName(String adminSiteName)
    {
    	this.m_adminSiteName = adminSiteName;
    }

	/**
     * @return the configFile
     */
    public String getConfigXml()
    {
       	StringBuffer buf = new StringBuffer();
    	for(Iterator<String> it = this.m_configs.iterator(); it.hasNext();)
    	{
    		String str = it.next();
    		buf.append(str);
    		if(it.hasNext())
    		{
    			buf.append(',');
    		}
    	}
    	return buf.toString();
    }

	/**
     * @param configFile the configFile to set
     */
    public void setConfigXml(String configXml)
    {
    	this.m_configs = new TreeSet<String>();
    	if(!isNull(configXml))
    	{
    	   	String[] configs = configXml.split("\\s*,\\s*");
    	   	for(String config : configs)
    	   	{
    	   		this.m_configs.add(config);
    	   	}
    	}
    }

	/**
     * @return the configFolder
     */
    public String getConfigFolder()
    {
    	return m_configFolder;
    }

	/**
     * @param configFolder the configFolder to set
     */
    public void setConfigFolder(String configFolder)
    {
    	this.m_configFolder = configFolder;
    }

	public void update(Observable arg0, Object arg1)
    {
	    if(arg1 instanceof Event)
	    {
	    	Event event = (Event) arg1;
	    	String refstr = event.getResource();

	    	if(this.m_updatableResources.contains(refstr))
	    	{
	    		// update the hierarchy
	    		updateConfig(refstr);
	    	}
	    }
    }

	protected void updateConfig(String configFileRef)
    {
		Reference ref = EntityManager.newReference(configFileRef);

		m_log.debug("UpdateConfig() processing: configFileRef = " + configFileRef);

		if(ref != null)
		{
			ContentHostingService contentService = (ContentHostingService) ComponentManager.get(ContentHostingService.class);
			try
            {
				enableSecurityAdvisor();
				ContentResource resource = contentService.getResource(ref.getId());
	            populateConfig(configFileRef, resource.streamContent());
            }
            catch (PermissionException e)
            {
	            // TODO Auto-generated catch block
	            m_log.warn("PermissionException ", e);
            }
            catch (IdUnusedException e)
            {
	            m_log.info("XML file which defines Citations configuration is missing (" + configFileRef + "); Citations ConfigurationService will watch for its creation");
            }
            catch (TypeException e)
            {
	            // TODO Auto-generated catch block
	            m_log.warn("TypeException ", e);
            }
            catch (ServerOverloadException e)
            {
	            // TODO Auto-generated catch block
	            m_log.warn("ServerOverloadException ", e);
            }
		}
		m_updatableResources.add(configFileRef);

    }

	/**
     * @return the categoriesXml
     */
    public String getCategoriesXml()
    {
    	StringBuffer buf = new StringBuffer();
    	for(Iterator<String> it = this.m_categories.iterator(); it.hasNext();)
    	{
    		String str = it.next();
    		buf.append(str);
    		if(it.hasNext())
    		{
    			buf.append(',');
    		}
    	}
    	return buf.toString();
    }

	/**
     * @param categoriesXml the categoriesXml to set
     */
    public void setCategoriesXml(String categoriesXml)
    {
    	this.m_categories = new TreeSet<String>();
    	if(!isNull(categoriesXml))
    	{
    	   	String[] categories = categoriesXml.split("\\s*,\\s*");
    	   	for(String category : categories)
    	   	{
    	   		this.m_categories.add(category);
    	   	}
    	}
    }

	public Collection<String> getAllCategoryXml()
    {
	    return new TreeSet<String>(this.m_categories);
    }

}
