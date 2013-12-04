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
import javax.xml.bind.annotation.XmlType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import com.inmobi.qa.falcon.generated.dependencies.Frequency;


/**
 * <p>Java class for retention complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="retention">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute name="type" type="{uri:falcon:feed:0.1}retention-type" default="instance" />
 *       &lt;attribute name="limit" use="required" type="{uri:falcon:feed:0.1}frequency-type" />
 *       &lt;attribute name="action" use="required" type="{uri:falcon:feed:0.1}action-type" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "retention")
public class Retention {

    @XmlAttribute
    protected RetentionType type;
    @XmlAttribute(required = true)
    @XmlJavaTypeAdapter(Adapter2 .class)
    protected Frequency limit;
    @XmlAttribute(required = true)
    protected ActionType action;

    /**
     * Gets the value of the type property.
     * 
     * @return
     *     possible object is
     *     {@link RetentionType }
     *     
     */
    public RetentionType getType() {
        if (type == null) {
            return RetentionType.INSTANCE;
        } else {
            return type;
        }
    }

    /**
     * Sets the value of the type property.
     * 
     * @param value
     *     allowed object is
     *     {@link RetentionType }
     *     
     */
    public void setType(RetentionType value) {
        this.type = value;
    }

    /**
     * Gets the value of the limit property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    public Frequency getLimit() {
        return limit;
    }

    /**
     * Sets the value of the limit property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    public void setLimit(Frequency value) {
        this.limit = value;
    }

    /**
     * Gets the value of the action property.
     * 
     * @return
     *     possible object is
     *     {@link ActionType }
     *     
     */
    public ActionType getAction() {
        return action;
    }

    /**
     * Sets the value of the action property.
     * 
     * @param value
     *     allowed object is
     *     {@link ActionType }
     *     
     */
    public void setAction(ActionType value) {
        this.action = value;
    }

}
