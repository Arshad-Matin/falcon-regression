//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.11.21 at 10:47:07 AM GMT+05:30 
//


package com.inmobi.qa.falcon.generated.feed;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.inmobi.qa.falcon.generated.dependencies.Frequency;


/**
 * 
 *                 Feed references a cluster by it's name, before submitting a feed all the
 *                 referenced cluster should be submitted to Falcon.
 *                 type: specifies whether the
 *                 referenced cluster should be treated as a
 *                 source or target for a feed.
 *                 Validity of a feed on cluster specifies duration for which this feed is
 *                 valid on this cluster.
 *                 Retention specifies how long the feed is retained on this cluster and the
 *                 action to be taken on the feed after the expiry of retention period.
 *                 The retention limit is
 *                 specified by expression frequency(times), ex: if
 *                 feed should be retained for at least 6 hours then retention's limit="hours(6)".
 *                 The field partitionExp contains
 *                 partition tags. Number of partition tags has to be equal to number of partitions specified in feed
 *                 schema.
 *                 A partition tag can be a wildcard(*), a static string or
 *                 an expression. Atleast one of the strings has to be an expression.
 *             
 * 
 * <p>Java class for cluster complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="cluster">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;sequence>
 *         &lt;element name="validity" type="{uri:falcon:feed:0.1}validity"/>
 *         &lt;element name="retention" type="{uri:falcon:feed:0.1}retention"/>
 *         &lt;choice minOccurs="0">
 *           &lt;element name="locations" type="{uri:falcon:feed:0.1}locations" minOccurs="0"/>
 *           &lt;element name="table" type="{uri:falcon:feed:0.1}catalog-table"/>
 *         &lt;/choice>
 *       &lt;/sequence>
 *       &lt;attribute name="name" use="required" type="{uri:falcon:feed:0.1}IDENTIFIER" />
 *       &lt;attribute name="type" type="{uri:falcon:feed:0.1}cluster-type" />
 *       &lt;attribute name="partition" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="delay" type="{uri:falcon:feed:0.1}frequency-type" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "cluster", propOrder = {
    "validity",
    "retention",
    "locations",
    "table"
})
public class Cluster {

    @XmlElement(required = true)
    protected Validity validity;
    @XmlElement(required = true)
    protected Retention retention;
    protected Locations locations;
    protected CatalogTable table;
    @XmlAttribute(required = true)
    protected String name;
    @XmlAttribute
    protected ClusterType type;
    @XmlAttribute
    protected String partition;
    @XmlAttribute
    @XmlJavaTypeAdapter(Adapter2 .class)
    protected Frequency delay;

    /**
     * Gets the value of the validity property.
     * 
     * @return
     *     possible object is
     *     {@link Validity }
     *     
     */
    public Validity getValidity() {
        return validity;
    }

    /**
     * Sets the value of the validity property.
     * 
     * @param value
     *     allowed object is
     *     {@link Validity }
     *     
     */
    public void setValidity(Validity value) {
        this.validity = value;
    }

    /**
     * Gets the value of the retention property.
     * 
     * @return
     *     possible object is
     *     {@link Retention }
     *     
     */
    public Retention getRetention() {
        return retention;
    }

    /**
     * Sets the value of the retention property.
     * 
     * @param value
     *     allowed object is
     *     {@link Retention }
     *     
     */
    public void setRetention(Retention value) {
        this.retention = value;
    }

    /**
     * Gets the value of the locations property.
     * 
     * @return
     *     possible object is
     *     {@link Locations }
     *     
     */
    public Locations getLocations() {
        return locations;
    }

    /**
     * Sets the value of the locations property.
     * 
     * @param value
     *     allowed object is
     *     {@link Locations }
     *     
     */
    public void setLocations(Locations value) {
        this.locations = value;
    }

    /**
     * Gets the value of the table property.
     * 
     * @return
     *     possible object is
     *     {@link CatalogTable }
     *     
     */
    public CatalogTable getTable() {
        return table;
    }

    /**
     * Sets the value of the table property.
     * 
     * @param value
     *     allowed object is
     *     {@link CatalogTable }
     *     
     */
    public void setTable(CatalogTable value) {
        this.table = value;
    }

    /**
     * Gets the value of the name property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the value of the name property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setName(String value) {
        this.name = value;
    }

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link ClusterType }
     *     
     */
    public ClusterType getType() {
        return type;
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link ClusterType }
     *     
     */
    public void setType(ClusterType value) {
        this.type = value;
    }

    /**
     * Gets the value of the partition property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public String getPartition() {
        return partition;
    }

    /**
     * Sets the value of the partition property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setPartition(String value) {
        this.partition = value;
    }

    /**
     * Gets the value of the delay property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public Frequency getDelay() {
        return delay;
    }

    /**
     * Sets the value of the delay property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setDelay(Frequency value) {
        this.delay = value;
    }

}
