package au.gov.digitalhealth.terminology.amtflatfile;

import java.util.ArrayList;

import org.openmbee.junit.model.JUnitFailure;
import org.openmbee.junit.model.JUnitTestCase;

public class JUnitTestCase_EXT extends JUnitTestCase{

	public JUnitTestCase_EXT addFailure(JUnitFailure failure) {
		if(this.getFailures() == null)
			this.setFailures(new ArrayList<JUnitFailure>());
		this.getFailures().add(failure);
		return this;
	}
	
	//Return proper class after setname
	@Override
	public JUnitTestCase_EXT setName(String name) {
		super.setName(name);
		return this;
	}
}
