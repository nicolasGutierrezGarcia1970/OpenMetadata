package org.openmetadata.service.search;

import static org.openmetadata.service.exception.CatalogExceptionMessage.NOT_IMPLEMENTED_METHOD;

import java.io.IOException;
import java.security.KeyStoreException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.net.ssl.SSLContext;
import javax.ws.rs.core.Response;
import lombok.Getter;
import org.apache.commons.lang3.tuple.Pair;
import org.openmetadata.schema.api.searcg.SearchSettings;
import org.openmetadata.schema.dataInsight.DataInsightChartResult;
import org.openmetadata.schema.dataInsight.custom.DataInsightCustomChart;
import org.openmetadata.schema.dataInsight.custom.DataInsightCustomChartResultList;
import org.openmetadata.schema.service.configuration.elasticsearch.ElasticSearchConfiguration;
import org.openmetadata.schema.settings.SettingsType;
import org.openmetadata.schema.tests.DataQualityReport;
import org.openmetadata.schema.type.EntityReference;
import org.openmetadata.service.exception.CustomExceptionMessage;
import org.openmetadata.service.resources.settings.SettingsCache;
import org.openmetadata.service.search.models.IndexMapping;
import org.openmetadata.service.search.security.RBACConditionEvaluator;
import org.openmetadata.service.security.policyevaluator.SubjectContext;
import org.openmetadata.service.util.SSLUtil;
import os.org.opensearch.action.bulk.BulkRequest;
import os.org.opensearch.action.bulk.BulkResponse;
import os.org.opensearch.client.RequestOptions;

public interface SearchClient {
  ExecutorService asyncExecutor = Executors.newFixedThreadPool(1);
  String UPDATE = "update";

  String ADD = "add";

  String DELETE = "delete";
  String GLOBAL_SEARCH_ALIAS = "all";
  String GLOSSARY_TERM_SEARCH_INDEX = "glossary_term_search_index";
  String TAG_SEARCH_INDEX = "tag_search_index";
  String DEFAULT_UPDATE_SCRIPT = "for (k in params.keySet()) { ctx._source.put(k, params.get(k)) }";
  String REMOVE_DOMAINS_CHILDREN_SCRIPT = "ctx._source.remove('domain')";

  // Updates field if null or if inherited is true and the parent is the same (matched by previous
  // ID), setting inherited=true on the new object.
  String PROPAGATE_ENTITY_REFERENCE_FIELD_SCRIPT =
      "if (ctx._source.%s == null || (ctx._source.%s != null && ctx._source.%s.inherited == true)) { "
          + "def newObject = params.%s; "
          + "newObject.inherited = true; "
          + "ctx._source.put('%s', newObject); "
          + "}";

  String PROPAGATE_FIELD_SCRIPT = "ctx._source.put('%s', '%s')";

  String REMOVE_PROPAGATED_ENTITY_REFERENCE_FIELD_SCRIPT =
      "if ((ctx._source.%s != null) && (ctx._source.%s.inherited == true)){ ctx._source.remove('%s');}";
  String REMOVE_PROPAGATED_FIELD_SCRIPT = "ctx._source.remove('%s')";

  // Updates field if inherited is true and the parent is the same (matched by previous ID), setting
  // inherited=true on the new object.
  String UPDATE_PROPAGATED_ENTITY_REFERENCE_FIELD_SCRIPT =
      "if (ctx._source.%s == null || (ctx._source.%s.inherited == true && ctx._source.%s.id == params.entityBeforeUpdate.id)) { "
          + "def newObject = params.%s; "
          + "newObject.inherited = true; "
          + "ctx._source.put('%s', newObject); "
          + "}";
  String SOFT_DELETE_RESTORE_SCRIPT = "ctx._source.put('deleted', '%s')";
  String REMOVE_TAGS_CHILDREN_SCRIPT =
      "for (int i = 0; i < ctx._source.tags.length; i++) { if (ctx._source.tags[i].tagFQN == params.fqn) { ctx._source.tags.remove(i) }}";

  String REMOVE_LINEAGE_SCRIPT =
      "for (int i = 0; i < ctx._source.lineage.length; i++) { if (ctx._source.lineage[i].doc_id == '%s') { ctx._source.lineage.remove(i) }}";

