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

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;

import org.springframework.cloud.gcp.data.datastore.core.DatastoreOperations;
import org.springframework.cloud.gcp.data.datastore.core.mapping.DatastoreMappingContext;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;

/**
 * Abstract class for implementing Cloud Datastore query methods.
 *
 * @author Chengyuan Zhao
 *
 * @since 1.1
 */
abstract class AbstractDatastoreQuery<T> implements RepositoryQuery {

	protected final DatastoreMappingContext datastoreMappingContext;

	final QueryMethod queryMethod;

	final DatastoreOperations datastoreOperations;

	final Class<T> entityType;

	AbstractDatastoreQuery(QueryMethod queryMethod,
			DatastoreOperations datastoreOperations,
			DatastoreMappingContext datastoreMappingContext, Class<T> entityType) {
		this.queryMethod = queryMethod;
		this.datastoreOperations = datastoreOperations;
		this.datastoreMappingContext = datastoreMappingContext;
		this.entityType = entityType;
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

	/**
	 * Execute query with given parameters and produce objects of the repository's domain
	 * type. These objects are then used to create projections.
	 * @param parameters the parameters with which to run the query.
	 * @return the domain objects.
	 */
	abstract List<T> executeRawResult(Object[] parameters);

	Object applyProjection(List<T> rawResult) {
		if (rawResult == null) {
			return null;
		}
		return rawResult.stream().map(this::processRawObjectForProjection)
				.collect(Collectors.toList());
	}

	@VisibleForTesting
	Object processRawObjectForProjection(Object object) {
		return this.queryMethod.getResultProcessor().processResult(object);
	}
}
