/*
 * *** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at https://github.com/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * J4Care.
 * Portions created by the Initial Developer are Copyright (C) 2019
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * See @authors listed below
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * *** END LICENSE BLOCK *****
 */

package org.dcm4chee.arc.ian.rs;

import org.dcm4che3.conf.api.ConfigurationException;
import org.dcm4che3.conf.api.IApplicationEntityCache;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.IDWithIssuer;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.net.ApplicationEntity;
import org.dcm4che3.net.Device;
import org.dcm4che3.net.service.QueryRetrieveLevel2;
import org.dcm4chee.arc.conf.ArchiveDeviceExtension;
import org.dcm4chee.arc.entity.ExpirationState;
import org.dcm4chee.arc.entity.Patient;
import org.dcm4chee.arc.ian.scu.IANScheduler;
import org.dcm4chee.arc.qmgt.QueueSizeLimitExceededException;
import org.dcm4chee.arc.query.Query;
import org.dcm4chee.arc.query.QueryContext;
import org.dcm4chee.arc.query.QueryService;
import org.dcm4chee.arc.query.util.QueryAttributes;
import org.dcm4chee.arc.validation.constraints.InvokeValidate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Pattern;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * @author Vrinda Nayak <vrinda.nayak@j4care.com>
 * @since Mar 2019
 */
@RequestScoped
@Path("aets/{aet}/ian/{externalAET}")
@InvokeValidate(type = IANSCUMatchingRS.class)
public class IANSCUMatchingRS {
    private static final Logger LOG = LoggerFactory.getLogger(IANSCUMatchingRS.class);

    @Context
    private UriInfo uriInfo;

    @Inject
    private Device device;

    @Inject
    private IANScheduler ianScheduler;

    @Inject
    private IApplicationEntityCache aeCache;

    @Inject
    private QueryService queryService;

    @PathParam("aet")
    private String aet;

    @PathParam("externalAET")
    private String externalAET;

    @Context
    private HttpServletRequest request;

    @QueryParam("fuzzymatching")
    @Pattern(regexp = "true|false")
    private String fuzzymatching;

    @QueryParam("incomplete")
    @Pattern(regexp = "true|false")
    private String incomplete;

    @QueryParam("retrievefailed")
    @Pattern(regexp = "true|false")
    private String retrievefailed;

    @QueryParam("storageVerificationFailed")
    @Pattern(regexp = "true|false")
    private String storageVerificationFailed;

    @QueryParam("compressionfailed")
    @Pattern(regexp = "true|false")
    private String compressionfailed;

    @QueryParam("ExternalRetrieveAET")
    private String externalRetrieveAET;

    @QueryParam("ExternalRetrieveAET!")
    private String externalRetrieveAETNot;

    @QueryParam("patientVerificationStatus")
    @Pattern(regexp = "UNVERIFIED|VERIFIED|NOT_FOUND|VERIFICATION_FAILED")
    private String patientVerificationStatus;

    @QueryParam("ExpirationDate")
    private String expirationDate;

    @QueryParam("storageID")
    private String storageID;

    @QueryParam("storageClustered")
    @Pattern(regexp = "true|false")
    private String storageClustered;

    @QueryParam("storageExported")
    @Pattern(regexp = "true|false")
    private String storageExported;

    @QueryParam("allOfModalitiesInStudy")
    @Pattern(regexp = "true|false")
    private String allOfModalitiesInStudy;

    @QueryParam("StudySizeInKB")
    @Pattern(regexp = "\\d{1,9}(-\\d{0,9})?|-\\d{1,9}")
    private String studySizeInKB;

    @QueryParam("ExpirationState")
    @Pattern(regexp = "UPDATEABLE|FROZEN|REJECTED|EXPORT_SCHEDULED|FAILED_TO_EXPORT|FAILED_TO_REJECT")
    private String expirationState;

    @QueryParam("batchID")
    private String batchID;

    public void validate() {
        new QueryAttributes(uriInfo, null);
    }

    @POST
    @Path("/studies")
    @Produces("application/json")
    public Response matchingStudyIAN() {
        return ianMatching("matchingStudyIAN",
                QueryRetrieveLevel2.STUDY, null, null);
    }

