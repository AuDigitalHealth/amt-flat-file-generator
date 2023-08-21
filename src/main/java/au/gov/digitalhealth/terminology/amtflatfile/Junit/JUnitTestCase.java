package au.gov.digitalhealth.terminology.amtflatfile.Junit;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Accessors(chain = true)
public class JUnitTestCase {

    @XmlAttribute(name = "classname", required = true)
    private String className;

    @XmlAttribute(required = true)
    private String name;

    @XmlAttribute
    private Double time;

    @XmlElement(name = "failure")
    private List<JUnitFailure> failures;

	public JUnitTestCase addFailure(JUnitFailure failure) {
		if(this.getFailures() == null)
			this.setFailures(new ArrayList<JUnitFailure>());
		this.getFailures().add(failure);
		return this;
	}
	
    @Override
    public String toString() {
        return ReflectionToStringBuilder.toStringExclude(this);
    }
}