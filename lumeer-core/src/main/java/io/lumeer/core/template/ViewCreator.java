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
package io.lumeer.core.template;

import io.lumeer.api.model.CollectionAttributeFilter;
import io.lumeer.api.model.LinkAttributeFilter;
import io.lumeer.api.model.Query;
import io.lumeer.api.model.QueryStem;
import io.lumeer.api.model.View;
import io.lumeer.core.constraint.ConstraintManager;
import io.lumeer.core.exception.TemplateNotAvailableException;
import io.lumeer.core.facade.ViewFacade;
import io.lumeer.core.facade.configuration.DefaultConfigurationProducer;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.IOException;
import java.sql.Date;
import java.time.DateTimeException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ViewCreator extends WithIdCreator {

   private final ViewFacade viewFacade;
   private final ObjectMapper mapper;
   private final ConstraintManager constraintManager;

   private ViewCreator(final TemplateParser templateParser, final ViewFacade viewFacade, final DefaultConfigurationProducer defaultConfigurationProducer) {
      super(templateParser);
      this.viewFacade = viewFacade;
      this.constraintManager = ConstraintManager.getInstance(defaultConfigurationProducer);

      mapper = new ObjectMapper();
      AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
      AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(TypeFactory.defaultInstance());
      AnnotationIntrospector pair = AnnotationIntrospector.pair(primary, secondary);
      mapper.setAnnotationIntrospector(pair);
   }

   public static void createViews(final TemplateParser templateParser, final ViewFacade viewFacade, final DefaultConfigurationProducer defaultConfigurationProducer) {
      final ViewCreator creator = new ViewCreator(templateParser, viewFacade, defaultConfigurationProducer);
      creator.createViews();
   }

   private void createViews() {
      ((JSONArray) templateParser.getTemplate().get("views")).forEach(viewObj -> {
         try {
            var viewJson = (JSONObject) viewObj;
            var templateId = TemplateParserUtils.getId(viewJson);
            viewJson.remove("_id");
            var view = mapper.readValue(viewJson.toJSONString(), View.class);
            viewJson.put("_id", templateId);
            view.setQuery(translateQuery(view.getQuery()));
            view.setConfig(translateConfig(view.getConfig()));
            view = viewFacade.createView(view);
            templateParser.getDict().addView(templateId, view);
         } catch (IOException e) {
            throw new TemplateNotAvailableException(e);
         }
      });
   }

   private Query translateQuery(final Query query) {
      var newStems = new ArrayList<QueryStem>();

      query.getStems().forEach(stem -> {
         var collectionId = stem.getCollectionId() != null ? templateParser.getDict().getCollectionId(stem.getCollectionId()) : null;

         List<String> linkTypeIds = new ArrayList<>();
         var linkTypeIdsUsed = false;
         if (stem.getLinkTypeIds() != null) {
            linkTypeIdsUsed = true;
            stem.getLinkTypeIds().forEach(linkTypeId -> linkTypeIds.add(templateParser.getDict().getLinkTypeId(linkTypeId)));
         }

         Set<String> documentIds = new HashSet<>();
         var documentIdsUsed = false;
         if (stem.getDocumentIds() != null) {
            documentIdsUsed = true;
            stem.getDocumentIds().forEach(documentId -> documentIds.add(templateParser.getDict().getDocumentId(documentId)));
         }

         Set<CollectionAttributeFilter> collectionAttributeFilters = new HashSet<>();
         var filtersUsed = false;
         if (stem.getFilters() != null) {
            filtersUsed = true;
            stem.getFilters().forEach(filter -> collectionAttributeFilters.add(new CollectionAttributeFilter(
                  templateParser.getDict().getCollectionId(filter.getCollectionId()),
                  filter.getAttributeId(),
                  filter.getCondition(),
                  filter.getConditionValues()
            )));
         }

         Set<LinkAttributeFilter> linkAttributeFilters = new HashSet<>();
         var linkFiltersUsed = false;
         if (stem.getCollectionId() != null) {
            linkFiltersUsed = true;
            stem.getLinkFilters().forEach(filter -> linkAttributeFilters.add(new LinkAttributeFilter(
                  templateParser.getDict().getLinkTypeId(filter.getLinkTypeId()),
                  filter.getAttributeId(),
                  filter.getCondition(),
                  filter.getConditionValues()
            )));
         }

         newStems.add(new QueryStem(
               collectionId,
               linkTypeIdsUsed ? linkTypeIds : null,
               documentIdsUsed ? documentIds : null,
               filtersUsed ? collectionAttributeFilters : null,
               linkFiltersUsed ? linkAttributeFilters : null
         ));
      });

      final Query result = new Query(newStems, query.getFulltexts(), query.getPage(), query.getPageSize());
      return result;
   }

   private Object translateConfig(final Object config) {
      if (config instanceof String) {
         return translateString((String) config);
      } else if (config instanceof List) {
         var newList = new ArrayList();
         ((List) config).forEach(value -> {
            newList.add(translateConfig(value));
         });
         return newList;
      } else if (config instanceof Set) {
         var newSet = new HashSet();
         ((Set) config).forEach(value -> {
            newSet.add(translateConfig(value));
         });
         return newSet;
      } else if (config instanceof Map) {
         var newMap = new HashMap<>();
         ((Map) config).forEach((k, v) -> {
            newMap.put(translateConfig(k), translateConfig(v));
         });
         return newMap;
      } else {
         return config;
      }
   }

   private Object translateString(final String resourceId) {
      if (resourceId == null) {
         return null;
      }

      if (resourceId.length() == 24) {

         String res = templateParser.getDict().getCollectionId(resourceId);

         if (res == null) {
            res = templateParser.getDict().getLinkTypeId(resourceId);

            if (res == null) {
               res = templateParser.getDict().getDocumentId(resourceId);

               if (res == null) {
                  res = templateParser.getDict().getLinkInstanceId(resourceId);

                  if (res == null) {
                     res = templateParser.getDict().getViewId(resourceId);
                  }
               }
            }
         }

         if (res != null) {
            return res;
         }
      }

      try {
         final ZonedDateTime zdt = ZonedDateTime.parse(resourceId, constraintManager.getDateDecoder());
         return Date.from(zdt.toInstant());
      } catch (DateTimeException e) {
         // nps
      }

      return constraintManager.encode(resourceId);
   }
}