    @POST
    @Path("/series")
    @Produces("application/json")
    public Response matchingSeriesIAN() {
        return ianMatching("matchingSeriesIAN",
                QueryRetrieveLevel2.SERIES, null, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series")
    @Produces("application/json")
    public Response matchingSeriesOfStudyIAN(
            @PathParam("StudyInstanceUID") String studyUID) {
        return ianMatching("matchingSeriesOfStudyIAN",
                QueryRetrieveLevel2.SERIES, studyUID, null);
    }

    @POST
    @Path("/instances")
    @Produces("application/json")
    public Response matchingInstancesIAN() {
        return ianMatching("matchingInstancesIAN",
                QueryRetrieveLevel2.IMAGE, null, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/instances")
    @Produces("application/json")
    public Response matchingInstancesOfStudyIAN(
            @PathParam("StudyInstanceUID") String studyUID) {
        return ianMatching("matchingInstancesOfStudyIAN",
                QueryRetrieveLevel2.IMAGE, studyUID, null);
    }

    @POST
    @Path("/studies/{StudyInstanceUID}/series/{SeriesInstanceUID}/instances")
    @Produces("application/json")
    public Response matchingInstancesOfSeriesIAN(
            @PathParam("StudyInstanceUID") String studyUID,
            @PathParam("SeriesInstanceUID") String seriesUID) {
        return ianMatching("matchingInstancesOfSeriesIAN",
                QueryRetrieveLevel2.IMAGE, studyUID, seriesUID);
    }

    private Response ianMatching(String method, QueryRetrieveLevel2 qrlevel, String studyUID, String seriesUID) {
        logRequest();
        ApplicationEntity ae = validateAE(aet, device.getApplicationEntity(aet, true));

        try {
            validateAE(externalAET, aeCache.get(externalAET));
        } catch (ConfigurationException e) {
            return errResponseAsTextPlain(e);
        }

        try {
            ArchiveDeviceExtension arcDev = device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class);
            QueryContext ctx = queryContext(method, qrlevel, studyUID, seriesUID, ae);
            String warning = null;
            int count = 0;
            Response.Status status = Response.Status.ACCEPTED;
            try (Query query = queryService.createQuery(ctx)) {
                query.beginTransaction();
                try {
                    query.executeQuery(arcDev.getQueryFetchSize());
                    while (query.hasMoreMatches()) {
                        Attributes match = query.nextMatch();
                        ianScheduler.scheduleIAN(ae, externalAET,
                                match.getString(Tag.StudyInstanceUID),
                                match.getString(Tag.SeriesInstanceUID));
                        count++;
                    }
                } catch (QueueSizeLimitExceededException e) {
                    status = Response.Status.SERVICE_UNAVAILABLE;
                    warning = e.getMessage();
                } catch (Exception e) {
                    warning = e.getMessage();
                    status = Response.Status.INTERNAL_SERVER_ERROR;
                }
            }
            Response.ResponseBuilder builder = Response.status(status);
            if (warning != null)
                builder.header("Warning", warning);
            return builder.entity("{\"count\":" + count + '}').build();
        } catch (Exception e) {
            return errResponseAsTextPlain(e);
        }
    }

    private QueryContext queryContext(String method, QueryRetrieveLevel2 qrlevel, String studyUID,
                                      String seriesUID, ApplicationEntity ae) {
        QueryContext ctx = queryService.newQueryContextQIDO(request, method, ae, queryParam(ae));
        ctx.setQueryRetrieveLevel(qrlevel);
        QueryAttributes queryAttrs = new QueryAttributes(uriInfo, null);
        Attributes keys = queryAttrs.getQueryKeys();
        IDWithIssuer idWithIssuer = IDWithIssuer.pidOf(keys);
        if (idWithIssuer != null)
            ctx.setPatientIDs(idWithIssuer);
        if (studyUID != null)
            keys.setString(Tag.StudyInstanceUID, VR.UI, studyUID);
        if (seriesUID != null)
            keys.setString(Tag.SeriesInstanceUID, VR.UI, seriesUID);
        ctx.setQueryKeys(keys);
        Attributes returnKeys = new Attributes(3);
        returnKeys.setNull(Tag.StudyInstanceUID, VR.UI);
        switch (qrlevel) {
            case IMAGE:
                returnKeys.setNull(Tag.SOPInstanceUID, VR.UI);
            case SERIES:
                returnKeys.setNull(Tag.SeriesInstanceUID, VR.UI);
        }
        ctx.setReturnKeys(returnKeys);
        return ctx;
    }

    private org.dcm4chee.arc.query.util.QueryParam queryParam(ApplicationEntity ae) {
        org.dcm4chee.arc.query.util.QueryParam queryParam = new org.dcm4chee.arc.query.util.QueryParam(ae);
        queryParam.setCombinedDatetimeMatching(true);
        queryParam.setFuzzySemanticMatching(Boolean.parseBoolean(fuzzymatching));
        queryParam.setAllOfModalitiesInStudy(Boolean.parseBoolean(allOfModalitiesInStudy));
        queryParam.setIncomplete(Boolean.parseBoolean(incomplete));
        queryParam.setRetrieveFailed(Boolean.parseBoolean(retrievefailed));
        queryParam.setStorageVerificationFailed(Boolean.parseBoolean(storageVerificationFailed));
        queryParam.setCompressionFailed(Boolean.parseBoolean(compressionfailed));
        queryParam.setExternalRetrieveAET(externalRetrieveAET);
        queryParam.setExternalRetrieveAETNot(externalRetrieveAETNot);
        queryParam.setExpirationDate(expirationDate);
        queryParam.setStudySizeRange(studySizeInKB);
        if (patientVerificationStatus != null)
            queryParam.setPatientVerificationStatus(Patient.VerificationStatus.valueOf(patientVerificationStatus));
        if (storageID != null)
            queryParam.setStudyStorageIDs(device.getDeviceExtensionNotNull(ArchiveDeviceExtension.class)
                    .getStudyStorageIDs(storageID, parseBoolean(storageClustered), parseBoolean(storageExported)));
        if (expirationState != null)
            queryParam.setExpirationState(ExpirationState.valueOf(expirationState));
        return queryParam;
    }

    private static Boolean parseBoolean(String s) {
        return s != null ? Boolean.valueOf(s) : null;
    }

    private void logRequest() {
        LOG.info("Process POST {} from {}@{}", request.getRequestURI(), request.getRemoteUser(), request.getRemoteHost());
    }

    private ApplicationEntity validateAE(String aet, ApplicationEntity ae) {
        if (ae == null || !ae.isInstalled())
            throw new WebApplicationException(errResponse(
                    "No such Application Entity: " + aet,
                    Response.Status.NOT_FOUND));
        return ae;
    }

    private Response errResponseAsTextPlain(Exception e) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(exceptionAsString(e))
                .type("text/plain")
                .build();
    }

    private String exceptionAsString(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private Response errResponse(String errorMessage, Response.Status status) {
        return Response.status(status).entity("{\"errorMessage\":\"" + errorMessage + "\"}").build();
    }
}
