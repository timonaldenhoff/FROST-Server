/*
 * Copyright (C) 2024 Fraunhofer Institut IOSB, Fraunhoferstr. 1, D 76131
 * Karlsruhe, Germany.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.fieldwrapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.bindings.MomentBinding;
import net.time4j.Moment;
import org.jooq.Field;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Interval comparisons with an instant must constrain the interval start, so
 * that tables partitioned on the start time can prune partitions.
 */
class StaTimeIntervalWrapperTest {

    private static final Field<Moment> START = DSL.field(DSL.name("PTS"), MomentBinding.dataType());
    private static final Field<Moment> END = DSL.field(DSL.name("PTE"), MomentBinding.dataType());
    private static final Field<Moment> T = DSL.field(DSL.name("T"), MomentBinding.dataType());

    @ParameterizedTest
    @ValueSource(strings = {"<", "<=", ">", ">="})
    void comparisonConstrainsStart(String op) {
        String sql = DSL.using(SQLDialect.POSTGRES).renderInlined(
                new StaTimeIntervalWrapper(START, END)
                        .simpleOpBool(op, new StaDateTimeWrapper(T))
                        .getCondition());
        assertTrue(sql.contains("\"PTS\" " + op + " \"T\""), () -> op + " does not constrain the start: " + sql);
    }
}