  String ADD_UPDATE_LINEAGE =
      "boolean docIdExists = false; for (int i = 0; i < ctx._source.lineage.size(); i++) { if (ctx._source.lineage[i].doc_id.equalsIgnoreCase(params.lineageData.doc_id)) { ctx._source.lineage[i] = params.lineageData; docIdExists = true; break;}}if (!docIdExists) {ctx._source.lineage.add(params.lineageData);}";
  String UPDATE_ADDED_DELETE_GLOSSARY_TAGS =
      "if (ctx._source.tags != null) { for (int i = ctx._source.tags.size() - 1; i >= 0; i--) { if (params.tagDeleted != null) { for (int j = 0; j < params.tagDeleted.size(); j++) { if (ctx._source.tags[i].tagFQN.equalsIgnoreCase(params.tagDeleted[j].tagFQN)) { ctx._source.tags.remove(i); } } } } } if (ctx._source.tags == null) { ctx._source.tags = []; } if (params.tagAdded != null) { ctx._source.tags.addAll(params.tagAdded); } ctx._source.tags = ctx._source.tags .stream() .distinct() .sorted((o1, o2) -> o1.tagFQN.compareTo(o2.tagFQN)) .collect(Collectors.toList());";
  String REMOVE_TEST_SUITE_CHILDREN_SCRIPT =
      "for (int i = 0; i < ctx._source.testSuites.length; i++) { if (ctx._source.testSuites[i].id == '%s') { ctx._source.testSuites.remove(i) }}";

  String ADD_OWNERS_SCRIPT =
      "if (ctx._source.owners == null || ctx._source.owners.isEmpty() || "
          + "(ctx._source.owners.size() > 0 && ctx._source.owners[0] != null && ctx._source.owners[0].inherited == true)) { "
          + "ctx._source.owners = params.updatedOwners; "
          + "}";

  String PROPAGATE_TEST_SUITES_SCRIPT = "ctx._source.testSuites = params.testSuites";

  String REMOVE_OWNERS_SCRIPT =
      "if (ctx._source.owners != null && !ctx._source.owners.isEmpty()) { "
          + "ctx._source.owners.removeIf(owner -> "
          + "params.deletedOwners.stream().anyMatch(deletedOwner -> deletedOwner.id == owner.id) && owner.inherited == true); "
          + "}";

  String NOT_IMPLEMENTED_ERROR_TYPE = "NOT_IMPLEMENTED";

  boolean isClientAvailable();

  ElasticSearchConfiguration.SearchType getSearchType();

  boolean indexExists(String indexName);

  void createIndex(IndexMapping indexMapping, String indexMappingContent);

  void updateIndex(IndexMapping indexMapping, String indexMappingContent);

  void deleteIndex(IndexMapping indexMapping);

  void createAliases(IndexMapping indexMapping);

  void addIndexAlias(IndexMapping indexMapping, String... aliasName);

  Response search(SearchRequest request, SubjectContext subjectContext) throws IOException;

  Response getDocByID(String indexName, String entityId) throws IOException;

  default ExecutorService getAsyncExecutor() {
    return asyncExecutor;
  }

  SearchResultListMapper listWithOffset(
      String filter,
      int limit,
      int offset,
      String index,
      SearchSortFilter searchSortFilter,
      String q)
      throws IOException;

  Response searchBySourceUrl(String sourceUrl) throws IOException;

  Response searchLineage(
      String fqn,
      int upstreamDepth,
      int downstreamDepth,
      String queryFilter,
      boolean deleted,
      String entityType)
      throws IOException;

  Response searchDataQualityLineage(
      String fqn, int upstreamDepth, String queryFilter, boolean deleted) throws IOException;

  /*
   Used for listing knowledge page hierarchy for a given parent and page type, used in Elastic/Open SearchClientExtension
  */
  @SuppressWarnings("unused")
  default Response listPageHierarchy(String parent, String pageType) {
    throw new CustomExceptionMessage(
        Response.Status.NOT_IMPLEMENTED, NOT_IMPLEMENTED_ERROR_TYPE, NOT_IMPLEMENTED_METHOD);
  }

  Map<String, Object> searchLineageInternal(
      String fqn,
      int upstreamDepth,
      int downstreamDepth,
      String queryFilter,
      boolean deleted,
      String entityType)
      throws IOException;

  Response searchByField(String fieldName, String fieldValue, String index) throws IOException;

  Response aggregate(String index, String fieldName, String value, String query) throws IOException;

  JsonObject aggregate(
      String query, String index, SearchAggregation searchAggregation, String filters)
      throws IOException;

