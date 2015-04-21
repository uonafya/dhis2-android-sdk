/*
 *  Copyright (c) 2015, University of Oslo
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  * Redistributions of source code must retain the above copyright notice, this
 *  * list of conditions and the following disclaimer.
 *  *
 *  * Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  * Neither the name of the HISP project nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 *  * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 *  * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 *  * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 *  * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 *  * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 *  * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 *  * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package org.hisp.dhis2.android.sdk.persistence.models;

import android.util.Log;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.raizlabs.android.dbflow.annotation.Column;
import com.raizlabs.android.dbflow.annotation.ForeignKeyAction;
import com.raizlabs.android.dbflow.annotation.Table;
import com.raizlabs.android.dbflow.runtime.DBTransactionInfo;
import com.raizlabs.android.dbflow.runtime.FlowContentObserver;
import com.raizlabs.android.dbflow.runtime.TransactionManager;
import com.raizlabs.android.dbflow.runtime.transaction.BaseTransaction;
import com.raizlabs.android.dbflow.sql.Queriable;
import com.raizlabs.android.dbflow.sql.builder.Condition;
import com.raizlabs.android.dbflow.sql.language.Select;
import com.raizlabs.android.dbflow.sql.language.Update;
import com.raizlabs.android.dbflow.structure.BaseModel;
import com.squareup.okhttp.internal.Util;

import org.hisp.dhis2.android.sdk.controllers.Dhis2;
import org.hisp.dhis2.android.sdk.controllers.datavalues.DataValueController;
import org.hisp.dhis2.android.sdk.controllers.metadata.MetaDataController;
import org.hisp.dhis2.android.sdk.utils.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * @author Simen Skogly Russnes on 23.02.15.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Table
public class Event extends BaseSerializableModel {

    private static final String CLASS_TAG = "Event";

    public static String STATUS_ACTIVE = "ACTIVE";

    public static String STATUS_COMPLETED = "COMPLETED";

    public static String STATUS_VISITED = "VISITED";

    public static String STATUS_FUTURE_VISIT = "SCHEDULE";

    public static String STATUS_LATE_VISIT = "OVERDUE";

    public static String STATUS_SKIPPED = "SKIPPED";

    @JsonAnySetter
    public void handleUnknown(String key, Object value) {}

    public Event(String organisationUnitId, String status, String programId, String programStageId,
                 String trackedEntityInstanceId, String enrollmentId) {
        this.event = Dhis2.QUEUED + UUID.randomUUID().toString();
        this.fromServer = false;
        this.dueDate = Utils.getCurrentDate();
        this.eventDate = Utils.getCurrentDate();
        this.organisationUnitId = organisationUnitId;
        this.programId = programId;
        this.programStageId = programStageId;
        this.status = status;
        this.lastUpdated = Utils.getCurrentDate();
        this.trackedEntityInstance = trackedEntityInstanceId;
        this.enrollment = enrollmentId;
        dataValues = new ArrayList<DataValue>();
    }

    public Event() {}

    /**
     * used to tell whether or not an event has been updated locally and needs to be sent to server.
     */
    @JsonIgnore
    @Column
    public boolean fromServer = true;

    @JsonIgnore
    @Column(columnType = Column.PRIMARY_KEY_AUTO_INCREMENT)
    public long localId;

    @JsonIgnore
    @Column(unique = true)
    public String event;

    @JsonProperty("event")
    public void setEvent(String event) {
        this.event = event;
    }

    /**
     * Should only be used by Jackson so that event is included only if its non-local generated
     * Use Event.event instead to access it.
     */
    @JsonProperty("event")
    public String getEvent() {
        if(Utils.isLocal(event))
        return null;
        else return event;
    }

    @JsonProperty("lastUpdated")
    @Column
    public String lastUpdated;

    @JsonProperty("created")
    @Column
    public String created;

    @JsonProperty("status")
    @Column
    public String status;

    @JsonProperty("coordinate")
    public void setCoordinate(Map<String, Object> coordinate) {
        this.latitude = (double) coordinate.get("latitude");
        this.longitude = (double) coordinate.get("longitude");
    }

    @JsonProperty("coordinate")
    public Map<String, Object> getCoordinate() {
        Map<String, Object> coordinate = new HashMap<>();
        coordinate.put("latitude", latitude);
        coordinate.put("longitude", longitude);
        return coordinate;
    }

    @JsonIgnore
    @Column
    public Double latitude;

    @JsonIgnore
    @Column
    public Double longitude;

    @JsonProperty("trackedEntityInstance")
    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    @Column
    public String trackedEntityInstance;

    @JsonIgnore
    @Column
    public long localEnrollmentId;

    @JsonProperty("enrollment")
    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    @Column
    public String enrollment;

    public String getEnrollment() {
        if(Utils.isLocal(enrollment))
            return null;
        else return enrollment;
    }

    @JsonProperty("program")
    @Column
    public String programId;

    @JsonProperty("programStage")
    @Column
    public String programStageId;

    @JsonProperty("orgUnit")
    @JsonSerialize(include=JsonSerialize.Inclusion.NON_NULL)
    @Column
    public String organisationUnitId;

    @JsonProperty("eventDate")
    @Column
    public String eventDate;

    @JsonProperty("dueDate")
    @Column
    public String dueDate;

    @JsonProperty("dataValues")
    public List<DataValue> dataValues;

    public List<DataValue> getDataValues() {
        if( dataValues == null) dataValues = Select.all(DataValue.class,
                Condition.column(DataValue$Table.LOCALEVENTID).is(localId));
        return dataValues;
    }

    @Override
    public void delete(boolean async) {
        if(dataValues != null) {
            for(DataValue dataValue: dataValues)
                dataValue.delete(async);
        }
        super.delete(async);
    }

    @Override
    public void save(boolean async) {
        /* check if there is an existing event with the same UID to avoid duplicates */
        Event existingEvent = DataValueController.getEventByUid(event);
        boolean exists = false;
        if(existingEvent != null) {
            exists = true;
            localId = existingEvent.localId;
        }
        if(getEvent() == null && DataValueController.getEvent(localId) != null) { //means that the event is local
            //then we don't want to update the event reference in fear of overwriting
            //an updated reference from server while the item has been loaded in memory
            //unfortunately a bit of hard coding I suppose but it's important to verify data integrity
            updateManually(async);
        } else {
            if(!exists) super.save(false); //ensuring a localId is created to give foreign key to datavalues
            else super.save(async);
        }
        if(dataValues!=null) {
            for(DataValue dataValue: dataValues)
            {
                dataValue.event = event;
                dataValue.localEventId = localId;
                dataValue.save(async);
            }
        }
    }

    /**
     * Updates manually without touching UIDs the fields that are modifiable by user.
     * This will and should only be called if the event has a locally created temp event reference
     * and has previously been saved, so that it has a localId.
     */
    private void updateManually(boolean async) {
        Queriable q = new Update().table(Event.class).set(
                Condition.column(Event$Table.LONGITUDE).is(longitude),
                Condition.column(Event$Table.LATITUDE).is(latitude),
                Condition.column(Event$Table.STATUS).is(status),
                Condition.column(Event$Table.FROMSERVER).is(fromServer))
                .where(Condition.column(Enrollment$Table.LOCALID).is(localId));
        if(async)
            TransactionManager.getInstance().transactQuery(DBTransactionInfo.create(BaseTransaction.PRIORITY_HIGH), q);
        else
            q.queryClose();
    }

    @Override
    public void update(boolean async) {
        save(async);
    }
}
