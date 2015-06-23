package org.sakaiproject.citation.api;

/**
 * Created by nickwilson on 6/23/15.
 */
public class CitationCollectionOrder {

	private String collectionId;
	private int location;
	private String sectionType;
	private String value;

	public CitationCollectionOrder(String collectionId, int location, String sectionType, String value) {
		this.collectionId = collectionId;
		this.location = location;
		this.sectionType = sectionType;
		this.value = value;
	}

	public String getCollectionId() {
		return collectionId;
	}

	public void setCollectionId(String collectionId) {
		this.collectionId = collectionId;
	}

	public int getLocation() {
		return location;
	}

	public void setLocation(int location) {
		this.location = location;
	}

	public String getSectionType() {
		return sectionType;
	}

	public void setSectionType(String sectionType) {
		this.sectionType = sectionType;
	}

	public String getValue() {
		return value;
	}

	public void setValue(String value) {
		this.value = value;
	}
}
