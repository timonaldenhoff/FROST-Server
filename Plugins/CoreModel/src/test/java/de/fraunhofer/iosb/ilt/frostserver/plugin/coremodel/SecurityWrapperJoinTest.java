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
package de.fraunhofer.iosb.ilt.frostserver.plugin.coremodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.validator.SecurityWrapperJoin;
import de.fraunhofer.iosb.ilt.frostserver.persistence.pgjooq.utils.validator.SecurityWrapperJoin.TableJoin;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.jooq.SQLDialect;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.junit.jupiter.api.Test;

/**
 * Tests the extraSelfJoinFields option of the SecurityWrapperJoin, used to let
 * partitioned tables (TimescaleDB hypertables) exclude partitions inside the
 * generated EXISTS-subquery.
 */
class SecurityWrapperJoinTest {

    private static final String CORRELATION_PK = "\"OBSERVATIONS\".\"ID\" = \"t\".\"ID\"";
    private static final String CORRELATION_PHTIME = "\"OBSERVATIONS\".\"PHENOMENON_TIME_START\" = \"t\".\"PHENOMENON_TIME_START\"";

    private static String wrapAndRender(List<String> extraSelfJoinFields) {
        return render(new SecurityWrapperJoin()
                .setJoins(List.of(datastreamJoin(), thingJoin()))
                .setWhere("not ds.\"restricted\"")
                .setExtraSelfJoinFields(extraSelfJoinFields));
    }

    private static TableJoin datastreamJoin() {
        final TableJoin join = new TableJoin();
        join.targetTable = "DATASTREAMS";
        join.targetAlias = "ds";
        join.joinOnSql = "t.\"DATASTREAM_ID\" = ds.\"ID\"";
        return join;
    }

    private static TableJoin thingJoin() {
        final TableJoin join = new TableJoin();
        join.targetTable = "THINGS";
        join.targetAlias = "th";
        join.joinOnSql = "ds.\"THING_ID\" = th.\"ID\"";
        join.joinType = TableJoin.JoinType.LEFT;
        return join;
    }

    private static String render(SecurityWrapperJoin wrapper) {
        final Table<?> wrapped = wrapper.wrap(new TableImpObservations(SQLDataType.BIGINT, SQLDataType.BIGINT, SQLDataType.BIGINT, null), null);
        return DSL.using(SQLDialect.POSTGRES).renderInlined(DSL.selectOne().from(wrapped));
    }

    @Test
    void testWithoutSelfJoinReadsMainTableOnce() {
        final String sql = render(new SecurityWrapperJoin()
                .setJoins(List.of(datastreamJoin(), thingJoin()))
                .setWhere("not ds.\"restricted\"")
                .setSelfJoin(false));
        assertEquals(1, StringUtils.countMatches(sql, "\"OBSERVATIONS\""), sql);
        assertTrue(sql.contains("from \"OBSERVATIONS\" as \"t\" where exists (select 1 from \"DATASTREAMS\" as \"ds\" left outer join \"THINGS\" as \"th\""), sql);
        assertTrue(sql.contains("where ((t.\"DATASTREAM_ID\" = ds.\"ID\") and (not ds.\"restricted\"))"), sql);
        assertFalse(sql.contains(CORRELATION_PK), sql);
    }

    @Test
    void testWithoutSelfJoinNeedsInnerFirstJoin() {
        final SecurityWrapperJoin wrapper = new SecurityWrapperJoin()
                .setJoins(List.of(thingJoin()))
                .setSelfJoin(false);
        assertThrows(IllegalArgumentException.class, () -> render(wrapper));
    }

    @Test
    void testDefaultCorrelatesOnPkOnly() {
        final String sql = wrapAndRender(null);
        assertTrue(sql.contains(CORRELATION_PK), sql);
        assertFalse(sql.contains(CORRELATION_PHTIME), sql);
        // An empty list must behave exactly like no list at all.
        assertEquals(sql, wrapAndRender(List.of()));
    }

    @Test
    void testExtraFieldIsAddedToCorrelation() {
        final String sql = wrapAndRender(List.of(TableImpObservations.NAME_COL_PHENOMENONTIMESTART));
        assertTrue(sql.contains(CORRELATION_PK), sql);
        assertTrue(sql.contains(CORRELATION_PHTIME), sql);
        // A cast would stop the database from excluding partitions again.
        assertFalse(sql.contains("cast("), sql);
        // The added correlation must be the only difference to the default.
        assertEquals(wrapAndRender(null), sql.replace(" and " + CORRELATION_PHTIME, ""));
    }

    @Test
    void testFieldLookupIsCaseInsensitive() {
        assertEquals(
                wrapAndRender(List.of(TableImpObservations.NAME_COL_PHENOMENONTIMESTART)),
                wrapAndRender(List.of("phenomenon_time_start")));
    }

    @Test
    void testUnknownFieldThrows() {
        final IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> wrapAndRender(List.of("NO_SUCH_COLUMN")));
        assertTrue(exception.getMessage().contains("NO_SUCH_COLUMN"), exception.getMessage());
    }

}
