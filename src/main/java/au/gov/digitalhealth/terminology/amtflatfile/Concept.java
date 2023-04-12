package au.gov.digitalhealth.terminology.amtflatfile;


import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Concept {

    private long id;
    private String fullSpecifiedName;
    private String preferredTerm;
    private Set<Concept> units = new HashSet<>();
    private Set<Concept> subpack = new HashSet<>();
    private Map<Long, Concept> parents = new HashMap<>();
    private Map<Long, Concept> ancestors = new HashMap<>();
    private Set<Concept> tps = new HashSet<>();
    private Set<String> artgIds = new HashSet<>();
    private boolean active;
    private AmtConcept type;

    public Concept(long id, boolean active) {
        this.id = id;
        this.active = active;
    }

    public void addParent(Concept concept) {
        parents.put(concept.getId(), concept);
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFullSpecifiedName() {
        return fullSpecifiedName;
    }

    public void setFullSpecifiedName(String fullSpecifiedName) {
        this.fullSpecifiedName = fullSpecifiedName;
    }

    public String getPreferredTerm() {
        return preferredTerm;
    }

    public void setPreferredTerm(String preferredTerm) {
        this.preferredTerm = preferredTerm;
    }

    public void addUnit(Concept unit) {
        if (units == null) {
            units = new HashSet<>();
        }
        units.add(unit);
    }

    public Set<Concept> getUnits() {
        if (units.isEmpty()) {
            return getSubpack().stream().map(Concept::getUnits).flatMap(Set::stream).collect(Collectors.toSet());
        }
        return units;
    }

    public boolean hasOneMatchingParent(AmtConcept... amtConcept) {
        for (AmtConcept parent : amtConcept) {
            if (parents.containsKey(parent.getId())) {
                return true;
            }
        }
        return false;
    }

    public boolean hasParent(AmtConcept amtConcept) {
        return parents.containsKey(amtConcept.getId());
    }

    public boolean hasParent(Concept concept) {
        return parents.containsKey(concept.getId());
    }

	public Map<Long, Concept> getParents() {
		return parents;
	}

	public String toConceptReference() {
		return getId() + "|" + getPreferredTerm() + "|";
	}

    public void addAncestors(Map<Long, Concept> map) {
        ancestors.putAll(map);
    }

    public boolean hasAtLeastOneMatchingAncestor(AmtConcept... concepts) {
        for (AmtConcept amtConcept : concepts) {
            if (ancestors.containsKey(amtConcept.getId())) {
                return true;
            }
        }
        return false;
    }

    public Collection<Concept> getAncestors(AmtConcept concept) {
        Collection<Concept> result = new ArrayList<>();
        for (Concept ancestor : ancestors.values()) {
            if (ancestor.hasAtLeastOneMatchingAncestor(concept)) {
                result.add(ancestor);
            }
        }
        return result;
    }

    public Collection<Concept> getAncestors() {
        return ancestors.values();
    }

    @Override
    public String toString() {
        return "Concept [id=" + id + ", fullSpecifiedName=" + fullSpecifiedName + ", parents=" + parents + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (id ^ (id >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Concept other = (Concept) obj;
        if (id != other.id)
            return false;
        return true;
    }

    public void addTp(Concept concept) {
        if (tps == null) {
            tps = new HashSet<>();
        }
        tps.add(concept);
    }

    public Set<Concept> getTps() {
        return tps;
    }

    public void addArtgIds(String row) {
        if (artgIds == null) {
            artgIds = new HashSet<>();
        }
        artgIds.add(row);
    }

    public Set<String> getArtgIds() {
        return artgIds;
    }

    public boolean isActive() {
        return active;
    }

    public void addSubpack(Concept concept) {
        subpack.add(concept);
    }

    public Set<Concept> getSubpack() {
        return subpack;
    }

    public void setType(AmtConcept type) {
        this.type = type;
    }

    public AmtConcept getType() {
        return type;
    }
}