  DataQualityReport genericAggregation(
      String query, String index, SearchAggregation aggregationMetadata) throws IOException;

  Response suggest(SearchRequest request) throws IOException;

  void createEntity(String indexName, String docId, String doc);

  void createTimeSeriesEntity(String indexName, String docId, String doc);

  void updateEntity(String indexName, String docId, Map<String, Object> doc, String scriptTxt);

  /* This function takes in Entity Reference, Search for occurances of those  entity across ES, and perform an update for that with reindexing the data from the database to ES */
  void reindexAcrossIndices(String matchingKey, EntityReference sourceRef);

  void deleteByScript(String indexName, String scriptTxt, Map<String, Object> params);

  void deleteEntity(String indexName, String docId);

  void deleteEntityByFields(List<String> indexName, List<Pair<String, String>> fieldAndValue);

  void deleteEntityByFQNPrefix(String indexName, String fqnPrefix);

  void softDeleteOrRestoreEntity(String indexName, String docId, String scriptTxt);

  void softDeleteOrRestoreChildren(
      List<String> indexName, String scriptTxt, List<Pair<String, String>> fieldAndValue);

  void updateChildren(
      String indexName,
      Pair<String, String> fieldAndValue,
      Pair<String, Map<String, Object>> updates);

  void updateChildren(
      List<String> indexName,
      Pair<String, String> fieldAndValue,
      Pair<String, Map<String, Object>> updates);

  void updateLineage(
      String indexName, Pair<String, String> fieldAndValue, Map<String, Object> lineagaData);

  Response listDataInsightChartResult(
      Long startTs,
      Long endTs,
      String tier,
      String team,
      DataInsightChartResult.DataInsightChartType dataInsightChartName,
      Integer size,
      Integer from,
      String queryFilter,
      String dataReportIndex)
      throws IOException;

  // TODO: Think if it makes sense to have this or maybe a specific deleteByRange
  void deleteByQuery(String index, String query);

  default BulkResponse bulk(BulkRequest data, RequestOptions options) throws IOException {
    throw new CustomExceptionMessage(
        Response.Status.NOT_IMPLEMENTED, NOT_IMPLEMENTED_ERROR_TYPE, NOT_IMPLEMENTED_METHOD);
  }

  default es.org.elasticsearch.action.bulk.BulkResponse bulk(
      es.org.elasticsearch.action.bulk.BulkRequest data,
      es.org.elasticsearch.client.RequestOptions options)
      throws IOException {
    throw new CustomExceptionMessage(
        Response.Status.NOT_IMPLEMENTED, NOT_IMPLEMENTED_ERROR_TYPE, NOT_IMPLEMENTED_METHOD);
  }

  void close();

  default SSLContext createElasticSearchSSLContext(
      ElasticSearchConfiguration elasticSearchConfiguration) throws KeyStoreException {
    return elasticSearchConfiguration.getScheme().equals("https")
        ? SSLUtil.createSSLContext(
            elasticSearchConfiguration.getTruststorePath(),
            elasticSearchConfiguration.getTruststorePassword(),
            "ElasticSearch")
        : null;
  }

  @Getter
  class SearchResultListMapper {
    public List<Map<String, Object>> results;
    public long total;

    public SearchResultListMapper(List<Map<String, Object>> results, long total) {
      this.results = results;
      this.total = total;
    }
  }

  static JsonArray getAggregationBuckets(JsonObject aggregationJson) {
    return aggregationJson.getJsonArray("buckets");
  }

  static JsonObject getAggregationObject(JsonObject aggregationJson, String key) {
    return aggregationJson.getJsonObject(key);
  }

  static String getAggregationKeyValue(JsonObject aggregationJson) {
    return aggregationJson.getString("key");
  }

  default DataInsightCustomChartResultList buildDIChart(
      DataInsightCustomChart diChart, long start, long end) throws IOException {
    return null;
  }

  default List<Map<String, String>> fetchDIChartFields() throws IOException {
    return null;
  }

  Object getLowLevelClient();

  static boolean shouldApplyRbacConditions(
      SubjectContext subjectContext, RBACConditionEvaluator rbacConditionEvaluator) {
    return Boolean.TRUE.equals(
            SettingsCache.getSetting(SettingsType.SEARCH_SETTINGS, SearchSettings.class)
                .getEnableAccessControl())
        && subjectContext != null
        && !subjectContext.isAdmin()
        && !subjectContext.isBot()
        && rbacConditionEvaluator != null;
  }
}
