/**
 * Licensed to The Apereo Foundation under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 *
 * The Apereo Foundation licenses this file to you under the Educational
 * Community License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License
 * at:
 *
 *   http://opensource.org/licenses/ecl2.txt
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *
 */

package org.opencastproject.adminui.endpoint;

import static com.entwinemedia.fn.data.json.Jsons.a;
import static com.entwinemedia.fn.data.json.Jsons.f;
import static com.entwinemedia.fn.data.json.Jsons.j;
import static com.entwinemedia.fn.data.json.Jsons.v;
import static com.entwinemedia.fn.data.json.Jsons.vN;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.apache.commons.lang.StringUtils.trimToNull;
import static org.apache.http.HttpStatus.SC_CONFLICT;
import static org.apache.http.HttpStatus.SC_INTERNAL_SERVER_ERROR;
import static org.opencastproject.index.service.util.RestUtils.okJsonList;
import static org.opencastproject.util.doc.rest.RestParameter.Type.STRING;

import org.opencastproject.adminui.impl.index.AdminUISearchIndex;
import org.opencastproject.index.service.api.IndexService;
import org.opencastproject.index.service.impl.index.group.Group;
import org.opencastproject.index.service.util.RestUtils;
import org.opencastproject.matterhorn.search.SearchIndexException;
import org.opencastproject.matterhorn.search.SearchResult;
import org.opencastproject.matterhorn.search.SearchResultItem;
import org.opencastproject.security.api.SecurityService;
import org.opencastproject.security.api.User;
import org.opencastproject.security.api.UserDirectoryService;
import org.opencastproject.util.NotFoundException;
import org.opencastproject.util.RestUtil;
import org.opencastproject.util.doc.rest.RestParameter;
import org.opencastproject.util.doc.rest.RestParameter.Type;
import org.opencastproject.util.doc.rest.RestQuery;
import org.opencastproject.util.doc.rest.RestResponse;
import org.opencastproject.util.doc.rest.RestService;

import com.entwinemedia.fn.data.Opt;
import com.entwinemedia.fn.data.json.JField;
import com.entwinemedia.fn.data.json.JValue;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("/")
@RestService(name = "groups", title = "Group service", notes = "This service offers the default groups CRUD Operations for the admin UI.", abstractText = "Provides operations for groups")
public class GroupsEndpoint {

  /** The logging facility */
  private static final Logger logger = LoggerFactory.getLogger(GroupsEndpoint.class);

  /** The admin UI search index */
  private AdminUISearchIndex searchIndex;

  /** The security service */
  private SecurityService securityService;

  /** The user directory service */
  private UserDirectoryService userDirectoryService;

  /** The index service */
  private IndexService indexService;

  /** OSGi callback for the security service. */
  public void setSecurityService(SecurityService securityService) {
    this.securityService = securityService;
  }

  /** OSGi callback for the index service. */
  public void setIndexService(IndexService indexService) {
    this.indexService = indexService;
  }

