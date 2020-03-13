/*
 * Copyright (c) 2019-2020 "Neo4j,"
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
package org.neo4j.springframework.data.core.cypher;

import org.apiguardian.api.API;
import org.neo4j.springframework.data.core.cypher.support.Visitable;
import org.neo4j.springframework.data.core.cypher.support.Visitor;
import org.springframework.lang.Nullable;

/**
 * See <a href="https://s3.amazonaws.com/artifacts.opencypher.org/railroad/Match.html">Match</a>.
 *
 * @author Michael J. Simons
 * @since 1.0
 */
@API(status = API.Status.INTERNAL, since = "1.0")
public final class Match implements ReadingClause {

	private final boolean optional;

	private final Pattern pattern;

	private @Nullable final Where optionalWhere;

	Match(boolean optional, Pattern pattern, @Nullable Where optionalWhere) {
		this.optional = optional;
		this.pattern = pattern;
		this.optionalWhere = optionalWhere;
	}

	public boolean isOptional() {
		return optional;
	}

	@Override
	public void accept(Visitor visitor) {

		visitor.enter(this);
		this.pattern.accept(visitor);
		Visitable.visitIfNotNull(optionalWhere, visitor);
		visitor.leave(this);
	}
}