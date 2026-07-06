package au.gov.digitalhealth.terminology.amtflatfile;


import java.util.HashMap;

public enum AmtConcept {
    // @formatter:off
    MP(763158003L, "medicinal product"),
    MPUU(763158003L, "medicinal product unit of use"),
    MPP(781405001L, "medicinal product package"),
    TP(774167006L, "product name"),
    TPUU(763158003L, "trade product unit of use"),
    TPP(781405001L, "trade product pack"),
    CTPP(781405001L, "containerized trade product pack"),
    SUBSTANCE(105590001L, "substance"),
    REPLACED_BY(900000000000526001L, "REPLACED BY"),
    POSSIBLY_EQUIVALENT_TO(900000000000523009L, "POSSIBLY EQUIVALENT TO"),
    SAME_AS(900000000000527005L, "SAME AS");
    // @formatter:on

    private static HashMap<Long, AmtConcept> instanceMap = new HashMap<>();

    static {
        for (AmtConcept instance : AmtConcept.values()) {
            instanceMap.put(instance.id, instance);
        }
    }

    public static AmtConcept fromId(long id) throws Exception {
        if (instanceMap.containsKey(id)) {
            return instanceMap.get(id);
        } else {
            throw new Exception("Cannot find enum for id " + id);
        }
    }

    private long id;
    private String display;

    private AmtConcept(long id, String display) {
        this.id = id;
        this.display = display;
    }

    public long getId() {
        return id;
    }

    public String getIdString() {
        return Long.toString(id);
    }

    public static AmtConcept fromIdString(String idString) throws NumberFormatException, Exception {
        return fromId(Long.parseLong(idString));
    }

    public static boolean isEnumValue(String idString) {
        return instanceMap.containsKey(Long.parseLong(idString));
    }

    public static boolean isEnumValue(long id) {
        return instanceMap.containsKey(id);
    }

    public String getDisplay() {
        return display;
    }
    
}
