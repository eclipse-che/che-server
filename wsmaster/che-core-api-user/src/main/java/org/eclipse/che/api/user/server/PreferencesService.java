/*
 * Copyright (c) 2012-2021 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package org.eclipse.che.api.user.server;

import io.swagger.annotations.ApiOperation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.annotations.ApiResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import org.eclipse.che.api.core.BadRequestException;
import org.eclipse.che.api.core.ServerException;
import org.eclipse.che.api.core.rest.Service;
import org.eclipse.che.api.core.rest.annotations.GenerateLink;
import org.eclipse.che.commons.env.EnvironmentContext;

import javax.inject.Inject;
import java.util.List;
import java.util.Map;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.che.api.user.server.Constants.LINK_REL_PREFERENCES;

/**
 * Preferences REST API.
 *
 * @author Yevhenii Voevodin
 */
@Path("/preferences")
@Tag(name = "preferences", description = "Preferences REST API")
public class PreferencesService extends Service {

    @Inject
    private PreferenceManager preferenceManager;

    @GET
    @Produces(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_PREFERENCES)
    @Operation(
            summary = "Gets preferences of logged in user. If not all the preferences needed then 'filter' may be used, " +
                    "basically it is regex for filtering preferences by names.",

            responses = {
                    @ApiResponse(responseCode = "200", description =  "Preferences successfully fetched"),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error")
            })
    public Map<String, String> find(
            @Parameter(description = 
                    "Regex for filtering preferences by names, e.g. '.*github.*' "
                            + "will return all the preferences which name contains github")
            @QueryParam("filter")
                    String filter)
            throws ServerException {
        if (filter == null) {
            return preferenceManager.find(userId());
        }
        return preferenceManager.find(userId(), filter);
    }

    @POST
    @Consumes(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_PREFERENCES)
    @ApiOperation(
            value = "Saves preferences of logged in user",
            notes = "All the existing user's preferences will be override by this method")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Preferences successfully saved"),
            @ApiResponse(code = 400, message = "Request doesn't contain new preferences"),
            @ApiResponse(code = 500, message = "Couldn't save preferences due to internal server error")
    })
    public void save(Map<String, String> preferences) throws BadRequestException, ServerException {
        if (preferences == null) {
            throw new BadRequestException("Required non-null new preferences");
        }
        preferenceManager.save(userId(), preferences);
    }

    @PUT
    @Consumes(APPLICATION_JSON)
    @Produces(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_PREFERENCES)
    @ApiOperation(
            value = "Updates preferences of logged in user",
            notes =
                    "The merge strategy is used for update, which means that "
                            + "existing preferences with keys equal to update preference keys will "
                            + "be replaces with new values, and new preferences will be added")
    @ApiResponses({
            @ApiResponse(
                    code = 200,
                    message =
                            "Preferences successfully updated, response contains " + "all the user preferences"),
            @ApiResponse(code = 400, message = "Request doesn't contain preferences update"),
            @ApiResponse(code = 500, message = "Couldn't update preferences due to internal server error")
    })
    public Map<String, String> update(Map<String, String> preferences)
            throws ServerException, BadRequestException {
        if (preferences == null) {
            throw new BadRequestException("Required non-null preferences update");
        }
        return preferenceManager.update(userId(), preferences);
    }

    @DELETE
    @Consumes(APPLICATION_JSON)
    @GenerateLink(rel = LINK_REL_PREFERENCES)
    @ApiOperation(
            value = "Remove preferences of logged in user.",
            notes =
                    "If names are not specified, then all the user's preferences will be removed, "
                            + "otherwise only the preferences which names are listed")
    @ApiResponses({
            @ApiResponse(code = 204, message = "Preferences successfully removed"),
            @ApiResponse(code = 500, message = "Couldn't remove preferences due to internal server error")
    })
    public void removePreferences(@Parameter(description ="Preferences to remove") List<String> names)
            throws ServerException {
        if (names == null || names.isEmpty()) {
            preferenceManager.remove(userId());
        } else {
            preferenceManager.remove(userId(), names);
        }
    }

    private static String userId() {
        return EnvironmentContext.getCurrent().getSubject().getUserId();
    }
}
