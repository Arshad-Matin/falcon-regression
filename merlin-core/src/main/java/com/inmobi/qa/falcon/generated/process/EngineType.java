//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.05.29 at 03:46:24 PM GMT+05:30 
//


package com.inmobi.qa.falcon.generated.process;

import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlEnumValue;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for engine-type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * <p>
 * <pre>
 * &lt;simpleType name="engine-type">
 *   &lt;restriction base="{http://www.w3.org/2001/XMLSchema}string">
 *     &lt;enumeration value="oozie"/>
 *   &lt;/restriction>
 * &lt;/simpleType>
 * </pre>
 * 
 */
@XmlType(name = "engine-type")
@XmlEnum
public enum EngineType {

    @XmlEnumValue("oozie")
    OOZIE("oozie"),
    @XmlEnumValue("pig") PIG("pig");

    private final String value;

    EngineType(String v) {
        value = v;
    }

    public String value() {
        return value;
    }

    public static EngineType fromValue(String v) {
        for (EngineType c: EngineType.values()) {
            if (c.value.equals(v)) {
                return c;
            }
        }
        throw new IllegalArgumentException(v);
    }

}
