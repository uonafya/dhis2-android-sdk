package org.hisp.dhis2.android.sdk.persistence.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.Table;

import java.util.Map;

/**
 * @author Simen Skogly Russnes on 29.04.15.
 */
@Table
public class ProgramRuleVariable extends BaseIdentifiableObject {

    @Column
    public String sourceType;

    @Column
    public boolean externalAccess;

    @Column
    public String displayName;

    @Column
    public String program;

    @JsonProperty("program")
    public void setProgram(Map<String, Object> program) {
        this.program = (String) program.get("id");
    }

    @Column
    public String dataElement;

    @JsonProperty("dataElement")
    public void setDataElement(Map<String, Object> dataElement) {
        this.dataElement = (String) dataElement.get("id");
    }

}