package com.sdms.model;

public class SearchResult {
    public enum Module { ATHLETE, OFFICIAL, COACH, EQUIPMENT }

    private final Module module;
    private final String id;
    private final String name;
    private final String detail;
    private final int dbId;

    public SearchResult(Module module, int dbId, String id, String name, String detail) {
        this.module = module; this.dbId = dbId;
        this.id = id; this.name = name; this.detail = detail;
    }

    public Module getModule()  { return module; }
    public int    getDbId()    { return dbId; }
    public String getId()      { return id; }
    public String getName()    { return name; }
    public String getDetail()  { return detail; }
    public String getModuleLabel() {
        return switch (module) {
            case ATHLETE   -> "Athlete";
            case OFFICIAL  -> "Official";
            case COACH     -> "Coach";
            case EQUIPMENT -> "Equipment";
        };
    }
}
