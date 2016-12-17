/*
 * -----------------------------------------------------------------------\
 * Lumeer
 *  
 * Copyright (C) 2016 - 2017 the original author or authors.
 *  
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -----------------------------------------------------------------------/
 */
package io.lumeer.engine.controller;

import io.lumeer.engine.api.LumeerConst;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.engine.api.data.DataStorage;
import io.lumeer.engine.api.exception.UnauthorizedAccessException;
import io.lumeer.engine.api.exception.UnsuccessfulOperationException;
import io.lumeer.engine.api.exception.ViewAlreadyExistsException;
import io.lumeer.engine.api.exception.ViewMetadataNotFoundException;
import io.lumeer.engine.util.ErrorMessageBuilder;
import io.lumeer.engine.util.Utils;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;

/**
 * @author <a href="alica.kacengova@gmail.com">Alica Kačengová</a>
 */
@SessionScoped
public class ViewFacade implements Serializable {

   @Inject
   private DataStorage dataStorage;

   @Inject
   private SequenceFacade sequenceFacade;

   @Inject
   private UserFacade userFacade;

   @Inject
   private SecurityFacade securityFacade;

   /**
    * Creates initial metadata for the view
    *
    * @param originalViewName
    *       name given by user
    * @return view id
    * @throws ViewAlreadyExistsException
    *       when view with given name already exists
    */
   public int createView(String originalViewName) throws ViewAlreadyExistsException {
      if (checkIfViewNameExists(originalViewName)) {
         throw new ViewAlreadyExistsException(ErrorMessageBuilder.viewUsernameAlreadyExistsString(originalViewName));
      }

      Map<String, Object> metadata = new HashMap<>();

      metadata.put(LumeerConst.View.VIEW_NAME_KEY, originalViewName);

      int viewId = sequenceFacade.getNext(LumeerConst.View.VIEW_SEQUENCE_NAME); // generates id
      metadata.put(LumeerConst.View.VIEW_ID_KEY, viewId);

      String createUser = getCurrentUser();
      metadata.put(LumeerConst.View.VIEW_CREATE_USER_KEY, createUser);

      String date = Utils.getCurrentTimeString();
      metadata.put(LumeerConst.View.VIEW_CREATE_DATE_KEY, date);

      metadata.put(LumeerConst.View.VIEW_TYPE_KEY, LumeerConst.View.VIEW_TYPE_DEFAULT_VALUE); // sets view type to default

      DataDocument metadataDocument = new DataDocument(metadata);

      // create user has complete access
      securityFacade.setRightsRead(metadataDocument, createUser);
      securityFacade.setRightsWrite(metadataDocument, createUser);
      securityFacade.setRightsExecute(metadataDocument, createUser);

      dataStorage.createDocument(LumeerConst.View.VIEW_METADATA_COLLECTION_NAME, metadataDocument);

      return viewId;
   }

