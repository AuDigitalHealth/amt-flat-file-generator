package au.gov.digitalhealth.terminology.amtflatfile;

public enum AmtRefset {
    // @formatter:off
    CTPP(929360051000036108L),
    TPP(929360041000036105L),
    TPUU(929360031000036100L),
    TP(929360021000036102L),
    MPP(929360081000036101L),
    MPUU(929360071000036103L),
    MP(929360061000036106L);
    // @formatter:on    

    private long id;

    private AmtRefset(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public String getIdString() {
        return Long.toString(id);
    }

}

