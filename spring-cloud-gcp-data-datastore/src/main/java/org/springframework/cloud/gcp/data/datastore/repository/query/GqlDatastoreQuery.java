/*
 *  Copyright 2018 original author or authors.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.springframework.cloud.gcp.data.datastore.repository.query;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.cloud.Timestamp;
import com.google.cloud.datastore.Blob;
import com.google.cloud.datastore.Cursor;
import com.google.cloud.datastore.Entity;
import com.google.cloud.datastore.GqlQuery;
import com.google.cloud.datastore.GqlQuery.Builder;
import com.google.cloud.datastore.Key;
import com.google.cloud.datastore.Query.ResultType;
import com.google.common.collect.ImmutableMap;

import org.springframework.cloud.gcp.data.datastore.core.DatastoreOperations;
import org.springframework.cloud.gcp.data.datastore.core.mapping.DatastoreDataException;
import org.springframework.cloud.gcp.data.datastore.core.mapping.DatastoreMappingContext;
import org.springframework.data.repository.query.Parameter;
import org.springframework.data.repository.query.Parameters;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.QueryMethodEvaluationContextProvider;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.util.StringUtils;

/**
 * Query Method for GQL queries.
 *
 * @author Chengyuan Zhao
 *
 * @since 1.1
 */
public class GqlDatastoreQuery<T> implements RepositoryQuery {

	private static final Map<Class<?>, Function<Builder, BiFunction<String, Object, Builder>>>
			GQL_PARAM_BINDING_FUNC_MAP;

	static {
		GQL_PARAM_BINDING_FUNC_MAP = ImmutableMap
				.<Class<?>, Function<Builder, BiFunction<String, Object, Builder>>>builder()
				.put(Cursor.class, builder -> (s, o) -> builder.setBinding(s, (Cursor) o))
				.put(String.class, builder -> (s, o) -> builder.setBinding(s, (String) o))
				.put(String[].class, builder -> (s, o) -> builder.setBinding(s, (String[]) o))
				.put(Long.class, builder -> (s, o) -> builder.setBinding(s, (Long) o))
				.put(long[].class, builder -> (s, o) -> builder.setBinding(s, (long[]) o))
				.put(Double.class, builder -> (s, o) -> builder.setBinding(s, (Double) o))
				.put(double[].class, builder -> (s, o) -> builder.setBinding(s, (double[]) o))
				.put(Boolean.class,
						builder -> (s, o) -> builder.setBinding(s, (Boolean) o))
				.put(boolean[].class,
						builder -> (s, o) -> builder.setBinding(s, (boolean[]) o))
				.put(Timestamp.class,
						builder -> (s, o) -> builder.setBinding(s, (Timestamp) o))
				.put(Timestamp[].class,
						builder -> (s, o) -> builder.setBinding(s, (Timestamp[]) o))
				.put(Key.class, builder -> (s, o) -> builder.setBinding(s, (Key) o))
				.put(Key[].class, builder -> (s, o) -> builder.setBinding(s, (Key[]) o))
				.put(Blob.class, builder -> (s, o) -> builder.setBinding(s, (Blob) o))
				.put(Blob[].class, builder -> (s, o) -> builder.setBinding(s, (Blob[]) o))
				.build();
	}

	private final QueryMethod queryMethod;

	private final DatastoreOperations datastoreOperations;

	protected final DatastoreMappingContext datastoreMappingContext;

	private final Class<T> entityType;

	private final String gql;

	// unused currently, but will be used for SpEL expression in the query.
	private QueryMethodEvaluationContextProvider evaluationContextProvider;

	// unused currently, but will be used for SpEL expression in the query.
	private SpelExpressionParser expressionParser;

	/**
	 * Constructor
	 * @param type the underlying entity type
	 * @param queryMethod the underlying query method to support.
	 * @param datastoreOperations used for executing queries.
	 * @param datastoreMappingContext used for getting metadata about entities.
	 */
	GqlDatastoreQuery(Class<T> type, QueryMethod queryMethod,
			DatastoreOperations datastoreOperations, String gql,
			QueryMethodEvaluationContextProvider evaluationContextProvider,
			SpelExpressionParser expressionParser,
			DatastoreMappingContext datastoreMappingContext) {
		this.queryMethod = queryMethod;
		this.entityType = type;
		this.datastoreOperations = datastoreOperations;
		this.datastoreMappingContext = datastoreMappingContext;
		this.evaluationContextProvider = evaluationContextProvider;
		this.expressionParser = expressionParser;
		this.gql = StringUtils.trimTrailingCharacter(gql.trim(), ';');
	}

	@Override
	public Object execute(Object[] parameters) {
		List<T> rawResult = executeRawResult(parameters);
		return applyProjection(rawResult);
	}

	@Override
	public QueryMethod getQueryMethod() {
		return this.queryMethod;
	}

	private List<T> executeRawResult(Object[] parameters) {
		List<T> results = new ArrayList<>();
		Iterable<T> found = this.datastoreOperations
				.query(bindArgsToGqlQuery(this.gql, getParamTags(), parameters),
						this.entityType);
		if (found != null) {
			found.forEach(results::add);
		}
		return results;
	}

	private List<String> getParamTags() {
		List<String> tags = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Parameters parameters = getQueryMethod().getParameters();
		for (int i = 0; i < parameters.getNumberOfParameters(); i++) {
			Parameter param = parameters.getParameter(i);
			Optional<String> paramName = param.getName();
			if (!paramName.isPresent()) {
				throw new DatastoreDataException(
						"Query method has a parameter without a valid name: "
								+ getQueryMethod().getName());
			}
			String name = paramName.get();
			if (seen.contains(name)) {
				throw new DatastoreDataException(
						"More than one param has the same name: " + name);
			}
			seen.add(name);
			tags.add(name);
		}
		return tags;
	}

	private Object processRawObjectForProjection(Object object) {
		return this.queryMethod.getResultProcessor().processResult(object);
	}

	private Object applyProjection(List<T> rawResult) {
		if (rawResult == null) {
			return null;
		}
		return rawResult.stream().map(this::processRawObjectForProjection)
				.collect(Collectors.toList());
	}

	private GqlQuery<Entity> bindArgsToGqlQuery(String gql, List<String> tags,
			Object[] vals) {
		Builder<Entity> builder = GqlQuery.newGqlQueryBuilder(ResultType.ENTITY, gql);
		if (tags.size() != vals.length) {
			throw new DatastoreDataException("Annotated GQL Query Method "
					+ this.queryMethod.getName() + " has " + tags.size()
					+ " tags but a different number of parameter values: " + vals.length);
		}
		for (int i = 0; i < tags.size(); i++) {
			Object val = vals[i];
			if (!GQL_PARAM_BINDING_FUNC_MAP.containsKey(val.getClass())) {
				throw new DatastoreDataException(
						"Param value for GQL annotated query is not a supported Cloud "
								+ "Datastore GQL param type: " + val.getClass());
			}
			// this value must be set due to compiler rule
			Object unusued = GQL_PARAM_BINDING_FUNC_MAP.get(val.getClass()).apply(builder)
					.apply(tags.get(i), val);
		}
		return builder.build();
	}
}