   /**
    * Creates a copy of the view. User must have read right to original view, and will have read, write and execute rights on the copy.
    *
    * @param viewId
    *       id of view to be copied
    * @param newName
    *       name of the copy of the view
    * @return id of view copy
    * @throws ViewMetadataNotFoundException
    *       when metadata about copied view was not found
    * @throws ViewAlreadyExistsException
    *       when view with given name already exists
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read copied view
    */
   public int copyView(int viewId, String newName) throws ViewMetadataNotFoundException, ViewAlreadyExistsException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);
      int viewCopyId = createView(newName); // we create new initial metadata for view copy
      DataDocument viewCopy = getViewMetadata(viewCopyId);

      List<String> nonChangingKeys = Arrays.asList(
            LumeerConst.View.VIEW_NAME_KEY,
            LumeerConst.View.VIEW_ID_KEY,
            LumeerConst.View.VIEW_CREATE_USER_KEY,
            LumeerConst.View.VIEW_CREATE_DATE_KEY,
            LumeerConst.View.VIEW_USER_RIGHTS_KEY);

      for (String key : viewDocument.keySet()) { // we copy all attributes of original view document except those which are listed in nonChangingKeys
         if (nonChangingKeys.contains(key)) {
            continue;
         }

         viewCopy.put(key, viewDocument.get(key));
      }
      return viewCopyId;
   }

   /**
    * @param viewId
    *       view id
    * @return type of the given view
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public String getViewType(int viewId) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      return (String) (getViewMetadataValue(viewId, LumeerConst.View.VIEW_TYPE_KEY));
   }

   /**
    * @param viewId
    *       view id
    * @param type
    *       view type
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to write to the view
    */
   public void setViewType(int viewId, String type) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);
      setViewMetadataValueWithoutChecks(viewDocument, LumeerConst.View.VIEW_TYPE_KEY, type);
      // TODO verify if the type can be changed - maybe it can be changed only from default?
   }

   /**
    * @param viewId
    *       view id
    * @return view name
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public String getViewName(int viewId) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      return (String) (getViewMetadataValue(viewId, LumeerConst.View.VIEW_NAME_KEY));
   }

   /**
    * @param viewId
    *       view id
    * @param name
    *       new view name
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to write to the view
    * @throws ViewAlreadyExistsException
    *       when view with given name already exists
    */
   public void setViewName(int viewId, String name) throws ViewMetadataNotFoundException, UnauthorizedAccessException, ViewAlreadyExistsException {
      if (checkIfViewNameExists(name)) {
         throw new ViewAlreadyExistsException(ErrorMessageBuilder.viewUsernameAlreadyExistsString(name));
      }

      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);
      setViewMetadataValueWithoutChecks(viewDocument, LumeerConst.View.VIEW_NAME_KEY, name);
   }

   /**
    * @param viewId
    *       view id
    * @return view configuration
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public DataDocument getViewConfiguration(int viewId) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      return (DataDocument) (getViewMetadataValue(viewId, LumeerConst.View.VIEW_CONFIGURATION_KEY));
   }

   /**
    * @param viewId
    *       view id
    * @param configuration
    *       view configuration
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to write to the view
    */
   public void setViewConfiguration(int viewId, DataDocument configuration) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);
      setViewMetadataValueWithoutChecks(viewDocument, LumeerConst.View.VIEW_CONFIGURATION_KEY, configuration);
   }

   /**
    * @param viewId
    *       view id
    * @param attributeName
    *       view configuration attribute name
    * @return value of view configuration attribute
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public Object getViewConfigurationAttribute(int viewId, String attributeName) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      return getViewMetadataValue(viewId, LumeerConst.View.VIEW_CONFIGURATION_KEY + "." + attributeName);
   }

   /**
    * @param viewId
    *       view id
    * @param attributeName
    *       view configuration attribute name
    * @param attributeValue
    *       value of view configuration attribute
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to write to the view
    */
   public void setViewConfigurationAttribute(int viewId, String attributeName, Object attributeValue) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);
      setViewMetadataValueWithoutChecks(viewDocument, LumeerConst.View.VIEW_CONFIGURATION_KEY + "." + attributeName, attributeValue);
   }

   /**
    * @param viewId
    *       view id
    * @return DataDocument with all metadata about given view
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public DataDocument getViewMetadata(int viewId) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);

      if (!securityFacade.checkForRead(viewDocument, getCurrentUser())) {
         throw new UnauthorizedAccessException();
      }

      return viewDocument;
   }

   /**
    * @param viewId
    *       view id
    * @param metaKey
    *       key of value we want to get
    * @return specific value from view metadata
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to read the view
    */
   public Object getViewMetadataValue(int viewId, String metaKey) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      Object value = getViewMetadata(viewId).get(metaKey); // access rights are checked in getViewMetadata
      if (value == null) {
         throw new ViewMetadataNotFoundException(ErrorMessageBuilder.viewMetadataValueNotFoundString(viewId, metaKey));
      }
      return value;
   }

   /**
    * Sets view metadata value. If the given key does not exist, it is created. Otherwise it is just updated
    *
    * @param viewId
    *       view id
    * @param metaKey
    *       key of value we want to set
    * @param value
    *       value we want to set
    * @throws ViewMetadataNotFoundException
    *       when view metadata was not found
    * @throws UnsuccessfulOperationException
    *       when metadata cannot be set
    * @throws UnauthorizedAccessException
    *       when current user is not allowed to write to the view
    */
   public void setViewMetadataValue(int viewId, String metaKey, Object value) throws ViewMetadataNotFoundException, UnsuccessfulOperationException, UnauthorizedAccessException {
      DataDocument viewDocument = getViewMetadataWithoutAccessCheck(viewId);

      String user = getCurrentUser();
      if (!securityFacade.checkForWrite(viewDocument, user)) {
         throw new UnauthorizedAccessException();
      }

      if (LumeerConst.View.VIEW_IMMUTABLE_KEYS.contains(metaKey)) { // we check whether the meta key is not between fields that cannot be changed
         throw new UnsuccessfulOperationException(ErrorMessageBuilder.viewMetaImmutableString(viewId, metaKey));
      }

      if (LumeerConst.View.VIEW_SPECIAL_KEYS.contains(metaKey)) { // we check whether the meta key is not between fields that can be changed only through special methods
         throw new UnsuccessfulOperationException(ErrorMessageBuilder.viewMetaSpecialString(viewId, metaKey));
      }

      setViewMetadataValueWithoutChecks(viewDocument, metaKey, value);
   }

   // returns MongoDb query for getting metadata for one given view
   private String queryOneViewMetadata(int viewId) {
      StringBuilder sb = new StringBuilder("{find:\"")
            .append(LumeerConst.View.VIEW_METADATA_COLLECTION_NAME)
            .append("\",filter:{\"")
            .append(LumeerConst.View.VIEW_ID_KEY)
            .append("\":\"")
            .append(viewId)
            .append("\"}}");
      String viewMetaQuery = sb.toString();
      return viewMetaQuery;
   }

   // gets info about all views
   private List<DataDocument> getViewsMetadata() {
      return dataStorage.search(LumeerConst.View.VIEW_METADATA_COLLECTION_NAME, null, null, 0, 0);
   }

   private boolean checkIfViewNameExists(String originalViewName) {
      for (DataDocument v : getViewsMetadata()) {
         if (v.get(LumeerConst.View.VIEW_NAME_KEY).toString().equals(originalViewName)) {
            return true;
         }
      }
      return false;
   }

   // gets info about one view without checking access rights
   private DataDocument getViewMetadataWithoutAccessCheck(int viewId) throws ViewMetadataNotFoundException {
      List<DataDocument> viewList = dataStorage.run(queryOneViewMetadata(viewId));
      if (viewList.isEmpty()) {
         throw new ViewMetadataNotFoundException(ErrorMessageBuilder.viewMetadataNotFoundString(viewId));
      }

      return viewList.get(0);
   }

   // sets info about one view without checking special metadata keys
   private void setViewMetadataValueWithoutChecks(DataDocument viewDocument, String metaKey, Object value) throws ViewMetadataNotFoundException, UnauthorizedAccessException {
      if (!securityFacade.checkForWrite(viewDocument, getCurrentUser())) {
         throw new UnauthorizedAccessException();
      }

      String id = viewDocument.getId();
      Map<String, Object> metadataMap = new HashMap<>();
      metadataMap.put(metaKey, value);

      // with every change, we change update user and date
      metadataMap.put(LumeerConst.View.VIEW_UPDATE_USER_KEY, getCurrentUser());
      String date = Utils.getCurrentTimeString();
      metadataMap.put(LumeerConst.View.VIEW_UPDATE_DATE_KEY, date);

      dataStorage.updateDocument(LumeerConst.View.VIEW_METADATA_COLLECTION_NAME, new DataDocument(metadataMap), id, -1);
   }

   private String getCurrentUser() {
      return userFacade.getUserEmail();
   }
}
