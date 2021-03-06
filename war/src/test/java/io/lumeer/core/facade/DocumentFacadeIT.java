/*
 * Lumeer: Modern Data Definition and Processing Platform
 *
 * Copyright (C) since 2017 Lumeer.io, s.r.o. and/or its affiliates.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.lumeer.core.facade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.lumeer.api.model.Attribute;
import io.lumeer.api.model.Collection;
import io.lumeer.api.model.Document;
import io.lumeer.api.model.Organization;
import io.lumeer.api.model.Permission;
import io.lumeer.api.model.Permissions;
import io.lumeer.api.model.Project;
import io.lumeer.api.model.Role;
import io.lumeer.api.model.User;
import io.lumeer.core.WorkspaceKeeper;
import io.lumeer.core.auth.AuthenticatedUser;
import io.lumeer.engine.IntegrationTestBase;
import io.lumeer.engine.api.data.DataDocument;
import io.lumeer.storage.api.dao.CollectionDao;
import io.lumeer.storage.api.dao.DataDao;
import io.lumeer.storage.api.dao.DocumentDao;
import io.lumeer.storage.api.dao.OrganizationDao;
import io.lumeer.storage.api.dao.ProjectDao;
import io.lumeer.storage.api.dao.UserDao;
import io.lumeer.storage.api.exception.ResourceNotFoundException;

import org.assertj.core.api.SoftAssertions;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.inject.Inject;

@RunWith(Arquillian.class)
public class DocumentFacadeIT extends IntegrationTestBase {

   private static final String ORGANIZATION_CODE = "TORG";
   private static final String PROJECT_CODE = "TPROJ";

   private static final String COLLECTION_NAME = "Testing collection";
   private static final String COLLECTION_ICON = "fa-eye";
   private static final String COLLECTION_COLOR = "#00ee00";

   private static final String USER = AuthenticatedUser.DEFAULT_EMAIL;
   private User user;

   private static final String KEY1 = "A";
   private static final String KEY2 = "B";
   private static final String KEY3 = "C";
   private static final String KEY4 = "D";
   private static final String KEY5 = "E";
   private static final String VALUE1 = "firstValue";
   private static final String VALUE2 = "secondValue";
   private static final String VALUE3 = "34";
   private static final String VALUE4 = "34.5";
   private static final String VALUE5 = "34.3E410";

   @Inject
   private DocumentFacade documentFacade;

   @Inject
   private DataDao dataDao;

   @Inject
   private CollectionDao collectionDao;

   @Inject
   private DocumentDao documentDao;

   @Inject
   private OrganizationDao organizationDao;

   @Inject
   private ProjectDao projectDao;

   @Inject
   private UserDao userDao;

   @Inject
   private WorkspaceKeeper workspaceKeeper;

   private Collection collection;

   @Before
   public void configureCollection() {
      User user = new User(USER);
      this.user = userDao.createUser(user);

      Organization organization = new Organization();
      organization.setCode(ORGANIZATION_CODE);
      organization.setPermissions(new Permissions());
      Organization storedOrganization = organizationDao.createOrganization(organization);

      projectDao.setOrganization(storedOrganization);

      Permissions organizationPermissions = new Permissions();
      Permission userPermission = Permission.buildWithRoles(this.user.getId(), Organization.ROLES);
      organizationPermissions.updateUserPermissions(userPermission);
      storedOrganization.setPermissions(organizationPermissions);
      organizationDao.updateOrganization(storedOrganization.getId(), storedOrganization);

      Project project = new Project();
      project.setCode(PROJECT_CODE);

      Permissions projectPermissions = new Permissions();
      projectPermissions.updateUserPermissions(new Permission(this.user.getId(), Project.ROLES.stream().map(Role::toString).collect(Collectors.toSet())));
      project.setPermissions(projectPermissions);
      Project storedProject = projectDao.createProject(project);

      workspaceKeeper.setWorkspaceIds(storedOrganization.getId(), storedProject.getId());

      collectionDao.setProject(storedProject);
      collectionDao.createRepository(storedProject);

      Permissions collectionPermissions = new Permissions();
      collectionPermissions.updateUserPermissions(new Permission(this.user.getId(), Project.ROLES.stream().map(Role::toString).collect(Collectors.toSet())));
      Collection jsonCollection = new Collection(null, COLLECTION_NAME, COLLECTION_ICON, COLLECTION_COLOR, collectionPermissions);
      jsonCollection.setDocumentsCount(0);
      jsonCollection.setLastAttributeNum(0);
      collection = collectionDao.createCollection(jsonCollection);
   }

   private Document prepareDocument() {
      DataDocument data = new DataDocument()
            .append(KEY1, VALUE1)
            .append(KEY2, VALUE2)
            .append(KEY3, VALUE3)
            .append(KEY4, VALUE4)
            .append(KEY5, VALUE5);

      return new Document(data);
   }

   private Document createDocument() {
      return documentFacade.createDocument(collection.getId(), prepareDocument());
   }

   @Test
   public void testCreateDocument() {
      Collection storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(0);
      assertThat(storedCollection.getAttributes()).extracting(Attribute::getName).isEmpty();

      Document document = prepareDocument();

      ZonedDateTime beforeTime = ZonedDateTime.now();
      String id = documentFacade.createDocument(collection.getId(), document).getId();
      assertThat(id).isNotNull();

      Document storedDocument = documentDao.getDocumentById(id);
      assertThat(storedDocument).isNotNull();

      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(storedDocument.getId()).isEqualTo(id);
      assertions.assertThat(storedDocument.getCollectionId()).isEqualTo(collection.getId());
      assertions.assertThat(storedDocument.getCreatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(storedDocument.getCreationDate()).isAfterOrEqualTo(beforeTime).isBeforeOrEqualTo(ZonedDateTime.now());
      assertions.assertThat(storedDocument.getUpdatedBy()).isNull();
      assertions.assertThat(storedDocument.getUpdateDate()).isNull();
      assertions.assertThat(storedDocument.getData()).isNull();
      assertions.assertAll();

      DataDocument storedData = dataDao.getData(collection.getId(), id);
      assertThat(storedData).isNotNull();
      assertThat(storedData).containsEntry(KEY1, VALUE1);
      assertThat(storedData).containsEntry(KEY2, VALUE2);
      assertThat(storedData.get(KEY3)).isInstanceOf(Long.class);
      assertThat(storedData.get(KEY4)).isInstanceOf(BigDecimal.class);
      assertThat(storedData.get(KEY5)).isInstanceOf(BigDecimal.class);

      storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);
   }

   @Test
   public void testDuplicateDocuments() {
      Collection storedCollection = collectionDao.getCollectionById(collection.getId());

      final List<Document> documents = IntStream.range(0, 10).mapToObj(i -> {
         var doc = prepareDocument();
         doc.getData().append("sample", i);
         return doc;
      }).collect(Collectors.toList());

      var createdDocuments = documentFacade.createDocuments(storedCollection.getId(), documents, false);
      var duplicatedDocuments = documentFacade.duplicateDocuments(storedCollection.getId(), createdDocuments.stream().map(Document::getId).collect(Collectors.toList()));

      var originalIds = createdDocuments.stream().map(Document::getId).collect(Collectors.toList());
      assertThat(duplicatedDocuments.stream().map(Document::getId).collect(Collectors.toSet())).hasSize(10);

      var newIds = duplicatedDocuments.stream().map(d -> d.getMetaData().getString(Document.META_ORIGINAL_DOCUMENT_ID)).collect(Collectors.toList());
      assertThat(newIds).containsExactly(originalIds.toArray(new String[0])); // expecting matching order
      assertThat(duplicatedDocuments.stream().map(Document::getId).collect(Collectors.toList())).doesNotContain(originalIds.toArray(new String[0]));
   }

   @Test
   public void testUpdateDocumentData() {
      Document document = createDocument();
      String id = document.getId();

      Collection storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);

      DataDocument data = new DataDocument(KEY1, VALUE2);

      ZonedDateTime beforeUpdateTime = ZonedDateTime.now();
      Document updatedDocument = documentFacade.updateDocumentData(collection.getId(), id, data);
      assertThat(updatedDocument).isNotNull();
      assertThat(updatedDocument.getData()).isNotNull();
      assertThat(updatedDocument.getData()).containsEntry(KEY1, VALUE2);

      Document storedDocument = documentDao.getDocumentById(id);
      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(storedDocument.getId()).isEqualTo(id);
      assertions.assertThat(storedDocument.getCollectionId()).isEqualTo(collection.getId());
      assertions.assertThat(storedDocument.getCreatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(storedDocument.getCreationDate()).isBeforeOrEqualTo(beforeUpdateTime);
      assertions.assertThat(storedDocument.getUpdatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(storedDocument.getUpdateDate()).isAfterOrEqualTo(beforeUpdateTime).isBeforeOrEqualTo(ZonedDateTime.now());
      assertions.assertThat(storedDocument.getDataVersion()).isEqualTo(1);
      assertions.assertThat(storedDocument.getData()).isNull();
      assertions.assertAll();

      DataDocument storedData = dataDao.getData(collection.getId(), id);
      assertThat(storedData).isNotNull();
      assertThat(storedData).containsEntry(KEY1, VALUE2);
      assertThat(storedData).doesNotContainKey(KEY2);

      storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);
   }

   @Test
   public void testPatchDocumentData() {
      Document document = createDocument();
      String id = document.getId();

      Collection storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);

      DataDocument data = new DataDocument(KEY1, VALUE2);

      ZonedDateTime beforeUpdateTime = ZonedDateTime.now();
      Document updatedDocument = documentFacade.patchDocumentData(collection.getId(), id, data);
      assertThat(updatedDocument).isNotNull();
      assertThat(updatedDocument.getData()).isNotNull();
      assertThat(updatedDocument.getData()).containsEntry(KEY1, VALUE2);

      Document storedDocument = documentDao.getDocumentById(id);
      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(storedDocument.getId()).isEqualTo(id);
      assertions.assertThat(storedDocument.getCollectionId()).isEqualTo(collection.getId());
      assertions.assertThat(storedDocument.getCreatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(storedDocument.getCreationDate()).isBeforeOrEqualTo(beforeUpdateTime);
      assertions.assertThat(storedDocument.getUpdatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(storedDocument.getUpdateDate()).isAfterOrEqualTo(beforeUpdateTime).isBeforeOrEqualTo(ZonedDateTime.now());
      assertions.assertThat(storedDocument.getDataVersion()).isEqualTo(1);
      assertions.assertThat(storedDocument.getData()).isNull();
      assertions.assertAll();

      DataDocument storedData = dataDao.getData(collection.getId(), id);
      assertThat(storedData).isNotNull();
      assertThat(storedData).containsEntry(KEY1, VALUE2);
      assertThat(storedData).containsEntry(KEY2, VALUE2);

      storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);
   }

   @Test
   public void testDeleteDocument() {
      String id = createDocument().getId();

      Collection storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(1);

      documentFacade.deleteDocument(collection.getId(), id);

      assertThatThrownBy(() -> documentDao.getDocumentById(id))
            .isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> dataDao.getData(collection.getId(), id))
            .isInstanceOf(ResourceNotFoundException.class);

      storedCollection = collectionDao.getCollectionById(collection.getId());

      assertThat(storedCollection.getDocumentsCount()).isEqualTo(0);
   }

   @Test
   public void testGetDocument() {
      String id = createDocument().getId();

      Document document = documentFacade.getDocument(collection.getId(), id);
      assertThat(document).isNotNull();

      SoftAssertions assertions = new SoftAssertions();
      assertions.assertThat(document.getId()).isEqualTo(id);
      assertions.assertThat(document.getCollectionId()).isEqualTo(collection.getId());
      assertions.assertThat(document.getCreatedBy()).isEqualTo(this.user.getId());
      assertions.assertThat(document.getCreationDate()).isBeforeOrEqualTo(ZonedDateTime.now());
      assertions.assertThat(document.getUpdatedBy()).isNull();
      assertions.assertThat(document.getUpdateDate()).isNull();
      assertions.assertAll();

      DataDocument data = document.getData();
      assertThat(data).isNotNull();
      assertThat(data).containsEntry(KEY1, VALUE1);
      assertThat(data).containsEntry(KEY2, VALUE2);
   }

   @Test
   public void testAddFavoriteDocument() {
      List<String> ids = new LinkedList<>();
      for (int i = 0; i < 10; i++) {
         ids.add(createDocument().getId());
      }

      assertThat(documentFacade.getFavoriteDocumentsIds()).isEmpty();

      documentFacade.addFavoriteDocument(collection.getId(), ids.get(0));
      documentFacade.addFavoriteDocument(collection.getId(), ids.get(3));
      documentFacade.addFavoriteDocument(collection.getId(), ids.get(5));

      assertThat(documentFacade.getFavoriteDocumentsIds()).containsOnly(ids.get(0), ids.get(3), ids.get(5));

      for (int i = 0; i < 10; i++) {
         assertThat(documentFacade.isFavorite(ids.get(i))).isEqualTo(i == 0 || i == 3 || i == 5);
      }
   }

   @Test
   public void testRemoveFavoriteCollection() {
      List<String> ids = new LinkedList<>();
      for (int i = 0; i < 10; i++) {
         ids.add(createDocument().getId());
      }

      documentFacade.addFavoriteDocument(collection.getId(), ids.get(1));
      documentFacade.addFavoriteDocument(collection.getId(), ids.get(2));
      documentFacade.addFavoriteDocument(collection.getId(), ids.get(9));

      assertThat(documentFacade.getFavoriteDocumentsIds()).containsOnly(ids.get(1), ids.get(2), ids.get(9));

      documentFacade.removeFavoriteDocument(collection.getId(), ids.get(1));
      documentFacade.removeFavoriteDocument(collection.getId(), ids.get(9));

      assertThat(documentFacade.getFavoriteDocumentsIds()).containsOnly(ids.get(2));
   }
}
