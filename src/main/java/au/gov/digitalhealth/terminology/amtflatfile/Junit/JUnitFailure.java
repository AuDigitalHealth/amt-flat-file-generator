package au.gov.digitalhealth.terminology.amtflatfile.Junit;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlValue;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class JUnitFailure {

    @XmlAttribute
    private String message;

    @XmlAttribute
    private String type;
    
    @XmlValue
    private String value;

    @Override
    public String toString() {
        return ReflectionToStringBuilder.toString(this);
    }
}