  /** OSGi callback for users services. */
  public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
    this.userDirectoryService = userDirectoryService;
  }

  /** OSGi callback for the search index. */
  public void setSearchIndex(AdminUISearchIndex searchIndex) {
    this.searchIndex = searchIndex;
  }

  /** OSGi callback. */
  protected void activate(ComponentContext cc) {
    logger.info("Activate the Admin ui - Groups facade endpoint");
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("groups.json")
  @RestQuery(name = "allgroupsasjson", description = "Returns a list of groups", returnDescription = "Returns a JSON representation of the list of groups available the current user's organization", restParameters = {
          @RestParameter(name = "filter", isRequired = false, description = "The filter used for the query. They should be formated like that: 'filter1:value1,filter2:value2'", type = STRING),
          @RestParameter(name = "sort", isRequired = false, description = "The sort order. May include any of the following: NAME, DESCRIPTION OR ROLE.  Add '_DESC' to reverse the sort order (e.g. NAME_DESC).", type = STRING),
          @RestParameter(defaultValue = "100", description = "The maximum number of items to return per page.", isRequired = false, name = "limit", type = RestParameter.Type.STRING),
          @RestParameter(defaultValue = "0", description = "The page number.", isRequired = false, name = "offset", type = RestParameter.Type.STRING) }, reponses = { @RestResponse(responseCode = SC_OK, description = "The groups.") })
  public Response getGroups(@QueryParam("filter") String filter, @QueryParam("sort") String sort,
          @QueryParam("offset") int offset, @QueryParam("limit") int limit) throws IOException {
    if (limit < 1)
      limit = 100;

    Opt<String> optSort = Opt.nul(trimToNull(sort));

    SearchResult<Group> results;
    try {
      results = indexService.getGroups(filter, Opt.some(limit), Opt.some(offset), optSort, searchIndex);
    } catch (SearchIndexException e) {
      logger.error("The External Search Index was not able to get the groups list: {}", ExceptionUtils.getStackTrace(e));
      return RestUtil.R.serverError();
    }

    List<JValue> groupsJSON = new ArrayList<JValue>();
    for (SearchResultItem<Group> item : results.getItems()) {
      Group group = item.getSource();
      List<JField> fields = new ArrayList<JField>();
      fields.add(f("id", v(group.getIdentifier())));
      fields.add(f("name", vN(group.getName())));
      fields.add(f("description", vN(group.getDescription())));
      fields.add(f("role", v(group.getRole())));
      fields.add(f("users", membersToJSON(group.getMembers())));
      groupsJSON.add(j(fields));
    }

    return okJsonList(groupsJSON, offset, limit, results.getHitCount());
  }

  @DELETE
  @Path("{id}")
  @RestQuery(name = "removegrouop", description = "Remove a group", returnDescription = "Return no content", pathParameters = { @RestParameter(name = "id", description = "The group identifier", isRequired = true, type = Type.STRING) }, reponses = {
          @RestResponse(responseCode = SC_OK, description = "Group deleted"),
          @RestResponse(responseCode = SC_NOT_FOUND, description = "Group not found."),
          @RestResponse(responseCode = SC_INTERNAL_SERVER_ERROR, description = "An internal server error occured.") })
  public Response removeGroup(@PathParam("id") String groupId) throws NotFoundException {
    return indexService.removeGroup(groupId);
  }

  @POST
  @Path("")
  @RestQuery(name = "createGroup", description = "Add a group", returnDescription = "Returns Created (201) if the group has been created", restParameters = {
          @RestParameter(name = "name", description = "The group name", isRequired = true, type = Type.STRING),
          @RestParameter(name = "description", description = "The group description", isRequired = false, type = Type.STRING),
          @RestParameter(name = "roles", description = "A comma seperated string of additional group roles", isRequired = false, type = Type.TEXT),
          @RestParameter(name = "users", description = "A comma seperated string of group members", isRequired = false, type = Type.TEXT) }, reponses = {
          @RestResponse(responseCode = SC_CREATED, description = "Group created"),
          @RestResponse(responseCode = SC_BAD_REQUEST, description = "Name too long"),
          @RestResponse(responseCode = SC_CONFLICT, description = "An group with this name already exists.") })
  public Response createGroup(@FormParam("name") String name, @FormParam("description") String description,
          @FormParam("roles") String roles, @FormParam("users") String users) {
    return indexService.createGroup(name, description, roles, users);
  }

  @PUT
  @Path("{id}")
  @RestQuery(name = "updateGroup", description = "Update a group", returnDescription = "Return the status codes", pathParameters = { @RestParameter(name = "id", description = "The group identifier", isRequired = true, type = Type.STRING) }, restParameters = {
          @RestParameter(name = "name", description = "The group name", isRequired = true, type = Type.STRING),
          @RestParameter(name = "description", description = "The group description", isRequired = false, type = Type.STRING),
          @RestParameter(name = "roles", description = "A comma seperated string of additional group roles", isRequired = false, type = Type.TEXT),
          @RestParameter(name = "users", description = "A comma seperated string of group members", isRequired = false, type = Type.TEXT) }, reponses = {
          @RestResponse(responseCode = SC_OK, description = "Group updated"),
          @RestResponse(responseCode = SC_NOT_FOUND, description = "Group not found"),
          @RestResponse(responseCode = SC_BAD_REQUEST, description = "Name too long") })
  public Response updateGroup(@PathParam("id") String groupId, @FormParam("name") String name,
          @FormParam("description") String description, @FormParam("roles") String roles,
          @FormParam("users") String users) throws NotFoundException {
    return indexService.updateGroup(groupId, name, description, roles, users);
  }

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @Path("{id}")
  @RestQuery(name = "getGroup", description = "Get a single group", returnDescription = "Return the status codes", pathParameters = { @RestParameter(name = "id", description = "The group identifier", isRequired = true, type = Type.STRING) }, reponses = {
          @RestResponse(responseCode = SC_OK, description = "Group updated"),
          @RestResponse(responseCode = SC_NOT_FOUND, description = "Group not found"),
          @RestResponse(responseCode = SC_BAD_REQUEST, description = "Name too long") })
  public Response getGroup(@PathParam("id") String groupId) throws NotFoundException, SearchIndexException {
    Opt<Group> groupOpt = indexService.getGroup(groupId, searchIndex);
    if (groupOpt.isNone())
      throw new NotFoundException("Group " + groupId + " does not exist.");

    Group group = groupOpt.get();
    return RestUtils.okJson(j(f("id", v(group.getIdentifier())), f("name", vN(group.getName())),
            f("description", vN(group.getDescription())), f("role", vN(group.getRole())),
            f("roles", rolesToJSON(group.getRoles())), f("users", membersToJSON(group.getMembers()))));
  }

  /**
   * Generate a JSON array based on the given set of roles
   *
   * @param roles
   *          the roles source
   * @return a JSON array ({@link JValue}) with the given roles
   */
  private JValue rolesToJSON(Set<String> roles) {
    List<JValue> rolesJSON = new ArrayList<JValue>();

    for (String role : roles) {
      rolesJSON.add(v(role));
    }
    return a(rolesJSON);
  }

  /**
   * Generate a JSON array based on the given set of members
   *
   * @param members
   *          the members source
   * @return a JSON array ({@link JValue}) with the given members
   */
  private JValue membersToJSON(Set<String> members) {
    List<JValue> membersJSON = new ArrayList<JValue>();

    for (String username : members) {
      User user = userDirectoryService.loadUser(username);
      String name = username;

      if (user != null && StringUtils.isNotBlank(user.getName())) {
        name = user.getName();
      }

      membersJSON.add(j(f("username", v(username)), f("name", v(name))));
    }

    return a(membersJSON);
  }
}
