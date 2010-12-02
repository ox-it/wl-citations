package org.sakaiproject.citation.impl.openurl;

import java.util.Hashtable;
import java.util.Map;
import java.util.StringTokenizer;

import javax.servlet.http.HttpServletRequest;

import org.sakaiproject.citation.api.Citation;
import org.sakaiproject.citation.api.Schema;
import org.sakaiproject.citation.impl.openurl.ContextObject.Entity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.AbstractSingleSpringContextTests;

public class OpenURLServiceImplTest extends AbstractSingleSpringContextTests {

	private static final String PRIMO_EXAMPLE = "ctx_ver=Z39.88-2004&ctx_enc=info:ofi/enc:UTF-8&" +
	"ctx_tim=2010-10-20T13:27:00IST&url_ver=Z39.88-2004&url_ctx_fmt=infofi/fmt:kev:mtx:ctx&" +
	"rfr_id=info:sid/primo.exlibrisgroup.com:primo3-Journal-UkOxU&rft_val_fmt=info:ofi/fmt:kev:mtx:book&" +
	"rft.genre=book&rft.atitle=&rft.jtitle=&rft.btitle=Linux%20in%20a%20nutshell&rft.aulast=Siever&" +
	"rft.auinit=&rft.auinit1=&rft.auinitm=&rft.ausuffix=&rft.au=&rft.aucorp=&rft.volume=&rft.issue=&" +
	"rft.part=&rft.quarter=&rft.ssn=&rft.spage=&rft.epage=&rft.pages=&rft.artnum=&rft.issn=&rft.eissn=&" +
	"rft.isbn=9780596154486&rft.sici=&rft.coden=&rft_id=info:doi/&rft.object_id=&" +
	"rft_dat=<UkOxU>UkOxUb17140770</UkOxU>&rft.eisbn=";

	private OpenURLServiceImpl service;

	protected void onSetUp() throws Exception {
		this.service = (OpenURLServiceImpl) getApplicationContext().getBean("org.sakaiproject.citation.impl.openurl.OpenURLServiceImpl");

	}
	
	protected String[] getConfigLocations() {
		return new String[] {
				"classpath:org/sakaiproject/citation/impl/openurl.xml",
				"classpath:org/sakaiproject/citation/impl/openurl/test-beans.xml"
		};
	}
	
	public void testConvertCitation() {
		assertNull(service.convert((Citation)null));
	}
	
	public void testConvertContextObject() {
		assertNull(service.convert((ContextObject)null));
	}
	
	public void testParseNull() {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "http://localhost:8080/someurl");
		ContextObject contextObject = service.parse(req);
		assertNull(contextObject);
	}
	
	public void testParsePrimo() {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", "http://localhost:8080/someurl?" + PRIMO_EXAMPLE);
		req.setQueryString(PRIMO_EXAMPLE);
		req.setParameters(parseQueryString(PRIMO_EXAMPLE));
		
		ContextObject contextObject = service.parse(req);
		assertNotNull(contextObject);
		ContextObjectEntity book = contextObject.getEntity(Entity.REFERENT);
		assertEquals("Linux in a nutshell", book.getValue("btitle"));
		
		Citation bookCitation = service.convert(contextObject);
		assertEquals("Linux in a nutshell", bookCitation.getCitationProperty(Schema.TITLE));
	}
	
	public void testParseBook() {
		Citation book = convert(find(mockGetRequest(SamplePrimoOpenURLs.BOOK)));
		Map props = book.getCitationProperties();
		assertEquals("", props.get("title"));
		
	}
	
	public Citation convert(ContextObject contextObject) {
		Citation citation = service.convert(contextObject);
		assertNotNull(citation);
		return citation;
	}
	
	public HttpServletRequest mockGetRequest(String url) {
		MockHttpServletRequest req = new MockHttpServletRequest("GET", url);
		init(req);
		return req;
	}
	
	public ContextObject find(HttpServletRequest req) {
		ContextObject contextObject = service.parse(req);
		assertNotNull(contextObject);
		return contextObject;
	}
	
	/**
	 * Sets up the query string and parameters based on the URL.
	 * @param req
	 */
	public void init(MockHttpServletRequest req) {
		String url = req.getRequestURL().toString();
		int queryStart = url.indexOf('?');
		if (queryStart >= 0) {
			String query = url.substring(queryStart+1);
			req.setQueryString(query);
			req.setParameters(parseQueryString(query));
		}
	}
	
	// Stolen from HttpUtils.parseQueryString()
	public static Map parseQueryString(String s) {
		String valArray[] = null;

		if (s == null) {
			throw new IllegalArgumentException();
		}
		Hashtable ht = new Hashtable();
		StringBuffer sb = new StringBuffer();
		StringTokenizer st = new StringTokenizer(s, "&");
		while (st.hasMoreTokens()) {
			String pair = (String) st.nextToken();
			int pos = pair.indexOf('=');
			if (pos == -1) {
				// XXX
				// should give more detail about the illegal argument
				throw new IllegalArgumentException();
			}
			String key = parseName(pair.substring(0, pos), sb);
			String val = parseName(pair.substring(pos + 1, pair.length()), sb);
			if (ht.containsKey(key)) {
				String oldVals[] = (String[]) ht.get(key);
				valArray = new String[oldVals.length + 1];
				for (int i = 0; i < oldVals.length; i++)
					valArray[i] = oldVals[i];
				valArray[oldVals.length] = val;
			} else {
				valArray = new String[1];
				valArray[0] = val;
			}
			ht.put(key, valArray);
		}
		return ht;
	}
	
	// Stolen from HttpUtils.parseQueryString()
	static private String parseName(String s, StringBuffer sb) {
		sb.setLength(0);
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '+':
				sb.append(' ');
				break;
			case '%':
				try {
					sb.append((char) Integer.parseInt(
							s.substring(i + 1, i + 3), 16));
					i += 2;
				} catch (NumberFormatException e) {
					// XXX
					// need to be more specific about illegal arg
					throw new IllegalArgumentException();
				} catch (StringIndexOutOfBoundsException e) {
					String rest = s.substring(i);
					sb.append(rest);
					if (rest.length() == 2)
						i++;
				}

				break;
			default:
				sb.append(c);
				break;
			}
		}
		return sb.toString();
	}
}
