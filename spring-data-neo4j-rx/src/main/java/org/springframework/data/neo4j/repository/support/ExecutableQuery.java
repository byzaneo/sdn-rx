/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.repository.support;

import static java.util.stream.Collectors.*;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.apiguardian.api.API;
import org.neo4j.driver.exceptions.NoSuchRecordException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.repository.NoResultException;

/**
 * Interface for controlling query execution.
 *
 * @param <T> The type of the objects returned by this query.
 * @author Michael J. Simons
 * @soundtrack Deichkind - Niveau weshalb warum
 * @since 1.0
 */
@API(status = API.Status.INTERNAL, since = "1.0")
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExecutableQuery<T> {

	public static <T> ExecutableQuery<T> create(Neo4jClient neo4jClient, PreparedQuery<T> preparedQuery) {

		Neo4jClient.MappingSpec<Optional<T>, Collection<T>, T> mappingSpec = neo4jClient
			.newQuery(preparedQuery.getCypherQuery())
			.bindAll(preparedQuery.getParameters())
			.fetchAs(preparedQuery.getResultType());
		Neo4jClient.RecordFetchSpec<Optional<T>, Collection<T>, T> fetchSpec = preparedQuery
			.getOptionalMappingFunction()
			.map(f -> mappingSpec.mappedBy(f))
			.orElse(mappingSpec);

		return new ExecutableQuery<>(preparedQuery, fetchSpec);
	}

	private final PreparedQuery<T> preparedQuery;
	private final Neo4jClient.RecordFetchSpec<Optional<T>, Collection<T>, T> fetchSpec;

	public List<T> getResults() {
		return fetchSpec.all().stream().collect(toList());
	}

	public Optional<T> getSingleResult() {
		try {
			return fetchSpec.one();
		} catch (NoSuchRecordException e) {
			// This exception is thrown by the driver in both cases when there are 0 or 1+n records
			// So there has been an incorrect result size, but not to few results but to many.
			throw new IncorrectResultSizeDataAccessException(1);
		}
	}

	public T getRequiredSingleResult() {
		return fetchSpec.one()
			.orElseThrow(() -> new NoResultException(1, preparedQuery.getCypherQuery()));
	}
}