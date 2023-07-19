package au.gov.digitalhealth.terminology.amtflatfile;

public class Replacement {
    Concept inactiveConcept;
    Concept activeConcept;
    Concept replacementType;
    String version;

    public Replacement(Concept inactiveConcept, Concept replacementType, Concept activeConcept, String version) {
        this.inactiveConcept = inactiveConcept;
        this.activeConcept = activeConcept;
        this.replacementType = replacementType;
        this.version = version;
    }

    public Concept getInactiveConcept() {
        return inactiveConcept;
    }

    public Concept getActiveConcept() {
        return activeConcept;
    }

    public Concept getReplacementType() {
        return replacementType;
    }

    public String getVersion() {
        return version;
    }
}
